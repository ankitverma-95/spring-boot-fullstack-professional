package com.example.demo.controller;

import com.example.demo.dto.ActiveWorkerDto;
import com.example.demo.dto.ClockInRequest;
import com.example.demo.dto.ClockOutRequest;
import com.example.demo.dto.PaginatedResponse;
import com.example.demo.entity.AttendanceLog;
import com.example.demo.service.AttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
@Validated
public class AttendanceController {

    private final AttendanceService attendanceService;

    @PostMapping("/clock-in")
    public ResponseEntity<AttendanceLog> clockIn(@Valid @RequestBody ClockInRequest request) {
        AttendanceLog logEntry = attendanceService.clockIn(request.getWorkerId(), request.getSiteId());
        return ResponseEntity.ok(logEntry);
    }

    @PostMapping("/clock-out")
    public ResponseEntity<AttendanceLog> clockOut(@Valid @RequestBody ClockOutRequest request) {
        AttendanceLog logEntry = attendanceService.clockOut(request.getWorkerId());
        return ResponseEntity.ok(logEntry);
    }

    @GetMapping("/active")
    public ResponseEntity<List<ActiveWorkerDto>> getActiveWorkers() {
        List<ActiveWorkerDto> active = attendanceService.getActiveWorkers();
        return ResponseEntity.ok(active);
    }

    @GetMapping("/log")
    public ResponseEntity<PaginatedResponse<AttendanceLog>> getAttendanceHistory(
            @RequestParam Long workerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        // Default range: last 30 days
        LocalDate fromDate = (from != null) ? from : LocalDate.now().minusDays(30);
        LocalDate toDate = (to != null) ? to : LocalDate.now();

        LocalDateTime startDateTime = fromDate.atStartOfDay();
        LocalDateTime endDateTime = toDate.atTime(LocalTime.MAX);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "clockIn"));
        Page<AttendanceLog> logPage = attendanceService.getAttendanceHistory(workerId, startDateTime, endDateTime, pageable);

        PaginatedResponse<AttendanceLog> response = PaginatedResponse.<AttendanceLog>builder()
                .content(logPage.getContent())
                .totalElements(logPage.getTotalElements())
                .totalPages(logPage.getTotalPages())
                .currentPage(logPage.getNumber())
                .build();

        return ResponseEntity.ok(response);
    }
}
