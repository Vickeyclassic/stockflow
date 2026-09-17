package com.stockflow.entity;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="inventory_transactions", indexes={@Index(name="idx_movement_created",columnList="createdAt,id"),@Index(name="idx_movement_product",columnList="product_id,createdAt")})
@org.hibernate.annotations.Immutable
@org.hibernate.annotations.Check(constraints="quantity > 0 AND previous_stock >= 0 AND new_stock >= 0")
public class InventoryTransaction {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(nullable=false,updatable=false) private Product product;
 @Column(nullable=false,updatable=false,length=64) private String productSku;
 @Column(nullable=false,updatable=false,length=150) private String productName;
 @Enumerated(EnumType.STRING) @Column(nullable=false,updatable=false,length=30) private TransactionType transactionType;
 @Column(nullable=false,updatable=false) private int quantity;
 @Column(nullable=false,updatable=false) private int previousStock;
 @Column(nullable=false,updatable=false) private int newStock;
 @Enumerated(EnumType.STRING) @Column(nullable=false,updatable=false,length=30) private ReferenceType referenceType;
 @Column(updatable=false,length=100) private String referenceId;
 @Column(nullable=false,updatable=false,length=250) private String reason;
 @Column(updatable=false,length=2000) private String notes;
 @Column(nullable=false,updatable=false) private Instant createdAt;
 protected InventoryTransaction() {}
 public InventoryTransaction(Product product,TransactionType type,int quantity,int previous,int next,ReferenceType referenceType,String referenceId,String reason,String notes) {
  this.product=product; this.productSku=product.getSku(); this.productName=product.getName(); this.transactionType=type;
  this.quantity=quantity;this.previousStock=previous;this.newStock=next;this.referenceType=referenceType;this.referenceId=referenceId;this.reason=reason;this.notes=notes;this.createdAt=Instant.now();
 }
 public Long getId(){return id;} public Product getProduct(){return product;} public String getProductSku(){return productSku;} public String getProductName(){return productName;}
 public TransactionType getTransactionType(){return transactionType;} public int getQuantity(){return quantity;} public int getPreviousStock(){return previousStock;} public int getNewStock(){return newStock;}
 public ReferenceType getReferenceType(){return referenceType;} public String getReferenceId(){return referenceId;} public String getReason(){return reason;} public String getNotes(){return notes;} public Instant getCreatedAt(){return createdAt;}
}