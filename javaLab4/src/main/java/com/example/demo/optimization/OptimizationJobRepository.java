package com.example.demo.optimization;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface OptimizationJobRepository extends JpaRepository<OptimizationJob, UUID> {
    List<OptimizationJob> findTop20ByPublishedFalseOrderByIdAsc();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from OptimizationJob j where j.id = :id")
    Optional<OptimizationJob> findLocked(@Param("id") UUID id);
}
