package com.stockflow;

import com.stockflow.config.DemoData;
import com.stockflow.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"app.demo.enabled=true",
    "spring.datasource.url=jdbc:h2:mem:stockflow_demo_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "logging.level.org.hibernate.SQL=WARN"})
@ActiveProfiles({"test", "dev"})
class DemoDataTests {
    @Autowired DemoData demo;
    @Autowired ProductRepository products;
    @Autowired CategoryRepository categories;
    @Autowired SupplierRepository suppliers;
    @Autowired CustomerRepository customers;
    @Autowired SalesOrderRepository sales;
    @Autowired PurchaseOrderRepository purchases;

    @Test void seedsOnceWithoutReplacingExistingData() {
        assertEquals(2, products.count());
        assertEquals(2, categories.count());
        assertEquals(1, suppliers.count());
        assertEquals(1, customers.count());
        assertEquals(2, sales.count());
        assertEquals(1, purchases.count());
        var ids = products.findAll().stream().map(p -> p.getId()).toList();
        demo.run();
        assertEquals(ids, products.findAll().stream().map(p -> p.getId()).toList());
        assertEquals(2, sales.count());
    }

    @Test void productionAndDefaultConfigurationsCannotSeed() {
        for (String profiles : new String[]{"prod", "dev,prod", "test"}) {
            new ApplicationContextRunner().withUserConfiguration(DemoData.class)
                .withPropertyValues("spring.profiles.active=" + profiles, "app.demo.enabled=true")
                .run(context -> { assertNull(context.getStartupFailure()); assertTrue(context.getBeansOfType(DemoData.class).isEmpty()); });
        }
        new ApplicationContextRunner().withUserConfiguration(DemoData.class)
            .withPropertyValues("spring.profiles.active=dev")
            .run(context -> { assertNull(context.getStartupFailure()); assertTrue(context.getBeansOfType(DemoData.class).isEmpty()); });
    }
}
