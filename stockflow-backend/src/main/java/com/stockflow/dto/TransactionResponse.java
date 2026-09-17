package com.stockflow.dto;
import com.stockflow.entity.*; import java.time.Instant;
public record TransactionResponse(Long id,Long productId,String productSku,String productName,TransactionType transactionType,int quantity,int previousStock,int newStock,ReferenceType referenceType,String referenceId,String reason,String notes,Instant createdAt){
 public static TransactionResponse from(InventoryTransaction t){return new TransactionResponse(t.getId(),t.getProduct().getId(),t.getProductSku(),t.getProductName(),t.getTransactionType(),t.getQuantity(),t.getPreviousStock(),t.getNewStock(),t.getReferenceType(),t.getReferenceId(),t.getReason(),t.getNotes(),t.getCreatedAt());}
}