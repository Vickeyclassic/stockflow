package com.stockflow.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
@Entity @Table(name="purchase_orders")
public class PurchaseOrder {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false,unique=true,length=100) private String orderNumber;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) private Supplier supplier;
 @Column(nullable=false) private LocalDate orderDate;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private PurchaseOrderStatus status=PurchaseOrderStatus.DRAFT;
 @Column(length=2000) private String notes;
 @Column(nullable=false,precision=30,scale=2) private BigDecimal totalAmount=BigDecimal.ZERO;
 @Column(nullable=false,updatable=false) private Instant createdAt=Instant.now();
 @OneToMany(mappedBy="order",cascade=CascadeType.ALL,orphanRemoval=true) @OrderBy("id ASC")
 private List<PurchaseOrderItem> items=new ArrayList<>();
 public Long getId(){return id;} public String getOrderNumber(){return orderNumber;}
 public Supplier getSupplier(){return supplier;} public LocalDate getOrderDate(){return orderDate;}
 public PurchaseOrderStatus getStatus(){return status;} public void setStatus(PurchaseOrderStatus v){status=v;}
 public String getNotes(){return notes;} public BigDecimal getTotalAmount(){return totalAmount;}
 public Instant getCreatedAt(){return createdAt;} public List<PurchaseOrderItem> getItems(){return Collections.unmodifiableList(items);}
 public void update(String number,Supplier supplier,LocalDate date,String notes){
  this.orderNumber=number;this.supplier=supplier;this.orderDate=date;this.notes=notes;items.clear();totalAmount=BigDecimal.ZERO;
 }
 public void addItem(Product product,int quantity,BigDecimal price){
  var item=new PurchaseOrderItem(this,product,quantity,price);items.add(item);totalAmount=totalAmount.add(item.getLineTotal());
 }
}

