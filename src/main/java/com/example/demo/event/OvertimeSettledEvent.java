package com.example.demo.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;

@Getter
public class OvertimeSettledEvent extends ApplicationEvent {
    private final Long workerId;
    private final String month;
    private final BigDecimal totalAmount;

    public OvertimeSettledEvent(Object source, Long workerId, String month, BigDecimal totalAmount) {
        super(source);
        this.workerId = workerId;
        this.month = month;
        this.totalAmount = totalAmount;
    }
}
