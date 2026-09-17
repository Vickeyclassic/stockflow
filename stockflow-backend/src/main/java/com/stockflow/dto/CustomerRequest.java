package com.stockflow.dto;
import jakarta.validation.constraints.*;
public record CustomerRequest(@NotBlank @Size(max=150) String name,@Email @Size(max=254) String email,
 @Size(max=30) String phone,@Size(max=500) String address,Boolean active){}
