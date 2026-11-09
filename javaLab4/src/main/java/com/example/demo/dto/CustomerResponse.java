package com.example.demo.dto;

import com.example.demo.entity.Customer;

import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonProperty;

public record CustomerResponse(Long id, String name, String email, String phone,
                               String address, BigDecimal latitude, BigDecimal longitude,
                               @JsonProperty("_links") CustomerLinks links) {
    public static CustomerResponse from(Customer customer, String collectionUrl) {
        String self = collectionUrl + "/" + customer.getId();
        return new CustomerResponse(customer.getId(), customer.getName(), customer.getEmail(),
                customer.getPhone(), customer.getAddress(), customer.getLatitude(), customer.getLongitude(),
                new CustomerLinks(new Link(self), new Link(collectionUrl), new Link(self + "/deliveries")));
    }

    public record CustomerLinks(Link self, Link collection, Link deliveries) {}
    public record Link(String href) {}
}
