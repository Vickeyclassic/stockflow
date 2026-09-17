package com.stockflow.controller;
import com.stockflow.dto.*;
import com.stockflow.entity.OrderStatus;
import com.stockflow.service.SalesOrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.time.LocalDate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/sales-orders")
public class SalesOrderController {
 private final SalesOrderService service;
 public SalesOrderController(SalesOrderService service){this.service=service;}
 @PostMapping public ResponseEntity<SalesOrderResponse> create(@Valid @RequestBody SalesOrderRequest r){
  var o=service.create(r);return ResponseEntity.created(URI.create("/api/sales-orders/"+o.id())).body(o);
 }
 @GetMapping public PageResponse<SalesOrderResponse> list(@RequestParam(required=false) String orderNumber,
  @RequestParam(required=false) @Positive Long customerId,@RequestParam(required=false) OrderStatus status,
  @RequestParam(required=false) LocalDate dateFrom,@RequestParam(required=false) LocalDate dateTo,
  @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){
  return service.list(orderNumber,customerId,status,dateFrom,dateTo,page,size);
 }
 @GetMapping("/{id}") public SalesOrderResponse get(@PathVariable @Positive Long id){return service.get(id);}
 @PutMapping("/{id}") public SalesOrderResponse update(@PathVariable @Positive Long id,@Valid @RequestBody SalesOrderRequest r){return service.update(id,r);}
 @PatchMapping("/{id}/status") public SalesOrderResponse status(@PathVariable @Positive Long id,@Valid @RequestBody OrderStatusRequest r){return service.status(id,r.status());}
}
