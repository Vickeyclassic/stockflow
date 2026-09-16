package com.stockflow.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record ProductUpdateRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]*", message = "must use letters, numbers, dots, underscores or hyphens") String sku,
        @NotBlank @Size(max = 150) String name,
        @Size(max = 2000) String description,
        @NotNull @Positive Long categoryId,
        @Positive Long supplierId,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal costPrice,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal sellingPrice,
        @NotNull @Min(0) Integer reorderLevel,
        @NotBlank @Size(max = 30) String unit,
        @NotNull Boolean active
) {}

