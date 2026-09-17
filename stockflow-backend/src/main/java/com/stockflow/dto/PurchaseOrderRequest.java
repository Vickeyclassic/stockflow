package com.stockflow.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
public record PurchaseOrderRequest(@NotBlank @Size(max=100) String orderNumber,@NotNull @Positive Long supplierId,
 @NotNull LocalDate orderDate,@Size(max=2000) String notes,@NotEmpty @Size(max=100) List<@NotNull @Valid Item> items){
 public record Item(@NotNull @Positive Long productId,@NotNull @Positive Integer quantity,
 @NotNull @DecimalMin("0.00") @Digits(integer=10,fraction=2) BigDecimal unitPrice){}
}

