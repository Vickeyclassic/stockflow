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
public class SalesOrderService {
 private final SalesOrderRepository orders; private final CustomerRepository customers; private final InventoryService inventory;
 public SalesOrderService(SalesOrderRepository orders,CustomerRepository customers,InventoryService inventory){this.orders=orders;this.customers=customers;this.inventory=inventory;}
 private SalesOrder lock(Long id){return orders.findForUpdate(id).orElseThrow(()->DomainException.notFound("Sales order",id));}
 private DomainException invalid(String message){return new DomainException(org.springframework.http.HttpStatus.BAD_REQUEST,"INVALID_ORDER",message);}
 public SalesOrderResponse get(Long id){return SalesOrderResponse.from(orders.findById(id).orElseThrow(()->DomainException.notFound("Sales order",id)));}
 @Transactional public SalesOrderResponse create(SalesOrderRequest r){return save(new SalesOrder(),r);}
 @Transactional public SalesOrderResponse update(Long id,SalesOrderRequest r){
  SalesOrder o=lock(id);if(o.getStatus()!=OrderStatus.DRAFT)throw DomainException.conflict("ORDER_NOT_EDITABLE","Only draft orders can be edited");return save(o,r);
 }
 private SalesOrderResponse save(SalesOrder o,SalesOrderRequest r){
  String number=r.orderNumber().strip().toUpperCase(Locale.ROOT);
  if(orders.existsByOrderNumberAndIdNot(number,o.getId()==null?0L:o.getId()))throw DomainException.conflict("DUPLICATE_ORDER_NUMBER","Order number already exists");
  Customer customer=customers.findById(r.customerId()).orElseThrow(()->DomainException.notFound("Customer",r.customerId()));
  if(!customer.isActive())throw invalid("Customer must be active");
  Map<Long,Product> products=new TreeMap<>();
  r.items().stream().map(SalesOrderRequest.Item::productId).distinct().sorted().forEach(id->{
   Product p=inventory.lock(id);if(!Boolean.TRUE.equals(p.getActive()))throw invalid("Product "+id+" must be active");products.put(id,p);
  });
  o.update(number,customer,r.orderDate(),DtoMapper.clean(r.notes()));
  for(var i:r.items())o.addItem(products.get(i.productId()),i.quantity(),i.unitPrice());
  return SalesOrderResponse.from(orders.saveAndFlush(o));
 }
 @Transactional public SalesOrderResponse status(Long id,OrderStatus next){
  SalesOrder o=lock(id);OrderStatus current=o.getStatus();
  boolean allowed=current==OrderStatus.DRAFT&&(next==OrderStatus.CONFIRMED||next==OrderStatus.CANCELLED)
   ||current==OrderStatus.CONFIRMED&&(next==OrderStatus.FULFILLED||next==OrderStatus.CANCELLED);
  if(!allowed)throw DomainException.conflict("INVALID_ORDER_STATUS","Cannot change "+current+" to "+next);
  if(next==OrderStatus.FULFILLED){
   // Lock every product in a consistent order before any stock movement.
   o.getItems().stream().map(i->i.getProduct().getId()).distinct().sorted().forEach(productId->{
    if(!Boolean.TRUE.equals(inventory.lock(productId).getActive()))throw invalid("Product "+productId+" must be active");
   });
   for(var i:o.getItems())inventory.move(i.getProduct().getId(),TransactionType.STOCK_OUT,i.getQuantity(),ReferenceType.SALES_ORDER,o.getId().toString(),"Fulfill "+o.getOrderNumber(),o.getNotes());
  }
  o.setStatus(next);return SalesOrderResponse.from(orders.saveAndFlush(o));
 }
 public PageResponse<SalesOrderResponse> list(String number,Long customerId,OrderStatus status,LocalDate from,LocalDate to,int page,int size){
  if(from!=null&&to!=null&&from.isAfter(to))throw invalid("dateFrom must be before or equal to dateTo");
  return PageResponse.from(orders.findAll((root,q,cb)->{
   var p=new ArrayList<Predicate>();
   if(number!=null&&!number.isBlank())p.add(cb.like(cb.upper(root.get("orderNumber")),"%"+number.strip().toUpperCase(Locale.ROOT).replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%",'\\'));
   if(customerId!=null)p.add(cb.equal(root.get("customer").get("id"),customerId));
   if(status!=null)p.add(cb.equal(root.get("status"),status));
   if(from!=null)p.add(cb.greaterThanOrEqualTo(root.get("orderDate"),from));
   if(to!=null)p.add(cb.lessThanOrEqualTo(root.get("orderDate"),to));
   return cb.and(p.toArray(Predicate[]::new));
  },PageRequest.of(page,size,Sort.by("id").descending())).map(SalesOrderResponse::from));
 }
}
