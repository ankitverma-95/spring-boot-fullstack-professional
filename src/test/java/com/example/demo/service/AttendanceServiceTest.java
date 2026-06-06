package com.example.demo.service;

import com.example.demo.entity.AttendanceLog;
import com.example.demo.entity.Designation;
import com.example.demo.entity.OvertimeEntry;
import com.example.demo.entity.SettlementStatus;
import com.example.demo.entity.Site;
import com.example.demo.entity.Worker;
import com.example.demo.exception.BadRequestException;
import com.example.demo.exception.ConflictException;
import com.example.demo.repository.AttendanceLogRepository;
import com.example.demo.repository.OvertimeEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AttendanceServiceTest {

    @Mock
    private AttendanceLogRepository attendanceLogRepository;

    @Mock
    private OvertimeEntryRepository overtimeEntryRepository;

    @Mock
    private WorkerService workerService;

    @Mock
    private SiteService siteService;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private AttendanceService attendanceService;

    private Worker worker;
    private Site site;

    @BeforeEach
    public void setUp() {
        worker = Worker.builder()
                .id(1L)
                .name("Ramesh Kumar")
                .phone("9876543210")
                .designation(Designation.MASON)
                .dailyWageRate(BigDecimal.valueOf(800.00)) // Hourly base = 100.00
                .active(true)
                .build();

        site = Site.builder()
                .id(1L)
                .siteName("Greenfield Phase 2")
                .location("Sector 62")
                .active(true)
                .build();
    }

    @Test
    public void testClockIn_Success() {
        when(workerService.getWorkerById(1L)).thenReturn(worker);
        when(siteService.getSiteById(1L)).thenReturn(site);
        when(attendanceLogRepository.findByWorkerIdAndClockOutIsNull(1L)).thenReturn(Optional.empty());
        
        AttendanceLog expectedLog = AttendanceLog.builder()
                .worker(worker)
                .site(site)
                .clockIn(LocalDateTime.now())
                .flagged(false)
                .build();
        when(attendanceLogRepository.save(any(AttendanceLog.class))).thenReturn(expectedLog);
        
        // Mock Redis
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        AttendanceLog result = attendanceService.clockIn(1L, 1L);

        assertNotNull(result);
        assertEquals(worker, result.getWorker());
        assertEquals(site, result.getSite());
        assertFalse(result.getFlagged());
        
        verify(attendanceLogRepository, times(1)).save(any(AttendanceLog.class));
        verify(valueOperations, times(1)).set(eq("active_worker:1"), any(), anyLong(), any());
    }

    @Test
    public void testClockIn_DoubleClockIn_ThrowsConflict() {
        when(workerService.getWorkerById(1L)).thenReturn(worker);
        when(siteService.getSiteById(1L)).thenReturn(site);
        
        AttendanceLog existingLog = AttendanceLog.builder()
                .worker(worker)
                .site(site)
                .clockIn(LocalDateTime.now().minusHours(2))
                .build();
        when(attendanceLogRepository.findByWorkerIdAndClockOutIsNull(1L)).thenReturn(Optional.of(existingLog));

        assertThrows(ConflictException.class, () -> {
            attendanceService.clockIn(1L, 1L);
        });
    }

    @Test
    public void testClockOut_CalculateOvertimeCorrectly() {
        LocalDateTime clockInTime = LocalDateTime.now().minusHours(11);
        AttendanceLog logEntry = AttendanceLog.builder()
                .id(100L)
                .worker(worker)
                .site(site)
                .clockIn(clockInTime)
                .flagged(false)
                .build();

        when(attendanceLogRepository.findByWorkerIdAndClockOutIsNull(1L)).thenReturn(Optional.of(logEntry));
        when(attendanceLogRepository.sumOvertimeHoursForWorkerInMonth(eq(1L), any(), any())).thenReturn(10.0);
        when(attendanceLogRepository.save(any(AttendanceLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(redisTemplate.delete("active_worker:1")).thenReturn(true);

        AttendanceLog result = attendanceService.clockOut(1L);

        assertNotNull(result);
        assertNotNull(result.getClockOut());
        assertTrue(result.getTotalHoursWorked() >= 10.9 && result.getTotalHoursWorked() <= 11.1);
        assertEquals(3.0, result.getOvertimeHours(), 0.05);
        assertFalse(result.getFlagged());

        // Base rate = 100/hr. 3 hours overtime: 2 hours at 1.5x (150/hr) = 300, 1 hour at 2x (200/hr) = 200. Total = 500.
        ArgumentCaptor<OvertimeEntry> overtimeCaptor = ArgumentCaptor.forClass(OvertimeEntry.class);
        verify(overtimeEntryRepository, times(1)).save(overtimeCaptor.capture());
        
        OvertimeEntry overtimeEntry = overtimeCaptor.getValue();
        assertEquals(3.0, overtimeEntry.getOvertimeHours(), 0.05);
        assertEquals(0, BigDecimal.valueOf(500.00).compareTo(overtimeEntry.getAmount()));
        assertEquals(SettlementStatus.PENDING, overtimeEntry.getSettlementStatus());
    }

    @Test
    public void testClockOut_CapOvertimeAt60() {
        LocalDateTime clockInTime = LocalDateTime.now().minusHours(11);
        AttendanceLog logEntry = AttendanceLog.builder()
                .id(100L)
                .worker(worker)
                .site(site)
                .clockIn(clockInTime)
                .flagged(false)
                .build();

        when(attendanceLogRepository.findByWorkerIdAndClockOutIsNull(1L)).thenReturn(Optional.of(logEntry));
        // Worker has 58.5 overtime hours already. Cap allows remaining 1.5 hours only.
        when(attendanceLogRepository.sumOvertimeHoursForWorkerInMonth(eq(1L), any(), any())).thenReturn(58.5); 
        when(attendanceLogRepository.save(any(AttendanceLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(redisTemplate.delete("active_worker:1")).thenReturn(true);

        AttendanceLog result = attendanceService.clockOut(1L);

        assertNotNull(result);
        assertEquals(1.5, result.getOvertimeHours(), 0.05); // Capped at 1.5 hours
        
        ArgumentCaptor<OvertimeEntry> overtimeCaptor = ArgumentCaptor.forClass(OvertimeEntry.class);
        verify(overtimeEntryRepository, times(1)).save(overtimeCaptor.capture());
        
        OvertimeEntry overtimeEntry = overtimeCaptor.getValue();
        assertEquals(1.5, overtimeEntry.getOvertimeHours(), 0.05);
        // Base rate = 100/hr. 1.5 hours at 1.5x (150/hr) = 225.00
        assertEquals(0, BigDecimal.valueOf(225.00).compareTo(overtimeEntry.getAmount()));
    }

    @Test
    public void testClockOut_FlagIfExceeds16Hours() {
        LocalDateTime clockInTime = LocalDateTime.now().minusHours(18);
        AttendanceLog logEntry = AttendanceLog.builder()
                .id(100L)
                .worker(worker)
                .site(site)
                .clockIn(clockInTime)
                .flagged(false)
                .build();

        when(attendanceLogRepository.findByWorkerIdAndClockOutIsNull(1L)).thenReturn(Optional.of(logEntry));
        when(attendanceLogRepository.sumOvertimeHoursForWorkerInMonth(eq(1L), any(), any())).thenReturn(0.0);
        when(attendanceLogRepository.save(any(AttendanceLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(redisTemplate.delete("active_worker:1")).thenReturn(true);

        AttendanceLog result = attendanceService.clockOut(1L);

        assertNotNull(result);
        assertTrue(result.getFlagged());
    }
}
