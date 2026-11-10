package com.example.demo.service;

import com.example.demo.entity.Delivery;
import com.example.demo.repository.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.time.OffsetDateTime;
import com.example.demo.dto.DeliveryRequest;
import com.example.demo.dto.DeliveryResponse;
import com.example.demo.repository.CustomerRepository;
import com.example.demo.exception.ResourceNotFoundException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeliveryService {
    private final DeliveryRepository repository;
    private final CustomerRepository customerRepository;

    @Transactional
    @CacheEvict(value = "deliverySearches", allEntries = true)
    public Delivery save(Delivery entity) {
        return repository.save(entity);
    }

    public Delivery findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery", id));
    }

    public List<Delivery> findAll() {
        return repository.findAll();
    }

    public List<DeliveryResponse> listResponses() {
        return findAll().stream().map(DeliveryResponse::from).toList();
    }

    public DeliveryResponse response(Long id) {
        return DeliveryResponse.from(findById(id));
    }

    @Transactional
    @CacheEvict(value = "deliverySearches", allEntries = true)
    @PreAuthorize("hasRole('ADMIN')")
    public DeliveryResponse create(DeliveryRequest request) {
        Delivery delivery = new Delivery();
        apply(delivery, request);
        return DeliveryResponse.from(repository.saveAndFlush(delivery));
    }

    @Transactional
    @CacheEvict(value = "deliverySearches", allEntries = true)
    @PreAuthorize("hasRole('ADMIN')")
    public DeliveryResponse update(Long id, DeliveryRequest request) {
        Delivery delivery = findById(id);
        apply(delivery, request);
        return DeliveryResponse.from(repository.saveAndFlush(delivery));
    }

    private void apply(Delivery delivery, DeliveryRequest request) {
        var customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer", request.customerId()));
        delivery.setCustomer(customer);
        delivery.setQuantity(request.quantity());
        delivery.setNotes(request.notes());
        delivery.setTimestamp(request.timestamp());
    }

    @Transactional
    @CacheEvict(value = "deliverySearches", allEntries = true)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(Long id) {
        repository.delete(findById(id));
        repository.flush();
    }

    public List<Delivery> findForCustomerInPeriod(Long customerId, OffsetDateTime from,
                                                  OffsetDateTime to) {
        if (from == null || to == null || !from.isBefore(to)) {
            throw new IllegalArgumentException("The start must be before the end");
        }
        return repository.findForCustomerInPeriod(customerId, from, to);
    }
}
