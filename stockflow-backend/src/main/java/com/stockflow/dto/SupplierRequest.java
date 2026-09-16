package com.stockflow.dto;

import jakarta.validation.constraints.*;

public record SupplierRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 100) String contactPerson,
        @Email @Size(max = 254) String email,
        @Size(max = 30) @Pattern(regexp = "[0-9+(). xX-]*", message = "must contain only phone number characters") String phone,
        @Size(max = 500) String address
) {}

