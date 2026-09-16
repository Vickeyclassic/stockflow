package com.stockflow.controller;

import com.stockflow.dto.*;
import com.stockflow.service.SupplierService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {
    private final SupplierService service;
    public SupplierController(SupplierService service) { this.service = service; }
    @GetMapping public List<SupplierResponse> list() { return service.list(); }
    @GetMapping("/{id}") public SupplierResponse get(@PathVariable @Positive Long id) { return service.get(id); }
    @PostMapping public ResponseEntity<SupplierResponse> create(@Valid @RequestBody SupplierRequest request) {
        var result = service.create(request);
        return ResponseEntity.created(URI.create("/api/suppliers/" + result.id())).body(result);
    }
    @PutMapping("/{id}") public SupplierResponse update(@PathVariable @Positive Long id, @Valid @RequestBody SupplierRequest request) { return service.update(id, request); }
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable @Positive Long id) { service.delete(id); return ResponseEntity.noContent().build(); }
}

