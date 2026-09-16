package com.stockflow.dto;

import jakarta.validation.constraints.*;

public record CategoryRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 1000) String description
) {}

