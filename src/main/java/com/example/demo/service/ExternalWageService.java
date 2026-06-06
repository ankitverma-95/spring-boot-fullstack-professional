package com.example.demo.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExternalWageService {

    private final RestTemplate restTemplate;

    @Value("${app.wage-api.url:https://api.mockgovernment.gov/wage-rates}")
    private String wageApiUrl;

    public BigDecimal fetchMinimumWageRate() {
        log.info("Fetching minimum wage rate from external government API: {}", wageApiUrl);
        try {
            // Attempt to contact external API
            BigDecimal rate = restTemplate.getForObject(wageApiUrl, BigDecimal.class);
            if (rate != null) {
                log.info("Successfully fetched external minimum wage rate: {}", rate);
                return rate;
            }
        } catch (Exception e) {
            log.warn("External government wage API failed: {}. Falling back to default minimum wage rate.", e.getMessage());
        }
        // Fallback value: standard default minimum wage rate
        return BigDecimal.valueOf(50.00); // e.g., ₹50/hour base minimum wage
    }
}
