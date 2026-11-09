package com.example.demo.exception;

public class ResourceNotFoundException extends IllegalArgumentException {
    public ResourceNotFoundException(String resource, Long id) {
        super(resource + " not found: " + id);
    }
}
