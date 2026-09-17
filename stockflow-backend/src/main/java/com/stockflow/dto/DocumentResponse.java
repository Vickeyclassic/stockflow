package com.stockflow.dto;
import com.stockflow.entity.*; import java.time.*; import java.util.List; import java.math.BigDecimal;
public record DocumentResponse(Long id,ReferenceType kind,String number,LocalDate date,Long supplierId,String supplierName,String notes,Instant createdAt,List<Line> lines){
 public record Line(Long id,Long productId,String productSku,String productName,int quantity,BigDecimal unitPrice){}
 public static DocumentResponse from(StockDocument d){return new DocumentResponse(d.getId(),d.getKind(),d.getNumber(),d.getDocumentDate(),d.getSupplier()==null?null:d.getSupplier().getId(),d.getSupplierName(),d.getNotes(),d.getCreatedAt(),d.getLines().stream().map(l->new Line(l.getId(),l.getProduct().getId(),l.getProductSku(),l.getProductName(),l.getQuantity(),l.getUnitPrice())).toList());}
}