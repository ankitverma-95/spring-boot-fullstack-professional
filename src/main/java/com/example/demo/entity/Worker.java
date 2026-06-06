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
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Entity
@Table(
    name = "workers",
    indexes = {
        @Index(name = "idx_workers_phone", columnList = "phone", unique = true)
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Worker {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Name is required")
    @Column(nullable = false)
    private String name;

    @NotBlank(message = "Phone number is required")
    @Column(nullable = false, unique = true)
    private String phone;

    @NotNull(message = "Designation is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Designation designation;

    @NotNull(message = "Daily wage rate is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Daily wage rate must be greater than zero")
    @Column(name = "daily_wage_rate", nullable = false, precision = 12, scale = 2)
    private BigDecimal dailyWageRate;

    @NotNull(message = "Active status is required")
    @Column(nullable = false)
    private Boolean active;
}
