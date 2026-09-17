package com.stockflow.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
@Entity @Table(name="stock_document_lines") @org.hibernate.annotations.Immutable
@org.hibernate.annotations.Check(constraints="quantity > 0 AND unit_price >= 0")
public class StockDocumentLine {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(nullable=false,updatable=false) private StockDocument document;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(nullable=false,updatable=false) private Product product;
 @Column(nullable=false,updatable=false,length=64) private String productSku;
 @Column(nullable=false,updatable=false,length=150) private String productName;
 @Column(nullable=false,updatable=false) private int quantity;
 @Column(nullable=false,updatable=false,precision=12,scale=2) private BigDecimal unitPrice;
 protected StockDocumentLine(){}
 public StockDocumentLine(StockDocument document,Product product,int quantity,BigDecimal price){this.document=document;this.product=product;this.quantity=quantity;this.unitPrice=price;this.productSku=product.getSku();this.productName=product.getName();}
 public Long getId(){return id;} public Product getProduct(){return product;} public String getProductSku(){return productSku;} public String getProductName(){return productName;} public int getQuantity(){return quantity;} public BigDecimal getUnitPrice(){return unitPrice;}
}