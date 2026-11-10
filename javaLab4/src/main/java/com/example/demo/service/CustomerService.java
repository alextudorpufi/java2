package com.example.demo.service;

import com.example.demo.entity.Customer;
import com.example.demo.repository.CustomerRepository;
import com.example.demo.exception.CustomerNotFoundException;
import com.example.demo.repository.DeliveryRepository;
import com.example.demo.dto.DeliveryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerService {
    private final CustomerRepository repository;
    private final DeliveryRepository deliveryRepository;

    @Transactional
    @CacheEvict(value = "deliverySearches", allEntries = true)
    public Customer save(Customer entity) {
        return repository.save(entity);
    }

    public Customer findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new CustomerNotFoundException(id));
    }

    public List<Customer> findAll() {
        return repository.findAll();
    }

    public List<DeliveryResponse> deliveries(Long customerId) {
        findById(customerId);
        return deliveryRepository.findByCustomerIdOrderByIdAsc(customerId).stream()
                .map(DeliveryResponse::from).toList();
    }

    public java.util.Optional<Customer> findByEmail(String email) {
        return repository.findByEmail(email);
    }

    @Transactional
    @CacheEvict(value = "deliverySearches", allEntries = true)
    @PreAuthorize("hasRole('ADMIN')")
    public Customer update(Long id, Customer changes) {
        Customer customer = findById(id);
        customer.setName(changes.getName());
        customer.setEmail(changes.getEmail());
        customer.setPhone(changes.getPhone());
        customer.setAddress(changes.getAddress());
        customer.setLatitude(changes.getLatitude());
        customer.setLongitude(changes.getLongitude());
        return repository.saveAndFlush(customer);
    }

    @Transactional
    @CacheEvict(value = "deliverySearches", allEntries = true)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(Long id) {
        repository.delete(findById(id));
        repository.flush();
    }
}
