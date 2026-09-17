package com.stockflow.controller;
import com.stockflow.dto.*; import com.stockflow.entity.ReferenceType; import com.stockflow.service.StockDocumentService;
import java.net.URI; import jakarta.validation.Valid; import jakarta.validation.constraints.*; import org.springframework.web.bind.annotation.*; import org.springframework.http.ResponseEntity;
@RestController @RequestMapping("/api/sales-issues")
public class SalesIssueController {
 private final StockDocumentService service; public SalesIssueController(StockDocumentService service){this.service=service;}
 @PostMapping public ResponseEntity<DocumentResponse> create(@Valid @RequestBody DocumentRequest request){var d=service.create(ReferenceType.SALES_ISSUE,request);return ResponseEntity.created(URI.create("/api/sales-issues/"+d.id())).body(d);}
 @GetMapping public PageResponse<DocumentResponse> list(@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return service.list(ReferenceType.SALES_ISSUE,page,size);}
 @GetMapping("/{id}") public DocumentResponse get(@PathVariable @Positive Long id){return service.get(ReferenceType.SALES_ISSUE,id);}
}