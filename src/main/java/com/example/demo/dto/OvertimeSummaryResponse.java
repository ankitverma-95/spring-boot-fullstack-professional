package com.example.demo.dto;

import com.example.demo.entity.SettlementStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OvertimeSummaryResponse {
    private Long workerId;
    private String month;
    private Double totalOvertimeHours;
    private BigDecimal totalAmount;
    private SettlementStatus settlementStatus;
    private BigDecimal minimumWageApplied;
    private List<OvertimeDetailDto> entries;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OvertimeDetailDto {
        private Long id;
        private LocalDate date;
        private Double overtimeHours;
        private BigDecimal amount;
        private SettlementStatus status;
    }
}
