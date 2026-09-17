package com.stockflow.controller;
import com.stockflow.dto.*; import com.stockflow.entity.ReferenceType; import com.stockflow.service.StockDocumentService;
import java.net.URI; import jakarta.validation.Valid; import jakarta.validation.constraints.*; import org.springframework.web.bind.annotation.*; import org.springframework.http.ResponseEntity;
@RestController @RequestMapping("/api/purchase-receipts")
public class PurchaseReceiptController {
 private final StockDocumentService service; public PurchaseReceiptController(StockDocumentService service){this.service=service;}
 @PostMapping public ResponseEntity<DocumentResponse> create(@Valid @RequestBody DocumentRequest request){var d=service.create(ReferenceType.PURCHASE_RECEIPT,request);return ResponseEntity.created(URI.create("/api/purchase-receipts/"+d.id())).body(d);}
 @GetMapping public PageResponse<DocumentResponse> list(@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return service.list(ReferenceType.PURCHASE_RECEIPT,page,size);}
 @GetMapping("/{id}") public DocumentResponse get(@PathVariable @Positive Long id){return service.get(ReferenceType.PURCHASE_RECEIPT,id);}
}