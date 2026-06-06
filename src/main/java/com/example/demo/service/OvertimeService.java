package com.example.demo.service;

import com.example.demo.dto.OvertimeSummaryResponse;
import com.example.demo.dto.SettlementResponse;
import com.example.demo.entity.OvertimeEntry;
import com.example.demo.entity.SettlementStatus;
import com.example.demo.entity.Worker;
import com.example.demo.exception.BadRequestException;
import com.example.demo.exception.ConflictException;
import com.example.demo.repository.OvertimeEntryRepository;
import com.example.demo.event.OvertimeSettledEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OvertimeService {

    private final OvertimeEntryRepository overtimeEntryRepository;
    private final WorkerService workerService;
    private final ExternalWageService externalWageService;
    private final ApplicationEventPublisher eventPublisher;

    // Note: NOT annotated with @Transactional to prevent holding the DB connection 
    // hostage during the external REST call (LF-205).
    public OvertimeSummaryResponse getOvertimeSummary(Long workerId, String monthStr) {
        log.info("Fetching overtime summary for worker ID: {} for month: {}", workerId, monthStr);
        
        // 1. Validate worker exists
        workerService.getWorkerById(workerId);
        
        // 2. Call external wage API OUTSIDE of active database transactions
        BigDecimal minimumWage = externalWageService.fetchMinimumWageRate();

        // 3. Database query (fetch entries)
        YearMonth yearMonth = parseMonth(monthStr);
        LocalDate start = yearMonth.atDay(1);
        LocalDate end = yearMonth.atEndOfMonth();

        List<OvertimeEntry> entries = overtimeEntryRepository.findByWorkerIdAndDateBetween(workerId, start, end);

        // 4. Calculate summary fields
        double totalHours = entries.stream()
                .mapToDouble(OvertimeEntry::getOvertimeHours)
                .sum();

        BigDecimal totalAmount = entries.stream()
                .map(OvertimeEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        SettlementStatus overallStatus = SettlementStatus.SETTLED;
        for (OvertimeEntry entry : entries) {
            if (entry.getSettlementStatus() == SettlementStatus.PENDING) {
                overallStatus = SettlementStatus.PENDING;
                break;
            }
        }

        List<OvertimeSummaryResponse.OvertimeDetailDto> detailDtos = entries.stream()
                .map(e -> OvertimeSummaryResponse.OvertimeDetailDto.builder()
                        .id(e.getId())
                        .date(e.getDate())
                        .overtimeHours(e.getOvertimeHours())
                        .amount(e.getAmount())
                        .status(e.getSettlementStatus())
                        .build())
                .collect(Collectors.toList());

        return OvertimeSummaryResponse.builder()
                .workerId(workerId)
                .month(monthStr)
                .totalOvertimeHours(totalHours)
                .totalAmount(totalAmount)
                .settlementStatus(overallStatus)
                .minimumWageApplied(minimumWage)
                .entries(detailDtos)
                .build();
    }

    // Wrapped in a single atomic transaction. (LF-204)
    @Transactional
    public SettlementResponse settleOvertime(Long workerId, String monthStr) {
        log.info("Settling overtime for worker ID: {} for month: {}", workerId, monthStr);

        // 1. Validate worker exists
        workerService.getWorkerById(workerId);

        // 2. Parse and validate month - Cannot settle current or future month
        YearMonth yearMonth = parseMonth(monthStr);
        YearMonth currentMonth = YearMonth.now();
        if (yearMonth.compareTo(currentMonth) >= 0) {
            throw new BadRequestException("CANNOT_SETTLE_CURRENT_MONTH", 
                "Cannot settle overtime for the current or future month. Completed months only.");
        }

        LocalDate start = yearMonth.atDay(1);
        LocalDate end = yearMonth.atEndOfMonth();

        // 3. Fetch PENDING entries
        List<OvertimeEntry> pendingEntries = overtimeEntryRepository
                .findByWorkerIdAndDateBetweenAndSettlementStatus(workerId, start, end, SettlementStatus.PENDING);

        if (pendingEntries.isEmpty()) {
            // Check if there are any settled entries
            List<OvertimeEntry> allEntries = overtimeEntryRepository.findByWorkerIdAndDateBetween(workerId, start, end);
            if (!allEntries.isEmpty()) {
                throw new ConflictException("ALREADY_SETTLED", "Overtime entries for this month are already settled.");
            } else {
                throw new BadRequestException("NO_OVERTIME_TO_SETTLE", "No overtime records exist for the specified month.");
            }
        }

        // 4. Update status and calculate total amount settled
        BigDecimal totalSettled = BigDecimal.ZERO;
        for (OvertimeEntry entry : pendingEntries) {
            entry.setSettlementStatus(SettlementStatus.SETTLED);
            totalSettled = totalSettled.add(entry.getAmount());
        }
        
        overtimeEntryRepository.saveAll(pendingEntries);
        log.info("Marked {} entries as SETTLED. Total settled amount: {}", pendingEntries.size(), totalSettled);

        // 5. Publish post-commit SMS event
        eventPublisher.publishEvent(new OvertimeSettledEvent(this, workerId, monthStr, totalSettled));

        return SettlementResponse.builder()
                .workerId(workerId)
                .month(monthStr)
                .totalAmountSettled(totalSettled)
                .status("SETTLED")
                .build();
    }

    private YearMonth parseMonth(String monthStr) {
        try {
            return YearMonth.parse(monthStr);
        } catch (Exception e) {
            throw new BadRequestException("INVALID_MONTH_FORMAT", "Month must be in YYYY-MM format (e.g. 2026-06).");
        }
    }
}
