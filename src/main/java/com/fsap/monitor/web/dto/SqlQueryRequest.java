package com.fsap.monitor.web.dto;

import jakarta.validation.constraints.NotBlank;

public record SqlQueryRequest(@NotBlank String sql) { }
