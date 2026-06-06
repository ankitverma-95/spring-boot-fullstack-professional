package com.example.demo.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@Slf4j
public class SmsService {

    public void sendSms(Long workerId, String month, BigDecimal amount) {
        log.info("Sending SMS notification to worker ID: {}", workerId);
        String message = String.format("Your %s overtime of ₹%s has been settled.", month, amount.setScale(2).toString());
        log.info("[SMS GATEWAY] Target: Worker ID {}, Message: '{}'", workerId, message);
    }
}
