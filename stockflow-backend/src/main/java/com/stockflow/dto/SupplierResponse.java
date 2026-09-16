package com.stockflow.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;
public record SupplierResponse(
        Long id,
        String name,
        String contactPerson,
        String email,
        String phone,
        String address,
        Instant createdAt,
        Instant updatedAt
) {}

