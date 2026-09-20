package com.stockflow.config;

import com.stockflow.dto.*;
import com.stockflow.repository.*;
import com.stockflow.service.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Explicit, development-only sample catalog. Never alters a populated catalog. */
@Component
@Profile("dev & !prod")
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
public class DemoData implements CommandLineRunner {
    private final CategoryService categories;
    private final SupplierService suppliers;
    private final ProductService products;
    private final CustomerService customers;
    private final PurchaseOrderService purchases;
    private final SalesOrderService sales;
    private final CategoryRepository categoryRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final PurchaseOrderRepository purchaseRepository;
    private final SalesOrderRepository salesRepository;
    public DemoData(CategoryService categories, SupplierService suppliers, ProductService products,
                    CustomerService customers, PurchaseOrderService purchases, SalesOrderService sales,
                    CategoryRepository categoryRepository, SupplierRepository supplierRepository,
                    ProductRepository productRepository, CustomerRepository customerRepository,
                    PurchaseOrderRepository purchaseRepository, SalesOrderRepository salesRepository) {
        this.categories = categories; this.suppliers = suppliers; this.products = products;
        this.customers = customers; this.purchases = purchases; this.sales = sales;
        this.categoryRepository = categoryRepository; this.supplierRepository = supplierRepository;
        this.productRepository = productRepository; this.customerRepository = customerRepository;
        this.purchaseRepository = purchaseRepository; this.salesRepository = salesRepository;
    }
    @Override @Transactional
    public void run(String... args) {
        if (categoryRepository.count() + supplierRepository.count() + productRepository.count()
                + customerRepository.count() + purchaseRepository.count() + salesRepository.count() > 0) return;
        var office = categories.create(new CategoryRequest("Demo Office", "Sample office supplies"));
        var packing = categories.create(new CategoryRequest("Demo Packing", "Sample packing supplies"));
        var supplier = suppliers.create(new SupplierRequest("Demo Supply Co", "Demo contact", "supply@example.test", null, "Demo address"));
        var customer = customers.create(new CustomerRequest("Demo Customer", "customer@example.test", null, "Demo address", true));
        var notebook = products.create(new ProductCreateRequest("DEMO-NOTEBOOK", "Demo Notebook", null,
            office.id(), supplier.id(), new BigDecimal("2.00"), new BigDecimal("3.50"), 5, "each", 25, true));
        var box = products.create(new ProductCreateRequest("DEMO-BOX", "Demo Packing Box", null,
            packing.id(), supplier.id(), new BigDecimal("1.00"), new BigDecimal("2.00"), 10, "each", 5, true));
        var today = LocalDate.now();
        purchases.create(new PurchaseOrderRequest("DEMO-PO-001", supplier.id(), today, "Demo draft",
            List.of(new PurchaseOrderRequest.Item(box.id(), 20, new BigDecimal("1.00")))));
        sales.create(new SalesOrderRequest("DEMO-SO-001", customer.id(), today, "Demo draft",
            List.of(new SalesOrderRequest.Item(notebook.id(), 2, new BigDecimal("3.50")))));
        sales.create(new SalesOrderRequest("DEMO-SO-002", customer.id(), today, "Demo draft",
            List.of(new SalesOrderRequest.Item(box.id(), 1, new BigDecimal("2.00")))));
    }
}
