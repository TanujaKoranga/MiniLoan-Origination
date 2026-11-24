package com.miniloan.model;

import java.time.LocalDate;

public record Customer(Long customerId, String name, LocalDate dateOfBirth,
                       String mobile, String address, String kycDetails) {
    public Customer withId(long id) {
        return new Customer(id, name, dateOfBirth, mobile, address, kycDetails);
    }
}
