package com.example.demo.controller;

import com.example.demo.dto.OvertimeSummaryResponse;
import com.example.demo.dto.SettlementResponse;
import com.example.demo.service.OvertimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/overtime")
@RequiredArgsConstructor
@Validated
public class OvertimeController {

    private final OvertimeService overtimeService;

    @GetMapping("/summary/{workerId}")
    public ResponseEntity<OvertimeSummaryResponse> getOvertimeSummary(
            @PathVariable Long workerId,
            @RequestParam String month
    ) {
        OvertimeSummaryResponse summary = overtimeService.getOvertimeSummary(workerId, month);
        return ResponseEntity.ok(summary);
    }

    @PostMapping("/settle/{workerId}")
    public ResponseEntity<SettlementResponse> settleOvertime(
            @PathVariable Long workerId,
            @RequestParam String month
    ) {
        SettlementResponse response = overtimeService.settleOvertime(workerId, month);
        return ResponseEntity.ok(response);
    }
}
