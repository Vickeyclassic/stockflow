package com.stockflow.controller;
import com.stockflow.dto.*; import com.stockflow.entity.*; import com.stockflow.service.InventoryService;
import java.net.URI; import java.time.Instant; import jakarta.validation.Valid; import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*; import org.springframework.http.ResponseEntity;
@RestController @RequestMapping("/api/inventory")
public class InventoryController {
 private final InventoryService service; public InventoryController(InventoryService service){this.service=service;}
 @PostMapping("/movements") public ResponseEntity<TransactionResponse> create(@Valid @RequestBody MovementRequest request){var t=service.manual(request);return ResponseEntity.created(URI.create("/api/inventory/transactions/"+t.id())).body(t);}
 @GetMapping("/transactions/{id}") public TransactionResponse get(@PathVariable @Positive Long id){return service.get(id);}
 @GetMapping("/transactions") public PageResponse<TransactionResponse> list(@RequestParam(required=false) @Positive Long productId,@RequestParam(required=false) TransactionType transactionType,@RequestParam(required=false) Instant dateFrom,@RequestParam(required=false) Instant dateTo,@RequestParam(required=false) ReferenceType referenceType,@RequestParam(required=false) @Size(max=100) String referenceId,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return service.list(productId,transactionType,dateFrom,dateTo,referenceType,referenceId,page,size);}
 @GetMapping("dashboard") public InventoryService.Dashboard dashboard(){return service.dashboard();}
}