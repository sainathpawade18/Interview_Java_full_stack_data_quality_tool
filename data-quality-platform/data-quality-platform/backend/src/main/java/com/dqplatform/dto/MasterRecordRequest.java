package com.dqplatform.dto;

import jakarta.validation.constraints.*;

public record MasterRecordRequest(
        @NotBlank @Size(max=50) String recordCode,
        @NotBlank @Size(max=120) String name,
        @Email @Size(max=150) String email,
        @Size(max=20) String phone,
        @NotBlank @Size(max=50) String category,
        @NotBlank @Size(max=20) String statusValue
) {}
