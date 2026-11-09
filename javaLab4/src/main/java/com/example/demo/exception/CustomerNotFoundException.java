package com.example.demo.exception;

public class CustomerNotFoundException extends ResourceNotFoundException {
    public CustomerNotFoundException(Long id) {
        super("Customer", id);
    }
}
