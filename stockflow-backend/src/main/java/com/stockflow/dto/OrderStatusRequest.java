package com.stockflow.dto;
import com.stockflow.entity.OrderStatus;
import jakarta.validation.constraints.NotNull;
public record OrderStatusRequest(@NotNull OrderStatus status){}
