package com.stockflow.dto;
import com.stockflow.entity.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
public record SalesOrderResponse(Long id,String orderNumber,CustomerResponse customer,LocalDate orderDate,
 OrderStatus status,String notes,BigDecimal totalAmount,Instant createdAt,List<Item> items){
 public record Item(Long productId,String productName,String sku,int quantity,BigDecimal unitPrice,BigDecimal lineTotal){}
 public static SalesOrderResponse from(SalesOrder o){
  return new SalesOrderResponse(o.getId(),o.getOrderNumber(),CustomerResponse.from(o.getCustomer()),o.getOrderDate(),o.getStatus(),o.getNotes(),o.getTotalAmount(),o.getCreatedAt(),
   o.getItems().stream().map(i->new Item(i.getProduct().getId(),i.getProduct().getName(),i.getProduct().getSku(),i.getQuantity(),i.getUnitPrice(),i.getLineTotal())).toList());
 }
}
