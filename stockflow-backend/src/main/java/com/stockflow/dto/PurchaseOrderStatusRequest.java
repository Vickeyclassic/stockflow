package com.stockflow.dto;
import com.stockflow.entity.PurchaseOrderStatus;
import jakarta.validation.constraints.NotNull;
public record PurchaseOrderStatusRequest(@NotNull PurchaseOrderStatus status){}

