package com.stockflow.entity;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
@Entity @Table(name="stock_documents",uniqueConstraints=@UniqueConstraint(name="uk_document_number",columnNames={"kind","document_number"}))
@org.hibernate.annotations.Immutable
public class StockDocument {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Enumerated(EnumType.STRING) @Column(nullable=false,updatable=false,length=30) private ReferenceType kind;
 @Column(name="document_number",nullable=false,updatable=false,length=100) private String number;
 @Column(nullable=false,updatable=false) private LocalDate documentDate;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(updatable=false) private Supplier supplier;
 @Column(updatable=false,length=150) private String supplierName;
 @Column(updatable=false,length=2000) private String notes;
 @Column(nullable=false,updatable=false) private Instant createdAt;
 @OneToMany(mappedBy="document",cascade=CascadeType.PERSIST) @OrderBy("id ASC") private List<StockDocumentLine> lines=new ArrayList<>();
 protected StockDocument() {}
 public StockDocument(ReferenceType kind,String number,LocalDate date,Supplier supplier,String notes){this.kind=kind;this.number=number;this.documentDate=date;this.supplier=supplier;this.supplierName=supplier==null?null:supplier.getName();this.notes=notes;this.createdAt=Instant.now();}
 public void addLine(StockDocumentLine line){lines.add(line);}
 public Long getId(){return id;} public ReferenceType getKind(){return kind;} public String getNumber(){return number;} public LocalDate getDocumentDate(){return documentDate;}
 public Supplier getSupplier(){return supplier;} public String getSupplierName(){return supplierName;} public String getNotes(){return notes;} public Instant getCreatedAt(){return createdAt;} public List<StockDocumentLine> getLines(){return Collections.unmodifiableList(lines);}
}