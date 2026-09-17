package com.stockflow.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
@Entity @Table(name="purchase_order_items")
public class PurchaseOrderItem {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="purchase_order_id") private PurchaseOrder order;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) private Product product;
 @Column(nullable=false) private int quantity;
 @Column(nullable=false,precision=12,scale=2) private BigDecimal unitPrice;
 @Column(nullable=false,precision=30,scale=2) private BigDecimal lineTotal;
 protected PurchaseOrderItem(){}
 public PurchaseOrderItem(PurchaseOrder order,Product product,int quantity,BigDecimal price){
  this.order=order;this.product=product;this.quantity=quantity;this.unitPrice=price;this.lineTotal=price.multiply(BigDecimal.valueOf(quantity));
 }
 public Product getProduct(){return product;} public int getQuantity(){return quantity;}
 public BigDecimal getUnitPrice(){return unitPrice;} public BigDecimal getLineTotal(){return lineTotal;}
}

