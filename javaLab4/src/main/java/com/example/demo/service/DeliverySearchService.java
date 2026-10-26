package com.example.demo.service;

import com.example.demo.repository.DeliverySearchRepository;
import com.example.demo.search.DeliveryFilter;
import com.example.demo.search.DeliveryView;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DeliverySearchService {
    private final DeliverySearchRepository repository;

    // Default key includes all arguments: filter, page, size.
    @Cacheable("deliverySearches")
    public List<DeliveryView> search(DeliveryFilter filter, int page, int size) {
        validate(filter, page, size);
        return repository.search(filter, page, size);
    }

    // Same query and result shape, but always goes to the database: benchmark baseline.
    public List<DeliveryView> searchUncached(DeliveryFilter filter, int page, int size) {
        validate(filter, page, size);
        return repository.search(filter, page, size);
    }
    private static void validate(DeliveryFilter filter, int page, int size) {
        java.util.Objects.requireNonNull(filter, "Filter is required");
        if (page < 0 || size < 1 || size > 200 || (long) page * size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Page must be >= 0 with a valid offset; size must be 1..200");
        }
    }
}

