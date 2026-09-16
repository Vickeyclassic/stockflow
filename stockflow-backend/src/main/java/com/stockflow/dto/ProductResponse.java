package com.stockflow.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.math.BigDecimal;
public record ProductResponse(
        Long id,
        String sku,
        String name,
        String description,
        Long categoryId,
        String categoryName,
        Long supplierId,
        String supplierName,
        BigDecimal costPrice,
        BigDecimal sellingPrice,
        Integer quantityInStock,
        Integer reorderLevel,
        String unit,
        Boolean active,
        boolean lowStock,
        Instant createdAt,
        Instant updatedAt
) {}

