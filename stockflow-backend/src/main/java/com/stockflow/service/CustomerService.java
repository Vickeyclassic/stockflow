package com.stockflow.service;
import com.stockflow.dto.*;
import com.stockflow.entity.Customer;
import com.stockflow.repository.*;
import com.stockflow.exception.DomainException;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service @Transactional(readOnly=true)
public class CustomerService {
 private final CustomerRepository customers; private final SalesOrderRepository orders;
 public CustomerService(CustomerRepository customers,SalesOrderRepository orders){this.customers=customers;this.orders=orders;}
 private Customer require(Long id){return customers.findById(id).orElseThrow(()->DomainException.notFound("Customer",id));}
 public List<CustomerResponse> list(){return customers.findAll(Sort.by("name","id")).stream().map(CustomerResponse::from).toList();}
 public CustomerResponse get(Long id){return CustomerResponse.from(require(id));}
 @Transactional public CustomerResponse create(CustomerRequest r){return save(new Customer(),r);}
 @Transactional public CustomerResponse update(Long id,CustomerRequest r){return save(require(id),r);}
 private CustomerResponse save(Customer c,CustomerRequest r){
  c.setName(r.name().strip());c.setEmail(DtoMapper.clean(r.email()));c.setPhone(DtoMapper.clean(r.phone()));c.setAddress(DtoMapper.clean(r.address()));c.setActive(r.active()==null||r.active());
  return CustomerResponse.from(customers.saveAndFlush(c));
 }
 @Transactional public void delete(Long id){
  Customer c=require(id);if(orders.existsByCustomerId(id))throw DomainException.conflict("RESOURCE_IN_USE","Customer has sales orders; mark it inactive instead");
  customers.delete(c);customers.flush();
 }
}
