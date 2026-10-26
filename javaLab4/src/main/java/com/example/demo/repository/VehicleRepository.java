package com.example.demo.repository;

import com.example.demo.entity.Vehicle;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
    // Derived query: Spring parses the property path depot.id and availability.
    List<Vehicle> findByDepotIdAndAvailableTrue(Long depotId);

    // Bulk JPQL bypasses managed entities, so flush first and clear afterwards.
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Vehicle v set v.available = :available where v.id = :id")
    int updateAvailability(@Param("id") Long id, @Param("available") boolean available);
}
