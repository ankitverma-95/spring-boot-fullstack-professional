package com.example.demo.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestTemplate;

@Configuration
@Slf4j
public class AppConfig {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000); // 3 seconds connect timeout
        factory.setReadTimeout(3000);    // 3 seconds read timeout
        return new RestTemplate(factory);
    }

    @Bean
    public CommandLineRunner createDatabaseIndexes(JdbcTemplate jdbcTemplate) {
        return args -> {
            try {
                // Creates a partial unique index in PostgreSQL to prevent double clock-ins
                jdbcTemplate.execute(
                    "CREATE UNIQUE INDEX IF NOT EXISTS idx_active_clock_in " +
                    "ON attendance_logs (worker_id) " +
                    "WHERE clock_out IS NULL;"
                );
                log.info("Successfully checked/created database partial index 'idx_active_clock_in' for active clock-ins.");
            } catch (Exception e) {
                log.error("Failed to check/create database index: {}", e.getMessage());
            }
        };
    }
}
