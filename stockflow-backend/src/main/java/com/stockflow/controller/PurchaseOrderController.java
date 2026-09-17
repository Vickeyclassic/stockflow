package com.stockflow.controller;
import com.stockflow.dto.*;
import com.stockflow.entity.PurchaseOrderStatus;
import com.stockflow.service.PurchaseOrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.time.LocalDate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/purchase-orders")
public class PurchaseOrderController {
 private final PurchaseOrderService service;
 public PurchaseOrderController(PurchaseOrderService service){this.service=service;}
 @PostMapping public ResponseEntity<PurchaseOrderResponse> create(@Valid @RequestBody PurchaseOrderRequest r){
  var o=service.create(r);return ResponseEntity.created(URI.create("/api/purchase-orders/"+o.id())).body(o);
 }
 @GetMapping public PageResponse<PurchaseOrderResponse> list(@RequestParam(required=false) String orderNumber,
  @RequestParam(required=false) @Positive Long supplierId,@RequestParam(required=false) PurchaseOrderStatus status,
  @RequestParam(required=false) LocalDate dateFrom,@RequestParam(required=false) LocalDate dateTo,
  @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){
  return service.list(orderNumber,supplierId,status,dateFrom,dateTo,page,size);
 }
 @GetMapping("/{id}") public PurchaseOrderResponse get(@PathVariable @Positive Long id){return service.get(id);}
 @PutMapping("/{id}") public PurchaseOrderResponse update(@PathVariable @Positive Long id,@Valid @RequestBody PurchaseOrderRequest r){return service.update(id,r);}
 @PatchMapping("/{id}/status") public PurchaseOrderResponse status(@PathVariable @Positive Long id,@Valid @RequestBody PurchaseOrderStatusRequest r){return service.status(id,r.status());}
}

