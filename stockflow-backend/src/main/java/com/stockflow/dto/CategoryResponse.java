package com.stockflow.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;
public record CategoryResponse(
        Long id,
        String name,
        String description,
        Instant createdAt,
        Instant updatedAt
) {}

