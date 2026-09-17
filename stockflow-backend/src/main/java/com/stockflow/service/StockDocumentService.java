package com.stockflow.service;
import com.stockflow.dto.*; import com.stockflow.entity.*; import com.stockflow.repository.*; import com.stockflow.exception.DomainException;
import java.util.*; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional; import org.springframework.data.domain.*;
@Service @Transactional(readOnly=true)
public class StockDocumentService {
 private final StockDocumentRepository documents; private final SupplierRepository suppliers; private final InventoryService inventory;
 public StockDocumentService(StockDocumentRepository documents,SupplierRepository suppliers,InventoryService inventory){this.documents=documents;this.suppliers=suppliers;this.inventory=inventory;}
 @Transactional public DocumentResponse create(ReferenceType kind,DocumentRequest r){
  String number=r.number().strip().toUpperCase(Locale.ROOT);
  if(documents.existsByKindAndNumber(kind,number)) throw DomainException.conflict("DUPLICATE_DOCUMENT_NUMBER","This receipt/issue number already exists");
  Supplier supplier=null;
  if(kind==ReferenceType.PURCHASE_RECEIPT){
   if(r.supplierId()==null) throw DomainException.invalidStock("Supplier is required for a purchase receipt");
   supplier=suppliers.findById(r.supplierId()).orElseThrow(()->DomainException.notFound("Supplier",r.supplierId()));
  } else if(r.supplierId()!=null) throw DomainException.invalidStock("Sales issues do not accept a supplier");
  // Every multi-product operation locks the same ascending ID order, avoiding lock-order deadlocks.
  Map<Long,Product> locked=new TreeMap<>();
  r.lines().stream().map(DocumentRequest.Line::productId).distinct().sorted().forEach(id->locked.put(id,inventory.lock(id)));
  StockDocument document=new StockDocument(kind,number,r.date(),supplier,DtoMapper.clean(r.notes()));
  for(var line:r.lines()) document.addLine(new StockDocumentLine(document,locked.get(line.productId()),line.quantity(),line.unitPrice()));
  documents.saveAndFlush(document);
  for(var line:r.lines()) inventory.move(line.productId(),kind==ReferenceType.PURCHASE_RECEIPT?TransactionType.STOCK_IN:TransactionType.STOCK_OUT,line.quantity(),kind,document.getId().toString(),number,DtoMapper.clean(r.notes()));
  return DocumentResponse.from(document);
 }
 public DocumentResponse get(ReferenceType kind,Long id){StockDocument d=documents.findById(id).filter(x->x.getKind()==kind).orElseThrow(()->DomainException.notFound("Document",id));return DocumentResponse.from(d);}
 public PageResponse<DocumentResponse> list(ReferenceType kind,int page,int size){return PageResponse.from(documents.findByKind(kind,PageRequest.of(page,size,Sort.by("id").descending())).map(DocumentResponse::from));}
}