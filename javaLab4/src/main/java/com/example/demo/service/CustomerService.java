package com.example.demo.service;

import com.example.demo.entity.Customer;
import com.example.demo.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerService {
    private final CustomerRepository repository;

    @Transactional
    @CacheEvict(value = "deliverySearches", allEntries = true)
    public Customer save(Customer entity) {
        return repository.save(entity);
    }

    public Customer findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + id));
    }

    public List<Customer> findAll() {
        return repository.findAll();
    }

    public java.util.Optional<Customer> findByEmail(String email) {
        return repository.findByEmail(email);
    }
}
