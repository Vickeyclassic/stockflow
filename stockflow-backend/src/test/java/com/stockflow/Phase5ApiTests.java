package com.stockflow;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class Phase5ApiTests {
    @Autowired MockMvc mvc; @Autowired JsonMapper json; @Autowired JdbcTemplate jdbc;
    @BeforeEach void clear() {
        for (String table : List.of("purchase_order_items", "purchase_orders", "sales_order_items", "sales_orders", "customers", "inventory_transactions", "stock_document_lines", "stock_documents", "products", "categories", "suppliers")) jdbc.update("delete from " + table);
    }
    JsonNode create(String path, Object body) throws Exception {
        return json.readTree(mvc.perform(post("/api/" + path).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
    JsonNode read(String path) throws Exception { return json.readTree(mvc.perform(get("/api/" + path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()); }
    void transition(String path, long id, String status) throws Exception {
        mvc.perform(patch("/api/" + path + "/" + id + "/status").contentType("application/json").content(json.writeValueAsString(Map.of("status", status)))).andExpect(status().isOk());
    }
    void amount(String expected, JsonNode actual) { assertEquals(0, new BigDecimal(expected).compareTo(new BigDecimal(actual.asText()))); }
    @Test void emptyAndInvalidRanges() throws Exception {
        var dashboard = read("dashboard"); assertEquals(0, dashboard.get("totalProducts").asInt()); amount("0", dashboard.get("totalInventoryValue"));
        var report = read("reports"); amount("0", report.get("totalSalesValue")); amount("0", report.get("totalPurchaseValue"));
        for (String key : List.of("recentPurchaseOrders", "recentSalesOrders", "recentStockMovements", "topSellingProducts", "sales", "purchases", "stockMovements")) assertTrue(report.get(key).isEmpty());
        for (String query : List.of("dateFrom=bad", "dateFrom=2026-09-18&dateTo=2026-09-17", "dateFrom=0000-01-01")) mvc.perform(get("/api/reports?" + query)).andExpect(status().isBadRequest());
    }
    @Test void exactTotalsStatusExclusionsDatesAndRankings() throws Exception {
        long category = create("categories", Map.of("name", "Reports")).get("id").asLong();
        long supplier = create("suppliers", Map.of("name", "Supplier")).get("id").asLong();
        long customer = create("customers", Map.of("name", "Customer")).get("id").asLong();
        long product = create("products", Map.of("sku", "REPORT", "name", "Report product", "categoryId", category, "costPrice", "0.10", "sellingPrice", "0.30", "quantityInStock", 10, "reorderLevel", 10, "unit", "piece")).get("id").asLong();
        for (String kind : List.of("purchase-orders", "sales-orders")) {
            for (int i = 0; i < 4; i++) {
                var body = new HashMap<String, Object>(Map.of("orderNumber", kind + i, "orderDate", i == 3 ? "2026-09-18" : "2026-09-17", "items", List.of(Map.of("productId", product, "quantity", 3, "unitPrice", "0.10"))));
                boolean purchase = kind.equals("purchase-orders"); body.put(purchase ? "supplierId" : "customerId", purchase ? supplier : customer);
                long id = create(kind, body).get("id").asLong();
                if (i == 1) transition(kind, id, "CANCELLED");
                if (i >= 2) { transition(kind, id, purchase ? "ORDERED" : "CONFIRMED"); transition(kind, id, purchase ? "RECEIVED" : "FULFILLED"); }
            }
        }
        jdbc.update("update inventory_transactions set created_at = ?", java.sql.Timestamp.valueOf("2026-09-17 23:59:59"));
        var m = read("dashboard");
        for (String key : List.of("totalProducts", "totalCustomers", "totalSuppliers", "lowStockCount")) assertEquals(1, m.get(key).asInt());
        amount("1.00", m.get("totalInventoryValue"));
        var r = read("reports?dateFrom=2026-09-17&dateTo=2026-09-17");
        amount("0.30", r.get("totalSalesValue")); amount("0.30", r.get("totalPurchaseValue"));
        assertEquals(3, r.get("recentSalesOrders").size()); assertEquals(3, r.get("recentPurchaseOrders").size());
        assertEquals("FULFILLED", r.get("recentSalesOrders").get(0).get("status").asText());
        assertEquals(5, r.get("recentStockMovements").size());
        assertEquals(3, r.get("topSellingProducts").get(0).get("quantity").asInt());
        amount("0.30", r.get("topSellingProducts").get(0).get("salesValue"));
        amount("0.30", r.get("sales").get(0).get("value")); amount("0.30", r.get("purchases").get(0).get("value"));
        assertEquals(22, r.get("stockMovements").valueStream().mapToInt(n -> n.get("quantity").asInt()).sum());
        amount("0.60", read("reports").get("totalSalesValue"));
        assertTrue(read("reports?dateFrom=2026-09-18").get("recentStockMovements").isEmpty());
        assertTrue(read("reports?dateTo=2026-09-16").get("recentStockMovements").isEmpty());
        amount("0", read("reports?dateFrom=2026-09-19").get("totalSalesValue"));
    }
}
