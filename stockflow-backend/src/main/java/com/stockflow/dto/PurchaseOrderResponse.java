package com.stockflow.dto;
import com.stockflow.entity.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
public record PurchaseOrderResponse(Long id,String orderNumber,SupplierResponse supplier,LocalDate orderDate,
 PurchaseOrderStatus status,String notes,BigDecimal totalAmount,Instant createdAt,List<Item> items){
 public record Item(Long productId,String productName,String sku,int quantity,BigDecimal unitPrice,BigDecimal lineTotal){}
 public static PurchaseOrderResponse from(PurchaseOrder o){
  Supplier s=o.getSupplier();
  return new PurchaseOrderResponse(o.getId(),o.getOrderNumber(),new SupplierResponse(s.getId(),s.getName(),s.getContactPerson(),s.getEmail(),s.getPhone(),s.getAddress(),s.getCreatedAt(),s.getUpdatedAt()),o.getOrderDate(),o.getStatus(),o.getNotes(),o.getTotalAmount(),o.getCreatedAt(),
   o.getItems().stream().map(i->new Item(i.getProduct().getId(),i.getProduct().getName(),i.getProduct().getSku(),i.getQuantity(),i.getUnitPrice(),i.getLineTotal())).toList());
 }
}


