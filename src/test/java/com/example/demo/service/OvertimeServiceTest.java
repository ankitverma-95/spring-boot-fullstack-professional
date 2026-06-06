package com.example.demo.service;

import com.example.demo.dto.SettlementResponse;
import com.example.demo.entity.OvertimeEntry;
import com.example.demo.entity.SettlementStatus;
import com.example.demo.entity.Worker;
import com.example.demo.exception.BadRequestException;
import com.example.demo.exception.ConflictException;
import com.example.demo.repository.OvertimeEntryRepository;
import com.example.demo.event.OvertimeSettledEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OvertimeServiceTest {

    @Mock
    private OvertimeEntryRepository overtimeEntryRepository;

    @Mock
    private WorkerService workerService;

    @Mock
    private ExternalWageService externalWageService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private OvertimeService overtimeService;

    private Worker worker;
    private OvertimeEntry entry1;
    private OvertimeEntry entry2;

    @BeforeEach
    public void setUp() {
        worker = Worker.builder()
                .id(1L)
                .name("Ramesh Kumar")
                .phone("9876543210")
                .active(true)
                .build();

        entry1 = OvertimeEntry.builder()
                .id(10L)
                .worker(worker)
                .date(LocalDate.of(2026, 5, 10)) // May 2026 (completed month relative to current time June 2026)
                .overtimeHours(2.0)
                .amount(BigDecimal.valueOf(300.00))
                .settlementStatus(SettlementStatus.PENDING)
                .build();

        entry2 = OvertimeEntry.builder()
                .id(11L)
                .worker(worker)
                .date(LocalDate.of(2026, 5, 20))
                .overtimeHours(1.0)
                .amount(BigDecimal.valueOf(200.00))
                .settlementStatus(SettlementStatus.PENDING)
                .build();
    }

    @Test
    public void testSettleOvertime_Success() {
        String pastMonth = "2026-05";
        when(workerService.getWorkerById(1L)).thenReturn(worker);
        
        List<OvertimeEntry> pending = new ArrayList<>();
        pending.add(entry1);
        pending.add(entry2);
        
        when(overtimeEntryRepository.findByWorkerIdAndDateBetweenAndSettlementStatus(
                eq(1L), any(), any(), eq(SettlementStatus.PENDING)))
                .thenReturn(pending);

        SettlementResponse response = overtimeService.settleOvertime(1L, pastMonth);

        assertNotNull(response);
        assertEquals(1L, response.getWorkerId());
        assertEquals(pastMonth, response.getMonth());
        assertEquals("SETTLED", response.getStatus());
        assertEquals(0, BigDecimal.valueOf(500.00).compareTo(response.getTotalAmountSettled()));
        
        assertEquals(SettlementStatus.SETTLED, entry1.getSettlementStatus());
        assertEquals(SettlementStatus.SETTLED, entry2.getSettlementStatus());
        
        verify(overtimeEntryRepository, times(1)).saveAll(pending);
        verify(eventPublisher, times(1)).publishEvent(any(OvertimeSettledEvent.class));
    }

    @Test
    public void testSettleOvertime_CurrentMonth_ThrowsBadRequest() {
        String currentMonthStr = YearMonth.now().toString();

        assertThrows(BadRequestException.class, () -> {
            overtimeService.settleOvertime(1L, currentMonthStr);
        });
        
        verify(overtimeEntryRepository, never()).saveAll(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    public void testSettleOvertime_AlreadySettled_ThrowsConflict() {
        String pastMonth = "2026-05";
        when(workerService.getWorkerById(1L)).thenReturn(worker);
        
        when(overtimeEntryRepository.findByWorkerIdAndDateBetweenAndSettlementStatus(
                eq(1L), any(), any(), eq(SettlementStatus.PENDING)))
                .thenReturn(Collections.emptyList());
        
        entry1.setSettlementStatus(SettlementStatus.SETTLED);
        when(overtimeEntryRepository.findByWorkerIdAndDateBetween(eq(1L), any(), any()))
                .thenReturn(Collections.singletonList(entry1));

        assertThrows(ConflictException.class, () -> {
            overtimeService.settleOvertime(1L, pastMonth);
        });
    }
}
