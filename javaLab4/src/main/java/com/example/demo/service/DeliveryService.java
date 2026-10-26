package com.example.demo.service;

import com.example.demo.entity.Delivery;
import com.example.demo.repository.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeliveryService {
    private final DeliveryRepository repository;

    @Transactional
    @CacheEvict(value = "deliverySearches", allEntries = true)
    public Delivery save(Delivery entity) {
        return repository.save(entity);
    }

    public Delivery findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Delivery not found: " + id));
    }

    public List<Delivery> findAll() {
        return repository.findAll();
    }

    public List<Delivery> findForCustomerInPeriod(Long customerId, OffsetDateTime from,
                                                  OffsetDateTime to) {
        if (from == null || to == null || !from.isBefore(to)) {
            throw new IllegalArgumentException("The start must be before the end");
        }
        return repository.findForCustomerInPeriod(customerId, from, to);
    }
}
