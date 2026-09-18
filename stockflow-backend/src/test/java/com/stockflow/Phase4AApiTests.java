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
class Phase4AApiTests {
 MockMvc mvc;
 @Autowired void configureAuthenticatedMvc(org.springframework.web.context.WebApplicationContext context) {
  // Default request authentication also applies to concurrent worker-thread requests.
  mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
   .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
   .defaultRequest(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/")
    .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("test-admin").roles("ADMIN")))
   .build();
 } @Autowired JsonMapper json; @Autowired JdbcTemplate jdbc;
 long category,customer; int sequence;
 @BeforeEach void setup() throws Exception {
  for(String t:List.of("purchase_order_items","purchase_orders","sales_order_items","sales_orders","customers","inventory_transactions","stock_document_lines","stock_documents","products","categories","suppliers"))jdbc.update("delete from "+t);
  category=postJson("/api/categories",Map.of("name","Orders"),201).get("id").asLong();
  customer=postJson("/api/customers",Map.of("name","Buyer","email","buyer@example.com"),201).get("id").asLong();
 }
 JsonNode postJson(String path,Object body,int expected)throws Exception{return json.readTree(mvc.perform(post(path).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());}
 JsonNode getJson(String path)throws Exception{return json.readTree(mvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
 JsonNode transition(long id,String value,int expected)throws Exception{return json.readTree(mvc.perform(patch("/api/sales-orders/"+id+"/status").contentType("application/json").content(json.writeValueAsString(Map.of("status",value)))).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());}
 long product(int stock)throws Exception{return postJson("/api/products",Map.of("sku","SO-"+(++sequence),"name","Product","categoryId",category,"costPrice","1.25","sellingPrice","2.50","quantityInStock",stock,"reorderLevel",0,"unit","piece"),201).get("id").asLong();}
 Map<String,Object> item(long p,int quantity){return Map.of("productId",p,"quantity",quantity,"unitPrice","2.50");}
 Map<String,Object> order(List<Map<String,Object>> items){return new HashMap<>(Map.of("orderNumber"," so-1 ","customerId",customer,"orderDate","2026-09-17","items",items));}
 long create(List<Map<String,Object>> items)throws Exception{return postJson("/api/sales-orders",order(items),201).get("id").asLong();}
 int stock(long id)throws Exception{return getJson("/api/products/"+id).get("quantityInStock").asInt();}
 long history(){return jdbc.queryForObject("select count(*) from inventory_transactions",Long.class);}
 @Test void createConfirmFulfillCalculatesTotalsAndReferencesOrder()throws Exception{
  long a=product(10),b=product(5);
  var body=order(List.of(item(a,2),item(b,3)));body.put("totalAmount",1);
  postJson("/api/sales-orders",body,400);body.remove("totalAmount");
  var o=postJson("/api/sales-orders",body,201);long id=o.get("id").asLong();
  assertEquals("SO-1",o.get("orderNumber").asText());assertEquals(12.5,o.get("totalAmount").asDouble());assertEquals(5,o.get("items").get(0).get("lineTotal").asDouble());
  assertEquals(10,stock(a));transition(id,"CONFIRMED",200);assertEquals(10,stock(a));assertEquals(2,history());
  transition(id,"FULFILLED",200);assertEquals(8,stock(a));assertEquals(2,stock(b));
  var h=getJson("/api/inventory/transactions?referenceType=SALES_ORDER&referenceId="+id);assertEquals(2,h.get("totalElements").asInt());
  transition(id,"FULFILLED",409);transition(id,"CANCELLED",409);assertEquals(4,history());
 }
 @Test void laterInsufficientItemRollsBackStockHistoryAndStatus()throws Exception{
  long a=product(10),b=product(1),id=create(List.of(item(a,4),item(b,2)));transition(id,"CONFIRMED",200);
  transition(id,"FULFILLED",400);assertEquals(10,stock(a));assertEquals(1,stock(b));assertEquals(2,history());
  assertEquals("CONFIRMED",getJson("/api/sales-orders/"+id).get("status").asText());
 }
 @Test void duplicateProductLinesCannotOversell()throws Exception{
  long p=product(5),id=create(List.of(item(p,3),item(p,3)));transition(id,"CONFIRMED",200);transition(id,"FULFILLED",400);assertEquals(5,stock(p));assertEquals(1,history());
 }
 @Test void cancellationAndInvalidTransitionsNeverMoveStock()throws Exception{
  long p=product(5),id=create(List.of(item(p,2)));transition(id,"FULFILLED",409);transition(id,"CANCELLED",200);transition(id,"CONFIRMED",409);transition(id,"FULFILLED",409);assertEquals(5,stock(p));assertEquals(1,history());
 }
 @Test void draftsCanBeEditedButConfirmedOrdersCannot()throws Exception{
  long p=product(5),id=create(List.of(item(p,1)));var body=order(List.of(item(p,3)));
  mvc.perform(put("/api/sales-orders/"+id).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isOk()).andExpect(jsonPath("$.totalAmount").value(7.5));
  transition(id,"CONFIRMED",200);
  mvc.perform(put("/api/sales-orders/"+id).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isConflict());
  assertEquals(5,stock(p));
 }
 @Test void validationAndInactiveProducts()throws Exception{
  long p=product(5);
  postJson("/api/sales-orders",order(List.of()),400);
  postJson("/api/sales-orders",order(List.of(item(p,0))),400);
  postJson("/api/sales-orders",order(List.of(item(p,-1))),400);
  postJson("/api/sales-orders",order(List.of(item(999999,1))),404);
  postJson("/api/sales-orders",order(List.of(Map.of("productId",p,"quantity",1,"unitPrice","1.234"))),400);
  jdbc.update("update products set active=false where id=?",p);
  postJson("/api/sales-orders",order(List.of(item(p,1))),400);
 }
 @Test void productDeactivatedAfterConfirmationPreventsFulfillment()throws Exception{
  long p=product(5),id=create(List.of(item(p,1)));transition(id,"CONFIRMED",200);jdbc.update("update products set active=false where id=?",p);
  transition(id,"FULFILLED",400);assertEquals(5,stock(p));assertEquals(1,history());
 }
 @Test void filtersAndDuplicateNumbers()throws Exception{
  long p=product(5);create(List.of(item(p,1)));postJson("/api/sales-orders",order(List.of(item(p,1))),409);
  assertEquals(1,getJson("/api/sales-orders?orderNumber=so-&customerId="+customer+"&status=DRAFT&dateFrom=2026-09-17&dateTo=2026-09-17").get("totalElements").asInt());
  assertEquals(0,getJson("/api/sales-orders?status=FULFILLED").get("totalElements").asInt());
  assertEquals(0,getJson("/api/sales-orders?dateFrom=2026-09-18").get("totalElements").asInt());
  for(String q:List.of("dateFrom=2026-09-18&dateTo=2026-09-17","size=101","status=BAD"))mvc.perform(get("/api/sales-orders?"+q)).andExpect(status().isBadRequest());
 }
 @Test void customerCrudValidationAndReferences()throws Exception{
  postJson("/api/customers",Map.of("name"," ","email","invalid"),400);
  mvc.perform(put("/api/customers/"+customer).contentType("application/json").content("{\"name\":\"Updated\",\"active\":false}")).andExpect(status().isOk());
  assertFalse(getJson("/api/customers/"+customer).get("active").asBoolean());
  long p=product(0);postJson("/api/sales-orders",order(List.of(item(p,1))),400);
  mvc.perform(put("/api/customers/"+customer).contentType("application/json").content("{\"name\":\"Updated\",\"active\":true}")).andExpect(status().isOk());
  create(List.of(item(p,1)));
  mvc.perform(delete("/api/customers/"+customer)).andExpect(status().isConflict());
  mvc.perform(delete("/api/products/"+p)).andExpect(status().isConflict());
  long other=postJson("/api/customers",Map.of("name","Unused"),201).get("id").asLong();
  mvc.perform(delete("/api/customers/"+other)).andExpect(status().isNoContent());
  mvc.perform(get("/api/customers/"+other)).andExpect(status().isNotFound());
 }
 @Test void concurrentFulfillmentDeductsOnlyOnce()throws Exception{
  long p=product(10),id=create(List.of(item(p,3)));transition(id,"CONFIRMED",200);
  var gate=new CountDownLatch(1);
  Callable<Integer> action=()->{gate.await();return mvc.perform(patch("/api/sales-orders/"+id+"/status").contentType("application/json").content("{\"status\":\"FULFILLED\"}")).andReturn().getResponse().getStatus();};
  try(var executor=Executors.newFixedThreadPool(2)){
   var a=executor.submit(action);var b=executor.submit(action);gate.countDown();
   var codes=new ArrayList<>(List.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS)));Collections.sort(codes);assertEquals(List.of(200,409),codes);
  }
  assertEquals(7,stock(p));assertEquals(2,history());
 }
}
