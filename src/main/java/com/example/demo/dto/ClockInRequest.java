package com.example.demo.dto;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotNull;

@Getter
@Setter
public class ClockInRequest {

    @NotNull(message = "workerId is required")
    private Long workerId;

    @NotNull(message = "siteId is required")
    private Long siteId;
}
