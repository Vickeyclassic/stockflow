package com.stockflow.controller;

import com.stockflow.dto.*;
import com.stockflow.service.CustomerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {
    private final CustomerService service;
    public CustomerController(CustomerService service) { this.service = service; }
    @GetMapping public List<CustomerResponse> list() { return service.list(); }
    @GetMapping("/{id}") public CustomerResponse get(@PathVariable @Positive Long id) { return service.get(id); }
    @PostMapping public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CustomerRequest request) {
        var result = service.create(request);
        return ResponseEntity.created(URI.create("/api/customers/" + result.id())).body(result);
    }
    @PutMapping("/{id}") public CustomerResponse update(@PathVariable @Positive Long id, @Valid @RequestBody CustomerRequest request) { return service.update(id, request); }
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable @Positive Long id) { service.delete(id); return ResponseEntity.noContent().build(); }
}
