package com.stockflow.dto;
import com.stockflow.entity.*; import jakarta.validation.constraints.*;
public record MovementRequest(@NotNull @Positive Long productId,@NotNull TransactionType transactionType,@NotNull @Positive Integer quantity,@NotBlank @Size(max=250) String reason,@Size(max=2000) String notes,ReferenceType referenceType,@Size(max=100) String referenceId) {}