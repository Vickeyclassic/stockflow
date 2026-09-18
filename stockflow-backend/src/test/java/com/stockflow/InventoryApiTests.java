package com.stockflow;

import com.stockflow.dto.StockAdjustmentRequest;
import com.stockflow.repository.*;
import com.stockflow.service.ProductService;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventoryApiTests {
    MockMvc mvc;
 @Autowired void configureAuthenticatedMvc(org.springframework.web.context.WebApplicationContext context) {
  // Default request authentication also applies to concurrent worker-thread requests.
  mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
   .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
   .defaultRequest(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/")
    .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("test-admin").roles("ADMIN")))
   .build();
 }
    @Autowired JsonMapper json;
    @Autowired ProductRepository products;
    @Autowired CategoryRepository categories;
    @Autowired SupplierRepository suppliers;
    @Autowired ProductService productService;

    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @BeforeEach void clean() {
        jdbc.update("delete from purchase_order_items"); jdbc.update("delete from purchase_orders"); jdbc.update("delete from sales_order_items"); jdbc.update("delete from sales_orders"); jdbc.update("delete from customers"); jdbc.update("delete from inventory_transactions"); jdbc.update("delete from stock_document_lines"); jdbc.update("delete from stock_documents");
        products.deleteAll(); categories.deleteAll(); suppliers.deleteAll();
    }

    private JsonNode create(String url, Object body) throws Exception {
        var result = mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isCreated()).andExpect(header().exists("Location")).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }
    private long category() throws Exception { return create("/api/categories", Map.of("name", "Hardware")).get("id").asLong(); }
    private long supplier() throws Exception { return create("/api/suppliers", Map.of("name", "Acme", "email", "hello@acme.example")).get("id").asLong(); }
    private Map<String,Object> product(long category) {
        var body = new HashMap<String,Object>();
        body.put("sku", "sf-001"); body.put("name", "Desk lamp"); body.put("categoryId", category);
        body.put("costPrice", "10.25"); body.put("sellingPrice", "19.99"); body.put("quantityInStock", 5);
        body.put("reorderLevel", 5); body.put("unit", "piece");
        return body;
    }
    private long createProduct() throws Exception { return create("/api/products", product(category())).get("id").asLong(); }

    @Test void categoryLifecycleAndTimestamps() throws Exception {
        var c = create("/api/categories", Map.of("name", " Hardware ", "description", "Tools"));
        long id = c.get("id").asLong();
        assertEquals("Hardware", c.get("name").asText());
        assertNotNull(c.get("createdAt")); assertNotNull(c.get("updatedAt"));
        mvc.perform(get("/api/categories")).andExpect(jsonPath("$[0].id").value(id));
        mvc.perform(get("/api/categories/{id}", id)).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Hardware"));
        mvc.perform(put("/api/categories/{id}", id).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Tools\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Tools"));
        mvc.perform(delete("/api/categories/{id}", id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/categories/{id}", id)).andExpect(status().isNotFound());
    }

    @Test void categoryNameIsRequiredAndCaseInsensitiveUnique() throws Exception {
        category();
        mvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" hardware \"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_CATEGORY"));
        mvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test void supplierLifecycleAndEmailValidation() throws Exception {
        long id = supplier();
        mvc.perform(get("/api/suppliers")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id));
        mvc.perform(get("/api/suppliers/{id}", id)).andExpect(jsonPath("$.email").value("hello@acme.example"));
        mvc.perform(put("/api/suppliers/{id}", id).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Updated Acme\",\"phone\":\"+91 (123) 456-7890\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Updated Acme"));
        mvc.perform(post("/api/suppliers").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Bad\",\"email\":\"invalid\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.email").exists());
        mvc.perform(delete("/api/suppliers/{id}", id)).andExpect(status().isNoContent());
    }

    @Test void productCreationRetrievalDefaultsAndMoneyPrecision() throws Exception {
        var body = product(category()); body.remove("quantityInStock");
        var p = create("/api/products", body);
        assertEquals("SF-001", p.get("sku").asText()); assertTrue(p.get("active").asBoolean());
        assertEquals(0, p.get("quantityInStock").asInt()); assertTrue(p.get("supplierId").isNull());
        mvc.perform(get("/api/products/{id}", p.get("id").asLong()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sellingPrice").value(19.99))
                .andExpect(jsonPath("$.categoryName").value("Hardware"))
                .andExpect(jsonPath("$.category").doesNotExist()).andExpect(jsonPath("$.version").doesNotExist());
    }

    @Test void duplicateSkuIsRejectedIncludingCaseVariants() throws Exception {
        long c = category(); var body = product(c); create("/api/products", body); body.put("sku", "SF-001");
        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_SKU"));
    }

    @ParameterizedTest
    @CsvSource({"costPrice,-0.01", "sellingPrice,-1", "quantityInStock,-1", "reorderLevel,-1", "sellingPrice,1.234", "costPrice,10000000000"})
    void invalidNumbersAreRejected(String field, String value) throws Exception {
        var body = product(category()); body.put(field, value);
        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors." + field).exists());
        assertEquals(0, products.count());
    }

    @Test void missingRequiredFieldsAndInvalidReferencesAreRejected() throws Exception {
        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.sku").exists())
                .andExpect(jsonPath("$.fieldErrors.categoryId").exists());
        var body = product(999999L);
        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isNotFound());
        body.put("categoryId", category()); body.put("supplierId", 999999L);
        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }

    @Test void updateChangesDetailsAndRelationshipsButPreservesStock() throws Exception {
        var body = product(category()); var p = create("/api/products", body); long id = p.get("id").asLong();
        long secondCategory = create("/api/categories", Map.of("name", "Lighting")).get("id").asLong();
        body.remove("quantityInStock"); body.put("name", "Updated lamp"); body.put("categoryId", secondCategory);
        body.put("supplierId", supplier()); body.put("active", false);
        mvc.perform(put("/api/products/{id}", id).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Updated lamp"))
                .andExpect(jsonPath("$.quantityInStock").value(5)).andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.categoryName").value("Lighting")).andExpect(jsonPath("$.supplierName").value("Acme"));
        body.put("supplierId", null);
        mvc.perform(put("/api/products/{id}", id).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.supplierId").isEmpty());
        // Phase 3 intentionally protects the opening-stock history.
        mvc.perform(delete("/api/products/{id}", id)).andExpect(status().isConflict());
        mvc.perform(get("/api/products/{id}", id)).andExpect(status().isOk());
    }

    @Test void normalUpdateCannotSilentlyChangeStock() throws Exception {
        var body = product(category()); long id = create("/api/products", body).get("id").asLong();
        body.put("active", true); body.put("quantityInStock", 100);
        mvc.perform(put("/api/products/{id}", id).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertEquals(5, productService.get(id).quantityInStock());
    }

    @Test void duplicateValuesAreAlsoRejectedOnUpdate() throws Exception {
        long c = category(); long otherCategory = create("/api/categories", Map.of("name", "Other")).get("id").asLong();
        mvc.perform(put("/api/categories/{id}", otherCategory).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Hardware\"}"))
                .andExpect(status().isConflict());
        var body = product(c); create("/api/products", body); body.put("sku", "SF-002");
        long id = create("/api/products", body).get("id").asLong();
        body.remove("quantityInStock"); body.put("active", true); body.put("sku", "sf-001");
        mvc.perform(put("/api/products/{id}", id).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_SKU"));
    }

    @Test void referencedCategoryAndSupplierCannotBeDeleted() throws Exception {
        long c = category(), s = supplier(); var body = product(c); body.put("supplierId", s); body.put("quantityInStock", 0);
        long id = create("/api/products", body).get("id").asLong();
        for (String url : List.of("/api/categories/" + c, "/api/suppliers/" + s)) {
            mvc.perform(delete(url)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"));
        }
        mvc.perform(delete("/api/products/{id}", id)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/categories/{id}", c)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/suppliers/{id}", s)).andExpect(status().isNoContent());
    }

    @Test void lowStockIncludesEqualityAndFilteringCombinesAllCriteria() throws Exception {
        long c = category(), s = supplier(); var body = product(c); body.put("supplierId", s);
        var p = create("/api/products", body); assertTrue(p.get("lowStock").asBoolean());
        body.put("sku", "SF-002"); body.put("quantityInStock", 6); body.put("active", false); create("/api/products", body);
        mvc.perform(get("/api/products").param("name", "LAMP").param("sku", "sf-001").param("categoryId", "" + c)
                .param("supplierId", "" + s).param("active", "true").param("lowStock", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].lowStock").value(true));
        mvc.perform(get("/api/products").param("lowStock", "false"))
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].sku").value("SF-002"));
        mvc.perform(get("/api/products").param("size", "1").param("page", "1"))
                .andExpect(jsonPath("$.content.length()").value(1)).andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/api/products").param("name", "%"))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test void stockAdjustmentUpdatesDerivedLowStockAndAllowsZeroBalance() throws Exception {
        long id = createProduct();
        mvc.perform(patch("/api/products/{id}/stock", id).contentType(MediaType.APPLICATION_JSON).content("{\"adjustment\":3}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.quantityInStock").value(8)).andExpect(jsonPath("$.lowStock").value(false));
        mvc.perform(patch("/api/products/{id}/stock", id).contentType(MediaType.APPLICATION_JSON).content("{\"adjustment\":-8}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.quantityInStock").value(0)).andExpect(jsonPath("$.lowStock").value(true));
    }

    @ParameterizedTest
    @CsvSource({"-6", "0", "2147483647"})
    void invalidStockAdjustmentsDoNotChangeStock(int adjustment) throws Exception {
        long id = createProduct();
        mvc.perform(patch("/api/products/{id}/stock", id).contentType(MediaType.APPLICATION_JSON).content("{\"adjustment\":" + adjustment + "}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_STOCK_ADJUSTMENT"));
        assertEquals(5, productService.get(id).quantityInStock());
    }

    @Test void fractionalAndMissingAdjustmentsAreRejected() throws Exception {
        long id = createProduct();
        for (String body : List.of("{}", "{\"adjustment\":1.5}")) {
            mvc.perform(patch("/api/products/{id}/stock", id).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertEquals(5, productService.get(id).quantityInStock());
    }

    @Test void concurrentAdjustmentsDoNotLoseUpdates() throws Exception {
        long id = createProduct();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); return productService.adjustStock(id, new StockAdjustmentRequest(2)); });
            var second = executor.submit(() -> { start.await(); return productService.adjustStock(id, new StockAdjustmentRequest(3)); });
            start.countDown(); first.get(10, TimeUnit.SECONDS); second.get(10, TimeUnit.SECONDS);
        }
        assertEquals(10, productService.get(id).quantityInStock());
    }

    @Test void concurrentWithdrawalsCannotOverspendStock() throws Exception {
        long id = createProduct(); var start = new CountDownLatch(1);
        Callable<Boolean> withdraw = () -> {
            start.await();
            try { productService.adjustStock(id, new StockAdjustmentRequest(-4)); return true; }
            catch (com.stockflow.exception.DomainException e) { assertEquals("INVALID_STOCK_ADJUSTMENT", e.getCode()); return false; }
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(withdraw); var second = executor.submit(withdraw); start.countDown();
            assertNotEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        }
        assertEquals(1, productService.get(id).quantityInStock());
    }

    @Test void errorsHaveConsistentShapeWithoutInternalDetails() throws Exception {
        for (String resource : List.of("products", "categories", "suppliers")) {
            mvc.perform(get("/api/" + resource + "/999999"))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"))
                    .andExpect(jsonPath("$.timestamp").exists()).andExpect(jsonPath("$.path").exists())
                    .andExpect(jsonPath("$.fieldErrors").isMap()).andExpect(jsonPath("$.trace").doesNotExist());
        }
        mvc.perform(get("/api/products").param("size", "101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/products").param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/products/not-a-number")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON).content("{bad"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test void corsAllowsInventoryWritesFromDevelopmentOriginOnly() throws Exception {
        for (String method : List.of("POST", "PUT", "PATCH", "DELETE")) {
            mvc.perform(options("/api/products").header("Origin", "http://localhost:5173")
                    .header("Access-Control-Request-Method", method).header("Access-Control-Request-Headers", "content-type"))
                    .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        }
        mvc.perform(options("/api/products").header("Origin", "https://untrusted.example").header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }
}
