package com.example.demo.repository;

import com.example.demo.entity.Customer;
import com.example.demo.entity.Delivery;
import com.example.demo.search.DeliveryFilter;
import com.example.demo.search.DeliveryView;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Repository
@RequiredArgsConstructor
public class DeliverySearchRepository {
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<DeliveryView> search(DeliveryFilter filter, int page, int size) {
        Objects.requireNonNull(filter, "Filter is required");
        if (page < 0 || size < 1 || size > 200) {
            throw new IllegalArgumentException("Page must be >= 0; size must be 1..200");
        }
        int offset = Math.multiplyExact(page, size);
        var cb = entityManager.getCriteriaBuilder();
        var query = cb.createQuery(DeliveryView.class);
        var delivery = query.from(Delivery.class);
        Join<Delivery, Customer> customer = delivery.join("customer");
        List<Predicate> predicates = new ArrayList<>();

        if (filter.customerId() != null) predicates.add(cb.equal(customer.get("id"), filter.customerId()));
        if (filter.minLatitude() != null) predicates.add(cb.ge(customer.get("latitude"), filter.minLatitude()));
        if (filter.maxLatitude() != null) predicates.add(cb.le(customer.get("latitude"), filter.maxLatitude()));
        if (filter.minLongitude() != null) predicates.add(cb.ge(customer.get("longitude"), filter.minLongitude()));
        if (filter.maxLongitude() != null) predicates.add(cb.le(customer.get("longitude"), filter.maxLongitude()));
        if (filter.quantityAbove() != null) predicates.add(cb.gt(delivery.get("quantity"), filter.quantityAbove()));
        if (filter.date() != null) {
            // Calendar-day boundaries in the requested timezone, including DST changes.
            var start = filter.date().atStartOfDay(filter.zone()).toOffsetDateTime();
            var end = filter.date().plusDays(1).atStartOfDay(filter.zone()).toOffsetDateTime();
            predicates.add(cb.greaterThanOrEqualTo(delivery.get("timestamp"), start));
            predicates.add(cb.lessThan(delivery.get("timestamp"), end));
        }

        query.select(cb.construct(DeliveryView.class, delivery.get("id"), customer.get("id"),
                customer.get("name"), delivery.get("quantity"), delivery.get("timestamp")));
        query.where(predicates.toArray(Predicate[]::new));
        query.orderBy(cb.asc(delivery.get("id")));
        return List.copyOf(entityManager.createQuery(query).setFirstResult(offset).setMaxResults(size).getResultList());
    }
}
