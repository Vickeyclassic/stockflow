package com.stockflow.service;

import com.stockflow.dto.*;
import com.stockflow.entity.Supplier;
import com.stockflow.repository.*;
import com.stockflow.exception.DomainException;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SupplierService {
    private final SupplierRepository suppliers;
    private final ProductRepository products;
    private final StockDocumentRepository documents; private final PurchaseOrderRepository orders;
    public SupplierService(SupplierRepository suppliers, ProductRepository products, StockDocumentRepository documents, PurchaseOrderRepository orders) { this.orders=orders; this.suppliers = suppliers; this.products = products; this.documents=documents; }
    public List<SupplierResponse> list() { return suppliers.findAll(Sort.by("name").and(Sort.by("id"))).stream().map(DtoMapper::supplier).toList(); }
    public SupplierResponse get(Long id) { return DtoMapper.supplier(require(id)); }
    private Supplier require(Long id) { return suppliers.findById(id).orElseThrow(() -> DomainException.notFound("Supplier", id)); }
    @Transactional
    public SupplierResponse create(SupplierRequest request) { return save(new Supplier(), request); }
    @Transactional
    public SupplierResponse update(Long id, SupplierRequest request) { return save(require(id), request); }
    private SupplierResponse save(Supplier supplier, SupplierRequest request) {
        supplier.setName(request.name().strip()); supplier.setContactPerson(DtoMapper.clean(request.contactPerson()));
        supplier.setEmail(DtoMapper.clean(request.email())); supplier.setPhone(DtoMapper.clean(request.phone())); supplier.setAddress(DtoMapper.clean(request.address()));
        return DtoMapper.supplier(suppliers.saveAndFlush(supplier));
    }
    @Transactional
    public void delete(Long id) {
        Supplier supplier = require(id);
        if (products.existsBySupplierId(id) || documents.existsBySupplierId(id) || orders.existsBySupplierId(id)) throw DomainException.conflict("RESOURCE_IN_USE", "Supplier is used by products, receipts or purchase orders and cannot be deleted");
        suppliers.delete(supplier); suppliers.flush();
    }
}

