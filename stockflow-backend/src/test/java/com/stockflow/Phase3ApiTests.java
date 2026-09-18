package com.stockflow;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class Phase3ApiTests {
 MockMvc mvc;
 @Autowired void configureAuthenticatedMvc(org.springframework.web.context.WebApplicationContext context) {
  // Default request authentication also applies to concurrent worker-thread requests.
  mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
   .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
   .defaultRequest(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/")
    .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("test-admin").roles("ADMIN")))
   .build();
 } @Autowired JsonMapper json; @Autowired JdbcTemplate jdbc;
 long category,supplier; int sequence;
 @BeforeEach void setup() throws Exception {
  for(String table:List.of("purchase_order_items","purchase_orders","sales_order_items","sales_orders","customers","inventory_transactions","stock_document_lines","stock_documents","products","categories","suppliers")) jdbc.update("delete from "+table);
  category=postJson("/api/categories",Map.of("name","Phase3"),201).get("id").asLong();
  supplier=postJson("/api/suppliers",Map.of("name","Supplier"),201).get("id").asLong();
 }
 JsonNode postJson(String path,Object body,int expected) throws Exception {var r=mvc.perform(post(path).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().is(expected)).andReturn();return json.readTree(r.getResponse().getContentAsString());}
 JsonNode getJson(String path) throws Exception {return json.readTree(mvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
 long product(int stock) throws Exception {return postJson("/api/products",Map.of("sku","P-"+(++sequence),"name","Product","categoryId",category,"costPrice","1.25","sellingPrice","2.50","quantityInStock",stock,"reorderLevel",2,"unit","piece"),201).get("id").asLong();}
 Map<String,Object> movement(long id,String type,int quantity){return Map.of("productId",id,"transactionType",type,"quantity",quantity,"reason","Test movement");}
 Map<String,Object> line(long id,int quantity){return Map.of("productId",id,"quantity",quantity,"unitPrice","1.25");}
 Map<String,Object> document(boolean purchase,String number,List<Map<String,Object>> lines){var b=new HashMap<String,Object>();b.put("number",number);b.put("date","2026-09-17");b.put("lines",lines);if(purchase)b.put("supplierId",supplier);return b;}
 int stock(long id)throws Exception{return getJson("/api/products/"+id).get("quantityInStock").asInt();}
 long count(){return jdbc.queryForObject("select count(*) from inventory_transactions",Long.class);}

 @Test void stockInRecordsBeforeAfterAndSnapshot() throws Exception {long p=product(0);var t=postJson("/api/inventory/movements",movement(p,"STOCK_IN",7),201);assertEquals(0,t.get("previousStock").asInt());assertEquals(7,t.get("newStock").asInt());assertEquals(7,stock(p));assertEquals(1,count());assertEquals("Product",getJson("/api/inventory/transactions/"+t.get("id").asLong()).get("productName").asText());}
 @Test void stockOutRecordsBeforeAfter() throws Exception {long p=product(8);var t=postJson("/api/inventory/movements",movement(p,"STOCK_OUT",3),201);assertEquals(8,t.get("previousStock").asInt());assertEquals(5,t.get("newStock").asInt());assertEquals(2,count());}
 @Test void openingStockIsAudited() throws Exception {long p=product(4);var page=getJson("/api/inventory/transactions?productId="+p);assertEquals("OPENING_STOCK",page.get("content").get(0).get("referenceType").asText());assertEquals(0,page.get("content").get(0).get("previousStock").asInt());}
 @Test void insufficientStockLeavesNoHistory() throws Exception {long p=product(2);postJson("/api/inventory/movements",movement(p,"STOCK_OUT",3),400);assertEquals(2,stock(p));assertEquals(1,count());}
 @ParameterizedTest @ValueSource(ints={0,-1}) void quantityMustBePositive(int quantity)throws Exception{long p=product(0);postJson("/api/inventory/movements",movement(p,"STOCK_IN",quantity),400);assertEquals(0,count());assertEquals(0,stock(p));}
 @Test void adjustmentTypesAndLegacyEndpointAreAudited()throws Exception{long p=product(5);postJson("/api/inventory/movements",movement(p,"ADJUSTMENT_IN",3),201);postJson("/api/inventory/movements",movement(p,"ADJUSTMENT_OUT",2),201);mvc.perform(patch("/api/products/"+p+"/stock").contentType("application/json").content("{\"adjustment\":-1}")).andExpect(status().isOk());assertEquals(5,stock(p));assertEquals(4,count());assertEquals("LEGACY_ADJUSTMENT",getJson("/api/inventory/transactions").get("content").get(0).get("referenceType").asText());}
 @Test void purchaseReceiptIncreasesStockAndReferencesDocument()throws Exception{long p=product(0);var d=postJson("/api/purchase-receipts",document(true," pr-1 ",List.of(line(p,5))),201);assertEquals("PR-1",d.get("number").asText());assertEquals(5,stock(p));var t=getJson("/api/inventory/transactions").get("content").get(0);assertEquals("PURCHASE_RECEIPT",t.get("referenceType").asText());assertEquals(d.get("id").asText(),t.get("referenceId").asText());assertEquals(1,getJson("/api/purchase-receipts").get("totalElements").asInt());assertEquals(1,getJson("/api/purchase-receipts/"+d.get("id").asText()).get("lines").size());}
 @Test void multilinePurchaseAndRepeatedProductsAccumulate()throws Exception{long a=product(0),b=product(0);postJson("/api/purchase-receipts",document(true,"PR-1",List.of(line(b,2),line(a,3),line(a,4))),201);assertEquals(7,stock(a));assertEquals(2,stock(b));assertEquals(3,count());}
 @Test void purchaseInvalidLineRollsBack()throws Exception{long p=product(0);postJson("/api/purchase-receipts",document(true,"PR-1",List.of(line(p,5),line(999999,1))),404);assertEquals(0,stock(p));assertEquals(0,count());assertEquals(0,getJson("/api/purchase-receipts").get("totalElements").asInt());}
 @Test void purchaseOverflowRollsBackEarlierMovementAndDocument()throws Exception{long a=product(0),b=product(Integer.MAX_VALUE);postJson("/api/purchase-receipts",document(true,"PR-1",List.of(line(a,5),line(b,1))),400);assertEquals(0,stock(a));assertEquals(Integer.MAX_VALUE,stock(b));assertEquals(1,count());assertEquals(0,getJson("/api/purchase-receipts").get("totalElements").asInt());}
 @Test void salesIssueDecreasesStock()throws Exception{long p=product(9);var d=postJson("/api/sales-issues",document(false,"SI-1",List.of(line(p,4))),201);assertEquals(5,stock(p));assertEquals("SALES_ISSUE",getJson("/api/inventory/transactions").get("content").get(0).get("referenceType").asText());assertEquals(1,getJson("/api/sales-issues/"+d.get("id").asText()).get("lines").size());}
 @Test void multilineSalesIssue()throws Exception{long a=product(9),b=product(6);postJson("/api/sales-issues",document(false,"SI-1",List.of(line(b,2),line(a,4))),201);assertEquals(5,stock(a));assertEquals(4,stock(b));assertEquals(4,count());}
 @Test void salesInsufficientLineRollsBackEverything()throws Exception{long a=product(9),b=product(1);postJson("/api/sales-issues",document(false,"SI-1",List.of(line(a,4),line(b,2))),400);assertEquals(9,stock(a));assertEquals(1,stock(b));assertEquals(2,count());assertEquals(0,getJson("/api/sales-issues").get("totalElements").asInt());}
 @Test void duplicateReceiptNumberRejected()throws Exception{long p=product(0);postJson("/api/purchase-receipts",document(true,"PR-1",List.of(line(p,1))),201);postJson("/api/purchase-receipts",document(true," pr-1 ",List.of(line(p,1))),409);assertEquals(1,stock(p));assertEquals(1,count());}
 @Test void duplicateIssueNumberRejected()throws Exception{long p=product(4);postJson("/api/sales-issues",document(false,"SI-1",List.of(line(p,1))),201);postJson("/api/sales-issues",document(false,"si-1",List.of(line(p,1))),409);assertEquals(3,stock(p));assertEquals(2,count());}
 @Test void transactionFiltersAndPagination()throws Exception{long a=product(4),b=product(0);var body=new HashMap<>(movement(a,"STOCK_OUT",1));body.put("referenceId","REF-A");postJson("/api/inventory/movements",body,201);postJson("/api/inventory/movements",movement(b,"STOCK_IN",1),201);var page=getJson("/api/inventory/transactions?productId="+a+"&transactionType=STOCK_OUT&referenceType=MANUAL&referenceId=REF-A&dateFrom=2020-01-01T00:00:00Z&dateTo=2100-01-01T00:00:00Z");assertEquals(1,page.get("totalElements").asInt());var first=getJson("/api/inventory/transactions?size=1&page=0");var second=getJson("/api/inventory/transactions?size=1&page=1");assertEquals(3,first.get("totalPages").asInt());assertTrue(first.get("content").get(0).get("id").asLong()>second.get("content").get(0).get("id").asLong());}
 @Test void invalidTypesDatesAndReferencesReturnConsistentErrors()throws Exception{long p=product(0);postJson("/api/inventory/movements",movement(p,"WRONG",1),400);postJson("/api/inventory/movements",movement(999999,"STOCK_IN",1),404);var body=new HashMap<>(movement(p,"STOCK_IN",1));body.put("referenceType","PURCHASE_RECEIPT");postJson("/api/inventory/movements",body,400);for(String query:List.of("dateFrom=bad","dateFrom=2100-01-01T00:00:00Z&dateTo=2000-01-01T00:00:00Z","size=101","page=-1","transactionType=BAD"))mvc.perform(get("/api/inventory/transactions?"+query)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").exists());mvc.perform(get("/api/inventory/transactions/999999")).andExpect(status().isNotFound());}
 @Test void invalidSupplierAndEmptyLinesAreRejected()throws Exception{long p=product(0);var body=document(true,"PR",List.of(line(p,1)));body.put("supplierId",999999);postJson("/api/purchase-receipts",body,404);body.remove("supplierId");postJson("/api/purchase-receipts",body,400);postJson("/api/sales-issues",document(false,"SI",List.of()),400);assertEquals(0,count());}
 @Test void productHistoryAndReceiptSupplierAreProtected()throws Exception{long p=product(0);postJson("/api/purchase-receipts",document(true,"PR",List.of(line(p,1))),201);mvc.perform(delete("/api/products/"+p)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"));mvc.perform(delete("/api/suppliers/"+supplier)).andExpect(status().isConflict());assertEquals(1,count());}
 @Test void productWithoutHistoryCanBeDeleted()throws Exception{long p=product(0);mvc.perform(delete("/api/products/"+p)).andExpect(status().isNoContent());}
 @Test void simultaneousStockOutsCannotOversell()throws Exception{long p=product(5);var gate=new CountDownLatch(1);Callable<Integer> action=()->{gate.await();return mvc.perform(post("/api/inventory/movements").contentType("application/json").content(json.writeValueAsString(movement(p,"STOCK_OUT",4)))).andReturn().getResponse().getStatus();};try(var executor=Executors.newFixedThreadPool(2)){var a=executor.submit(action);var b=executor.submit(action);gate.countDown();var codes=new ArrayList<>(List.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS)));Collections.sort(codes);assertEquals(List.of(201,400),codes);}assertEquals(1,stock(p));assertEquals(2,count());}
 @Test void concurrentReceiptAndIssueRemainConsistent()throws Exception{long a=product(10),b=product(10);var gate=new CountDownLatch(1);try(var executor=Executors.newFixedThreadPool(2)){var receipt=executor.submit(()->{gate.await();return postJson("/api/purchase-receipts",document(true,"PR",List.of(line(a,2),line(b,2))),201);});var issue=executor.submit(()->{gate.await();return postJson("/api/sales-issues",document(false,"SI",List.of(line(b,3),line(a,3))),201);});gate.countDown();receipt.get(10,TimeUnit.SECONDS);issue.get(10,TimeUnit.SECONDS);}assertEquals(9,stock(a));assertEquals(9,stock(b));assertEquals(6,count());}
 @Test void dashboardTotalsIncludeAdjustments()throws Exception{long p=product(5);postJson("/api/inventory/movements",movement(p,"ADJUSTMENT_OUT",4),201);var d=getJson("/api/inventory/dashboard");assertEquals(1,d.get("productCount").asInt());assertEquals(1,d.get("lowStockProductCount").asInt());assertEquals(5,d.get("recentStockIn").asInt());assertEquals(4,d.get("recentStockOut").asInt());}
}
