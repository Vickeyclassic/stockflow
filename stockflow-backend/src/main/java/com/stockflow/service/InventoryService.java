package com.stockflow.service;
import com.stockflow.dto.*; import com.stockflow.entity.*; import com.stockflow.repository.*; import com.stockflow.exception.DomainException;
import java.time.Instant; import java.util.*; import jakarta.persistence.criteria.Predicate;
import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional; import org.springframework.data.domain.*;
@Service @Transactional(readOnly=true)
public class InventoryService {
 private final ProductRepository products; private final InventoryTransactionRepository transactions;
 public InventoryService(ProductRepository products,InventoryTransactionRepository transactions){this.products=products;this.transactions=transactions;}
 public Product lock(Long id){return products.findForUpdate(id).orElseThrow(()->DomainException.notFound("Product",id));}
 @Transactional public TransactionResponse manual(MovementRequest r){
  if(r.referenceType()!=null && r.referenceType()!=ReferenceType.MANUAL) throw DomainException.invalidStock("Manual movements must use MANUAL reference type; use the receipt or issue API for documents");
  return TransactionResponse.from(move(r.productId(),r.transactionType(),r.quantity(),ReferenceType.MANUAL,DtoMapper.clean(r.referenceId()),r.reason().strip(),DtoMapper.clean(r.notes())));
 }
 @Transactional public void initialize(Product product,Integer quantity){
  product.setQuantityInStock(0); products.saveAndFlush(product);
  if(quantity!=null && quantity>0) move(product.getId(),TransactionType.STOCK_IN,quantity,ReferenceType.OPENING_STOCK,null,"Initial product stock",null);
 }
 @Transactional public ProductResponse legacy(Long id,StockAdjustmentRequest r){
  long quantity=Math.abs((long)r.adjustment());
  if(quantity==0 || quantity>Integer.MAX_VALUE) throw DomainException.invalidStock("Adjustment must be nonzero and within the supported quantity range");
  move(id,r.adjustment()>0?TransactionType.ADJUSTMENT_IN:TransactionType.ADJUSTMENT_OUT,(int)quantity,ReferenceType.LEGACY_ADJUSTMENT,null,"Legacy stock adjustment",null);
  return DtoMapper.product(lock(id));
 }
 @Transactional public InventoryTransaction move(Long id,TransactionType type,int quantity,ReferenceType reference,String referenceId,String reason,String notes){
  if(type==null || quantity<=0) throw DomainException.invalidStock("Quantity must be positive and movement type is required");
  Product p=lock(id); int previous=p.getQuantityInStock(); long next=(long)previous+(long)type.direction()*quantity;
  if(next<0) throw DomainException.invalidStock("Insufficient stock: movement would make stock negative");
  if(next>Integer.MAX_VALUE) throw DomainException.invalidStock("Movement exceeds the supported stock quantity");
  p.setQuantityInStock((int)next); products.saveAndFlush(p);
  return transactions.saveAndFlush(new InventoryTransaction(p,type,quantity,previous,(int)next,reference,referenceId,reason,notes));
 }
 public TransactionResponse get(Long id){return TransactionResponse.from(transactions.findById(id).orElseThrow(()->DomainException.notFound("Inventory transaction",id)));}
 public PageResponse<TransactionResponse> list(Long productId,TransactionType type,Instant from,Instant to,ReferenceType reference,String referenceId,int page,int size){
  if(from!=null && to!=null && from.isAfter(to)) throw DomainException.invalidStock("dateFrom must be before or equal to dateTo");
  return PageResponse.from(transactions.findAll((root,q,cb)->{
   var predicates=new ArrayList<Predicate>();
   if(productId!=null) predicates.add(cb.equal(root.get("product").get("id"),productId));
   if(type!=null) predicates.add(cb.equal(root.get("transactionType"),type));
   if(from!=null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"),from));
   if(to!=null) predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"),to));
   if(reference!=null) predicates.add(cb.equal(root.get("referenceType"),reference));
   if(referenceId!=null) predicates.add(cb.equal(root.get("referenceId"),referenceId));
   return cb.and(predicates.toArray(Predicate[]::new));
  },PageRequest.of(page,size,Sort.by(Sort.Order.desc("createdAt"),Sort.Order.desc("id")))).map(TransactionResponse::from));
 }
 public record Dashboard(long productCount,long lowStockProductCount,long recentStockIn,long recentStockOut,Instant since,List<TransactionResponse> recentMovements){}
 public Dashboard dashboard(){Instant since=Instant.now().minus(30,java.time.temporal.ChronoUnit.DAYS);return new Dashboard(products.count(),products.count(ProductSpecifications.filter(null,null,null,null,null,true)),transactions.totalSince(since,List.of(TransactionType.STOCK_IN,TransactionType.ADJUSTMENT_IN)),transactions.totalSince(since,List.of(TransactionType.STOCK_OUT,TransactionType.ADJUSTMENT_OUT)),since,list(null,null,since,null,null,null,0,10).content());}
}