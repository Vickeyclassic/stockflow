package com.stockflow;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
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
class Phase4BApiTests {
 @Autowired MockMvc mvc; @Autowired JsonMapper json; @Autowired JdbcTemplate jdbc;
 long category,supplier; int sequence;
 @BeforeEach void setup() throws Exception {
  for(String t:List.of("purchase_order_items","purchase_orders","sales_order_items","sales_orders","customers","inventory_transactions","stock_document_lines","stock_documents","products","categories","suppliers"))jdbc.update("delete from "+t);
  category=postJson("/api/categories",Map.of("name","Orders"),201).get("id").asLong();
  supplier=postJson("/api/suppliers",Map.of("name","Buyer","email","buyer@example.com"),201).get("id").asLong();
 }
 JsonNode postJson(String path,Object body,int expected)throws Exception{return json.readTree(mvc.perform(post(path).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());}
 JsonNode getJson(String path)throws Exception{return json.readTree(mvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
 JsonNode transition(long id,String value,int expected)throws Exception{return json.readTree(mvc.perform(patch("/api/purchase-orders/"+id+"/status").contentType("application/json").content(json.writeValueAsString(Map.of("status",value)))).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());}
 long product(int stock)throws Exception{return postJson("/api/products",Map.of("sku","PO-"+(++sequence),"name","Product","categoryId",category,"costPrice","1.25","sellingPrice","2.50","quantityInStock",stock,"reorderLevel",0,"unit","piece"),201).get("id").asLong();}
 Map<String,Object> item(long p,int quantity){return Map.of("productId",p,"quantity",quantity,"unitPrice","2.50");}
 Map<String,Object> order(List<Map<String,Object>> items){return new HashMap<>(Map.of("orderNumber"," po-1 ","supplierId",supplier,"orderDate","2026-09-17","items",items));}
 long create(List<Map<String,Object>> items)throws Exception{return postJson("/api/purchase-orders",order(items),201).get("id").asLong();}
 int stock(long id)throws Exception{return getJson("/api/products/"+id).get("quantityInStock").asInt();}
 long history(){return jdbc.queryForObject("select count(*) from inventory_transactions",Long.class);}
 @Test void createOrderReceiveCalculatesTotalsAndReferencesOrder()throws Exception{
  long a=product(10),b=product(5);
  var body=order(List.of(item(a,2),item(b,3)));body.put("totalAmount",1);
  postJson("/api/purchase-orders",body,400);body.remove("totalAmount");
  var o=postJson("/api/purchase-orders",body,201);long id=o.get("id").asLong();
  assertEquals("PO-1",o.get("orderNumber").asText());assertEquals(12.5,o.get("totalAmount").asDouble());assertEquals(5,o.get("items").get(0).get("lineTotal").asDouble());
  assertEquals(10,stock(a));transition(id,"ORDERED",200);assertEquals(10,stock(a));assertEquals(2,history());
  transition(id,"RECEIVED",200);assertEquals(12,stock(a));assertEquals(8,stock(b));
  var h=getJson("/api/inventory/transactions?referenceType=PURCHASE_ORDER&referenceId="+id);assertEquals(2,h.get("totalElements").asInt());
  transition(id,"RECEIVED",409);transition(id,"CANCELLED",409);assertEquals(4,history());
 }
 @Test void laterOverflowingItemRollsBackStockHistoryAndStatus()throws Exception{
  long a=product(10),b=product(Integer.MAX_VALUE),id=create(List.of(item(a,4),item(b,2)));transition(id,"ORDERED",200);
  transition(id,"RECEIVED",400);assertEquals(10,stock(a));assertEquals(Integer.MAX_VALUE,stock(b));assertEquals(2,history());
  assertEquals("ORDERED",getJson("/api/purchase-orders/"+id).get("status").asText());
 }
 @Test void duplicateProductLinesCannotOverflow()throws Exception{
  long p=product(Integer.MAX_VALUE-5),id=create(List.of(item(p,3),item(p,3)));transition(id,"ORDERED",200);transition(id,"RECEIVED",400);assertEquals(Integer.MAX_VALUE-5,stock(p));assertEquals(1,history());
 }
 @Test void cancellationAndInvalidTransitionsNeverMoveStock()throws Exception{
  long p=product(5),id=create(List.of(item(p,2)));transition(id,"RECEIVED",409);transition(id,"CANCELLED",200);transition(id,"ORDERED",409);transition(id,"RECEIVED",409);assertEquals(5,stock(p));assertEquals(1,history());
 }
 @Test void draftsCanBeEditedButOrderedOrdersCannot()throws Exception{
  long p=product(5),id=create(List.of(item(p,1)));var body=order(List.of(item(p,3)));
  mvc.perform(put("/api/purchase-orders/"+id).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isOk()).andExpect(jsonPath("$.totalAmount").value(7.5));
  transition(id,"ORDERED",200);
  mvc.perform(put("/api/purchase-orders/"+id).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isConflict());
  assertEquals(5,stock(p));
 }
 @Test void validationAndInactiveProducts()throws Exception{
  long p=product(5);
  postJson("/api/purchase-orders",order(List.of()),400);
  postJson("/api/purchase-orders",order(List.of(item(p,0))),400);
  postJson("/api/purchase-orders",order(List.of(item(p,-1))),400);
  postJson("/api/purchase-orders",order(List.of(item(999999,1))),404);
  postJson("/api/purchase-orders",order(List.of(Map.of("productId",p,"quantity",1,"unitPrice","1.234"))),400);
  jdbc.update("update products set active=false where id=?",p);
  postJson("/api/purchase-orders",order(List.of(item(p,1))),400);
 }
 @Test void productDeactivatedAfterOrderingPreventsReceipt()throws Exception{
  long p=product(5),id=create(List.of(item(p,1)));transition(id,"ORDERED",200);jdbc.update("update products set active=false where id=?",p);
  transition(id,"RECEIVED",400);assertEquals(5,stock(p));assertEquals(1,history());
 }
 @Test void filtersAndDuplicateNumbers()throws Exception{
  long p=product(5);create(List.of(item(p,1)));postJson("/api/purchase-orders",order(List.of(item(p,1))),409);
  assertEquals(1,getJson("/api/purchase-orders?orderNumber=po-&supplierId="+supplier+"&status=DRAFT&dateFrom=2026-09-17&dateTo=2026-09-17").get("totalElements").asInt());
  assertEquals(0,getJson("/api/purchase-orders?status=RECEIVED").get("totalElements").asInt());
  assertEquals(0,getJson("/api/purchase-orders?dateFrom=2026-09-18").get("totalElements").asInt());
  for(String q:List.of("dateFrom=2026-09-18&dateTo=2026-09-17","size=101","status=BAD"))mvc.perform(get("/api/purchase-orders?"+q)).andExpect(status().isBadRequest());
 }
 @Test void supplierAndProductReferencesAreProtected()throws Exception{
  long p=product(0);var body=order(List.of(item(p,1)));body.put("supplierId",999999);postJson("/api/purchase-orders",body,404);
  create(List.of(item(p,1)));
  mvc.perform(delete("/api/suppliers/"+supplier)).andExpect(status().isConflict());
  mvc.perform(delete("/api/products/"+p)).andExpect(status().isConflict());
 }
 @Test void orderedCancellationDoesNotMoveStock()throws Exception{
  long p=product(5),id=create(List.of(item(p,2)));transition(id,"ORDERED",200);transition(id,"CANCELLED",200);transition(id,"RECEIVED",409);assertEquals(5,stock(p));assertEquals(1,history());
 }
 @Test void invalidLaterLineCannotPartiallyCreateOrEdit()throws Exception{
  long p=product(5);var invalid=order(List.of(item(p,1),item(999999,2)));
  postJson("/api/purchase-orders",invalid,404);
  assertEquals(0,getJson("/api/purchase-orders").get("totalElements").asInt());
  long id=create(List.of(item(p,3)));
  mvc.perform(put("/api/purchase-orders/"+id).contentType("application/json").content(json.writeValueAsString(invalid))).andExpect(status().isNotFound());
  assertEquals(7.5,getJson("/api/purchase-orders/"+id).get("totalAmount").asDouble());assertEquals(5,stock(p));assertEquals(1,history());
 }
 @Test void concurrentReceiptAddsOnlyOnce()throws Exception{
  long p=product(10),id=create(List.of(item(p,3)));transition(id,"ORDERED",200);
  var gate=new CountDownLatch(1);
  Callable<Integer> action=()->{gate.await();return mvc.perform(patch("/api/purchase-orders/"+id+"/status").contentType("application/json").content("{\"status\":\"RECEIVED\"}")).andReturn().getResponse().getStatus();};
  try(var executor=Executors.newFixedThreadPool(2)){
   var a=executor.submit(action);var b=executor.submit(action);gate.countDown();
   var codes=new ArrayList<>(List.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS)));Collections.sort(codes);assertEquals(List.of(200,409),codes);
  }
  assertEquals(13,stock(p));assertEquals(2,history());
 }
}

