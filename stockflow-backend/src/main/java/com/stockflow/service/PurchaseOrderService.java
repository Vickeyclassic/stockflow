package com.stockflow.service;
import com.stockflow.dto.*;
import com.stockflow.entity.*;
import com.stockflow.repository.*;
import com.stockflow.exception.DomainException;
import java.time.LocalDate;
import java.util.*;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service @Transactional(readOnly=true)
public class PurchaseOrderService {
 private final PurchaseOrderRepository orders; private final SupplierRepository suppliers; private final InventoryService inventory;
 public PurchaseOrderService(PurchaseOrderRepository orders,SupplierRepository suppliers,InventoryService inventory){this.orders=orders;this.suppliers=suppliers;this.inventory=inventory;}
 private PurchaseOrder lock(Long id){return orders.findForUpdate(id).orElseThrow(()->DomainException.notFound("Purchase order",id));}
 private DomainException invalid(String message){return new DomainException(org.springframework.http.HttpStatus.BAD_REQUEST,"INVALID_ORDER",message);}
 public PurchaseOrderResponse get(Long id){return PurchaseOrderResponse.from(orders.findById(id).orElseThrow(()->DomainException.notFound("Purchase order",id)));}
 @Transactional public PurchaseOrderResponse create(PurchaseOrderRequest r){return save(new PurchaseOrder(),r);}
 @Transactional public PurchaseOrderResponse update(Long id,PurchaseOrderRequest r){
  PurchaseOrder o=lock(id);if(o.getStatus()!=PurchaseOrderStatus.DRAFT)throw DomainException.conflict("ORDER_NOT_EDITABLE","Only draft orders can be edited");return save(o,r);
 }
 private PurchaseOrderResponse save(PurchaseOrder o,PurchaseOrderRequest r){
  String number=r.orderNumber().strip().toUpperCase(Locale.ROOT);
  if(orders.existsByOrderNumberAndIdNot(number,o.getId()==null?0L:o.getId()))throw DomainException.conflict("DUPLICATE_ORDER_NUMBER","Order number already exists");
  Supplier supplier=suppliers.findById(r.supplierId()).orElseThrow(()->DomainException.notFound("Supplier",r.supplierId()));

  Map<Long,Product> products=new TreeMap<>();
  r.items().stream().map(PurchaseOrderRequest.Item::productId).distinct().sorted().forEach(id->{
   Product p=inventory.lock(id);if(!Boolean.TRUE.equals(p.getActive()))throw invalid("Product "+id+" must be active");products.put(id,p);
  });
  o.update(number,supplier,r.orderDate(),DtoMapper.clean(r.notes()));
  for(var i:r.items())o.addItem(products.get(i.productId()),i.quantity(),i.unitPrice());
  return PurchaseOrderResponse.from(orders.saveAndFlush(o));
 }
 @Transactional public PurchaseOrderResponse status(Long id,PurchaseOrderStatus next){
  PurchaseOrder o=lock(id);PurchaseOrderStatus current=o.getStatus();
  boolean allowed=current==PurchaseOrderStatus.DRAFT&&(next==PurchaseOrderStatus.ORDERED||next==PurchaseOrderStatus.CANCELLED)
   ||current==PurchaseOrderStatus.ORDERED&&(next==PurchaseOrderStatus.RECEIVED||next==PurchaseOrderStatus.CANCELLED);
  if(!allowed)throw DomainException.conflict("INVALID_ORDER_STATUS","Cannot change "+current+" to "+next);
  if(next==PurchaseOrderStatus.RECEIVED){
   // Lock every product in a consistent order before any stock movement.
   o.getItems().stream().map(i->i.getProduct().getId()).distinct().sorted().forEach(productId->{
    if(!Boolean.TRUE.equals(inventory.lock(productId).getActive()))throw invalid("Product "+productId+" must be active");
   });
   for(var i:o.getItems())inventory.move(i.getProduct().getId(),TransactionType.STOCK_IN,i.getQuantity(),ReferenceType.PURCHASE_ORDER,o.getId().toString(),"Receive "+o.getOrderNumber(),o.getNotes());
  }
  o.setStatus(next);return PurchaseOrderResponse.from(orders.saveAndFlush(o));
 }
 public PageResponse<PurchaseOrderResponse> list(String number,Long supplierId,PurchaseOrderStatus status,LocalDate from,LocalDate to,int page,int size){
  if(from!=null&&to!=null&&from.isAfter(to))throw invalid("dateFrom must be before or equal to dateTo");
  return PageResponse.from(orders.findAll((root,q,cb)->{
   var p=new ArrayList<Predicate>();
   if(number!=null&&!number.isBlank())p.add(cb.like(cb.upper(root.get("orderNumber")),"%"+number.strip().toUpperCase(Locale.ROOT).replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%",'\\'));
   if(supplierId!=null)p.add(cb.equal(root.get("supplier").get("id"),supplierId));
   if(status!=null)p.add(cb.equal(root.get("status"),status));
   if(from!=null)p.add(cb.greaterThanOrEqualTo(root.get("orderDate"),from));
   if(to!=null)p.add(cb.lessThanOrEqualTo(root.get("orderDate"),to));
   return cb.and(p.toArray(Predicate[]::new));
  },PageRequest.of(page,size,Sort.by("id").descending())).map(PurchaseOrderResponse::from));
 }
}

