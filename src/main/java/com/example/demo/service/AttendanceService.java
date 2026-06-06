package com.example.demo.service;

import com.example.demo.dto.ActiveWorkerDto;
import com.example.demo.entity.AttendanceLog;
import com.example.demo.entity.OvertimeEntry;
import com.example.demo.entity.SettlementStatus;
import com.example.demo.entity.Site;
import com.example.demo.entity.Worker;
import com.example.demo.exception.BadRequestException;
import com.example.demo.exception.ConflictException;
import com.example.demo.exception.NotFoundException;
import com.example.demo.repository.AttendanceLogRepository;
import com.example.demo.repository.OvertimeEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class AttendanceService {

    private final AttendanceLogRepository attendanceLogRepository;
    private final OvertimeEntryRepository overtimeEntryRepository;
    private final WorkerService workerService;
    private final SiteService siteService;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String ACTIVE_CACHE_PREFIX = "active_worker:";
    private static final double STANDARD_SHIFT_HOURS = 8.0;
    private static final double MAX_SHIFT_HOURS = 16.0;
    private static final double MONTHLY_OVERTIME_CAP = 60.0;

    @Transactional
    public AttendanceLog clockIn(Long workerId, Long siteId) {
        log.info("Clock-in request for worker ID: {}, site ID: {}", workerId, siteId);

        // 1. Validation
        Worker worker = workerService.getWorkerById(workerId);
        if (Boolean.FALSE.equals(worker.getActive())) {
            throw new BadRequestException("WORKER_INACTIVE", "Cannot clock in an inactive worker.");
        }

        Site site = siteService.getSiteById(siteId);
        if (Boolean.FALSE.equals(site.getActive())) {
            throw new BadRequestException("SITE_INACTIVE", "Cannot clock in at an inactive site.");
        }

        // Check double clock-in
        attendanceLogRepository.findByWorkerIdAndClockOutIsNull(workerId).ifPresent(log -> {
            throw new ConflictException("DUPLICATE_CLOCK_IN", 
                "Worker is already clocked in at Site: " + log.getSite().getSiteName());
        });

        // 2. Save Attendance
        AttendanceLog attendanceLog = AttendanceLog.builder()
                .worker(worker)
                .site(site)
                .clockIn(LocalDateTime.now())
                .flagged(false)
                .build();
        AttendanceLog savedLog = attendanceLogRepository.save(attendanceLog);

        // 3. Cache in Redis
        cacheActiveWorker(savedLog);

        return savedLog;
    }

    @Transactional
    public AttendanceLog clockOut(Long workerId) {
        log.info("Clock-out request for worker ID: {}", workerId);

        // 1. Check if clocked in
        AttendanceLog attendanceLog = attendanceLogRepository.findByWorkerIdAndClockOutIsNull(workerId)
                .orElseThrow(() -> new BadRequestException("NOT_CLOCKED_IN", "Worker is not currently clocked in."));

        LocalDateTime clockIn = attendanceLog.getClockIn();
        LocalDateTime clockOut = LocalDateTime.now();
        
        // Prevent clock-out before clock-in (safety fallback)
        if (clockOut.isBefore(clockIn)) {
            clockOut = clockIn;
        }

        // 2. Calculate hours
        double totalHours = Duration.between(clockIn, clockOut).toMillis() / (1000.0 * 60 * 60);
        attendanceLog.setClockOut(clockOut);
        attendanceLog.setTotalHoursWorked(totalHours);

        // Flag if shift exceeds 16 hours
        if (totalHours > MAX_SHIFT_HOURS) {
            attendanceLog.setFlagged(true);
        }

        // 3. Calculate overtime
        double overtimeHours = Math.max(0.0, totalHours - STANDARD_SHIFT_HOURS);
        if (overtimeHours > 0.0) {
            // Apply monthly overtime cap
            LocalDateTime startOfMonth = clockIn.withDayOfMonth(1).toLocalDate().atStartOfDay();
            LocalDateTime endOfMonth = startOfMonth.plusMonths(1);
            
            Double existingOvertime = attendanceLogRepository.sumOvertimeHoursForWorkerInMonth(workerId, startOfMonth, endOfMonth);
            if (existingOvertime == null) {
                existingOvertime = 0.0;
            }

            double cappedOvertime = overtimeHours;
            if (existingOvertime + overtimeHours > MONTHLY_OVERTIME_CAP) {
                cappedOvertime = Math.max(0.0, MONTHLY_OVERTIME_CAP - existingOvertime);
            }

            attendanceLog.setOvertimeHours(cappedOvertime);

            if (cappedOvertime > 0.0) {
                // Calculate payout
                Worker worker = attendanceLog.getWorker();
                BigDecimal dailyWage = worker.getDailyWageRate();
                BigDecimal hourlyRate = dailyWage.divide(BigDecimal.valueOf(8.0), 2, RoundingMode.HALF_UP);

                double tier1Hours = Math.min(2.0, cappedOvertime);
                double tier2Hours = Math.max(0.0, cappedOvertime - 2.0);

                BigDecimal tier1Rate = hourlyRate.multiply(BigDecimal.valueOf(1.5));
                BigDecimal tier2Rate = hourlyRate.multiply(BigDecimal.valueOf(2.0));

                BigDecimal tier1Amount = tier1Rate.multiply(BigDecimal.valueOf(tier1Hours));
                BigDecimal tier2Amount = tier2Rate.multiply(BigDecimal.valueOf(tier2Hours));
                BigDecimal totalAmount = tier1Amount.add(tier2Amount);

                OvertimeEntry overtimeEntry = OvertimeEntry.builder()
                        .worker(worker)
                        .attendanceLog(attendanceLog)
                        .date(clockIn.toLocalDate())
                        .overtimeHours(cappedOvertime)
                        .overtimeRateApplied(hourlyRate) // base rate stored
                        .amount(totalAmount)
                        .settlementStatus(SettlementStatus.PENDING)
                        .build();

                overtimeEntryRepository.save(overtimeEntry);
            } else {
                attendanceLog.setOvertimeHours(0.0);
            }
        } else {
            attendanceLog.setOvertimeHours(0.0);
        }

        AttendanceLog savedLog = attendanceLogRepository.save(attendanceLog);

        // 4. Evict from Redis Cache
        evictActiveWorker(workerId);

        return savedLog;
    }

    public List<ActiveWorkerDto> getActiveWorkers() {
        log.info("Fetching active workers...");
        List<ActiveWorkerDto> activeWorkers = new ArrayList<>();
        
        try {
            Set<String> keys = redisTemplate.keys(ACTIVE_CACHE_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                for (String key : keys) {
                    ActiveWorkerDto dto = (ActiveWorkerDto) redisTemplate.opsForValue().get(key);
                    if (dto != null) {
                        activeWorkers.add(dto);
                    }
                }
                log.info("Active workers fetched from Redis. Count: {}", activeWorkers.size());
                return activeWorkers;
            }
        } catch (Exception e) {
            log.warn("Redis connection failed. Falling back to DB: {}", e.getMessage());
        }

        // Graceful degradation: DB fallback
        List<AttendanceLog> activeLogs = attendanceLogRepository.findByClockOutIsNull();
        for (AttendanceLog logEntry : activeLogs) {
            activeWorkers.add(ActiveWorkerDto.builder()
                    .workerId(logEntry.getWorker().getId())
                    .workerName(logEntry.getWorker().getName())
                    .siteId(logEntry.getSite().getId())
                    .siteName(logEntry.getSite().getSiteName())
                    .clockIn(logEntry.getClockIn())
                    .build());
        }
        log.info("Active workers fetched from DB (fallback). Count: {}", activeWorkers.size());
        return activeWorkers;
    }

    public Page<AttendanceLog> getAttendanceHistory(Long workerId, LocalDateTime from, LocalDateTime to, Pageable pageable) {
        log.info("Fetching attendance history for worker ID: {} from {} to {}", workerId, from, to);
        return attendanceLogRepository.findByWorkerIdAndClockInBetween(workerId, from, to, pageable);
    }

    private void cacheActiveWorker(AttendanceLog logEntry) {
        try {
            ActiveWorkerDto dto = ActiveWorkerDto.builder()
                    .workerId(logEntry.getWorker().getId())
                    .workerName(logEntry.getWorker().getName())
                    .siteId(logEntry.getSite().getId())
                    .siteName(logEntry.getSite().getSiteName())
                    .clockIn(logEntry.getClockIn())
                    .build();
            
            String key = ACTIVE_CACHE_PREFIX + logEntry.getWorker().getId();
            redisTemplate.opsForValue().set(key, dto, 16, TimeUnit.HOURS);
            log.info("Cached active worker in Redis under key: {}", key);
        } catch (Exception e) {
            log.warn("Redis is down. Failed to cache active worker ID {}: {}", 
                    logEntry.getWorker().getId(), e.getMessage());
        }
    }

    private void evictActiveWorker(Long workerId) {
        try {
            String key = ACTIVE_CACHE_PREFIX + workerId;
            redisTemplate.delete(key);
            log.info("Evicted active worker from Redis: {}", key);
        } catch (Exception e) {
            log.warn("Redis is down. Failed to evict active worker ID {}: {}", workerId, e.getMessage());
        }
    }

    // Background Scheduler to auto-close and flag shifts exceeding 16 hours
    @Scheduled(cron = "0 0 * * * *") // Every hour
    @Transactional
    public void autoFlagExpiredShifts() {
        log.info("Running scheduled task to auto-close shifts exceeding 16 hours...");
        List<AttendanceLog> openLogs = attendanceLogRepository.findByClockOutIsNull();
        LocalDateTime threshold = LocalDateTime.now().minusHours(16);
        
        for (AttendanceLog logEntry : openLogs) {
            if (logEntry.getClockIn().isBefore(threshold)) {
                log.warn("Auto-closing and flagging attendance log ID {} for worker ID {} (clocked in for >16 hours)",
                        logEntry.getId(), logEntry.getWorker().getId());
                
                logEntry.setClockOut(logEntry.getClockIn().plusHours(16));
                logEntry.setTotalHoursWorked(16.0);
                logEntry.setOvertimeHours(8.0); // 16 - 8 = 8 overtime hours
                logEntry.setFlagged(true);
                
                // Let's sum and cap overtime
                LocalDateTime startOfMonth = logEntry.getClockIn().withDayOfMonth(1).toLocalDate().atStartOfDay();
                LocalDateTime endOfMonth = startOfMonth.plusMonths(1);
                
                Double existingOvertime = attendanceLogRepository.sumOvertimeHoursForWorkerInMonth(
                        logEntry.getWorker().getId(), startOfMonth, endOfMonth);
                if (existingOvertime == null) {
                    existingOvertime = 0.0;
                }
                
                double calculatedOvertime = 8.0;
                double cappedOvertime = calculatedOvertime;
                if (existingOvertime + calculatedOvertime > MONTHLY_OVERTIME_CAP) {
                    cappedOvertime = Math.max(0.0, MONTHLY_OVERTIME_CAP - existingOvertime);
                }
                
                logEntry.setOvertimeHours(cappedOvertime);
                attendanceLogRepository.save(logEntry);
                
                if (cappedOvertime > 0.0) {
                    BigDecimal dailyWage = logEntry.getWorker().getDailyWageRate();
                    BigDecimal hourlyRate = dailyWage.divide(BigDecimal.valueOf(8.0), 2, RoundingMode.HALF_UP);
                    
                    double tier1Hours = Math.min(2.0, cappedOvertime);
                    double tier2Hours = Math.max(0.0, cappedOvertime - 2.0);
                    
                    BigDecimal tier1Rate = hourlyRate.multiply(BigDecimal.valueOf(1.5));
                    BigDecimal tier2Rate = hourlyRate.multiply(BigDecimal.valueOf(2.0));
                    
                    BigDecimal tier1Amount = tier1Rate.multiply(BigDecimal.valueOf(tier1Hours));
                    BigDecimal tier2Amount = tier2Rate.multiply(BigDecimal.valueOf(tier2Hours));
                    BigDecimal totalAmount = tier1Amount.add(tier2Amount);
                    
                    OvertimeEntry overtimeEntry = OvertimeEntry.builder()
                            .worker(logEntry.getWorker())
                            .attendanceLog(logEntry)
                            .date(logEntry.getClockIn().toLocalDate())
                            .overtimeHours(cappedOvertime)
                            .overtimeRateApplied(hourlyRate)
                            .amount(totalAmount)
                            .settlementStatus(SettlementStatus.PENDING)
                            .build();
                    overtimeEntryRepository.save(overtimeEntry);
                }
                
                // Evict from Redis
                evictActiveWorker(logEntry.getWorker().getId());
            }
        }
    }
}
