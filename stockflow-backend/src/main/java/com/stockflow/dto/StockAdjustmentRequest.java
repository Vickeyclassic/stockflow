package com.stockflow.dto;

import jakarta.validation.constraints.*;

public record StockAdjustmentRequest(
        @NotNull Integer adjustment
) {}

