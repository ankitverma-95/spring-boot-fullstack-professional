package com.example.demo.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(
    name = "overtime_entries",
    indexes = {
        @Index(name = "idx_overtime_worker_date", columnList = "worker_id, date"),
        @Index(name = "idx_overtime_worker_status", columnList = "worker_id, settlement_status"),
        @Index(name = "idx_overtime_attendance_id", columnList = "attendance_log_id", unique = true)
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OvertimeEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Worker is required")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "worker_id", nullable = false)
    private Worker worker;

    @NotNull(message = "Attendance log reference is required")
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attendance_log_id", nullable = false, unique = true)
    private AttendanceLog attendanceLog;

    @NotNull(message = "Date is required")
    @Column(nullable = false)
    private LocalDate date;

    @NotNull(message = "Overtime hours is required")
    @Column(name = "overtime_hours", nullable = false)
    private Double overtimeHours;

    @NotNull(message = "Overtime rate applied is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Overtime rate applied must be greater than zero")
    @Column(name = "overtime_rate_applied", nullable = false, precision = 12, scale = 2)
    private BigDecimal overtimeRateApplied;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.0", message = "Amount cannot be negative")
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @NotNull(message = "Settlement status is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_status", nullable = false)
    private SettlementStatus settlementStatus;
}
