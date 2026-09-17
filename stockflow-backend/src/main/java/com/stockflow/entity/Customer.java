package com.stockflow.entity;
import jakarta.persistence.*;
@Entity @Table(name="customers")
public class Customer extends AuditedEntity {
 @Column(nullable=false,length=150) private String name;
 @Column(length=254) private String email;
 @Column(length=30) private String phone;
 @Column(length=500) private String address;
 @Column(nullable=false) private boolean active=true;
 public String getName(){return name;} public void setName(String v){name=v;}
 public String getEmail(){return email;} public void setEmail(String v){email=v;}
 public String getPhone(){return phone;} public void setPhone(String v){phone=v;}
 public String getAddress(){return address;} public void setAddress(String v){address=v;}
 public boolean isActive(){return active;} public void setActive(boolean v){active=v;}
}
