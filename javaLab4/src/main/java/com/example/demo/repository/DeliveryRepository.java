package com.example.demo.repository;

import com.example.demo.entity.Delivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;
import java.util.List;

public interface DeliveryRepository extends JpaRepository<Delivery, Long> {
    // JPQL refers to Java entities/properties, not SQL tables/columns.
    // The interval includes from but excludes to, so adjacent periods do not overlap.
    @Query("""
            select d from Delivery d
            where d.customer.id = :customerId
              and d.timestamp >= :from and d.timestamp < :to
            order by d.timestamp
            """)
    List<Delivery> findForCustomerInPeriod(@Param("customerId") Long customerId,
                                         @Param("from") OffsetDateTime from,
                                         @Param("to") OffsetDateTime to);
}
