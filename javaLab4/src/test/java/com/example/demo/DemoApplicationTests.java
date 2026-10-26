package com.example.demo;

import com.example.demo.entity.*;
import com.example.demo.service.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class DemoApplicationTests {
    @Autowired DepotService depots;
    @Autowired CustomerService customers;
    @Autowired DriverService drivers;
    @Autowired VehicleService vehicles;
    @Autowired DeliveryService deliveries;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;

    private Depot newDepot() {
        return depots.save(new Depot("Test depot", new BigDecimal("44.426800"),
                new BigDecimal("26.102500")));
    }

    private Customer newCustomer() {
        return customers.save(new Customer("Test Customer", "test@example.com", "0700000000",
                "Test address", new BigDecimal("44.426800"), new BigDecimal("26.102500")));
    }

    @Test
    void associationsSurviveReload() {
        Depot depot = newDepot();
        Customer customer = newCustomer();
        Driver driver = drivers.save(new Driver("Test Driver", "driver@example.com", "0711111111"));
        Vehicle vehicle = vehicles.save(new Vehicle("Van", new BigDecimal("100"), depot));
        Vehicle second = vehicles.save(new Vehicle("Second van", new BigDecimal("50"), depot));
        vehicles.assignDriver(vehicle.getId(), driver.getId());
        Delivery delivery = deliveries.save(new Delivery(customer, new BigDecimal("10"), "Fragile"));
        entityManager.flush();
        entityManager.clear();

        assertNotNull(delivery.getId());
        assertEquals(2, depots.findById(depot.getId()).getVehicles().size());
        assertEquals(driver.getId(), vehicles.findById(vehicle.getId()).getDriver().getId());
        assertEquals(vehicle.getId(), drivers.findById(driver.getId()).getVehicle().getId());
        assertEquals(customer.getId(), deliveries.findById(delivery.getId()).getCustomer().getId());
        assertEquals(1, customers.findById(customer.getId()).getDeliveries().size());
        assertNull(vehicles.findById(second.getId()).getDriver());
    }

    @Test
    void derivedQueryAndBulkUpdateFilterAvailableVehicles() {
        Depot depot = newDepot();
        Vehicle vehicle = vehicles.save(new Vehicle("Van", new BigDecimal("100"), depot));
        vehicles.save(new Vehicle("Other depot van", new BigDecimal("100"), newDepot()));
        assertEquals(1, vehicles.findAvailableByDepot(depot.getId()).size());

        vehicles.changeAvailability(vehicle.getId(), false);

        assertFalse(entityManager.contains(vehicle), "Bulk update must clear stale managed entities");
        assertFalse(vehicles.findById(vehicle.getId()).isAvailable());
        assertTrue(vehicles.findAvailableByDepot(depot.getId()).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> vehicles.changeAvailability(-1L, true));
    }

    @Test
    void jpqlQueryUsesCustomerAndHalfOpenTimeInterval() {
        Customer customer = newCustomer();
        OffsetDateTime from = OffsetDateTime.parse("2026-10-01T00:00:00Z");
        OffsetDateTime to = from.plusDays(1);
        Delivery included = new Delivery(customer, BigDecimal.ONE, "Included");
        included.setTimestamp(from);
        deliveries.save(included);
        Delivery excluded = new Delivery(customer, BigDecimal.ONE, "At exclusive end");
        excluded.setTimestamp(to);
        deliveries.save(excluded);
        Customer other = customers.save(new Customer("Other", "other@example.com", "0722222222",
                "Other address", BigDecimal.ZERO, BigDecimal.ZERO));
        Delivery otherCustomer = new Delivery(other, BigDecimal.ONE, "Other customer");
        otherCustomer.setTimestamp(from);
        deliveries.save(otherCustomer);

        var result = deliveries.findForCustomerInPeriod(customer.getId(), from, to);
        assertEquals(1, result.size());
        assertEquals(included.getId(), result.getFirst().getId());
        assertThrows(IllegalArgumentException.class,
                () -> deliveries.findForCustomerInPeriod(customer.getId(), to, from));
    }

    @Test
    void driverAssignmentRejectsConflicts() {
        Depot depot = newDepot();
        Vehicle first = vehicles.save(new Vehicle("First", BigDecimal.TEN, depot));
        Vehicle second = vehicles.save(new Vehicle("Second", BigDecimal.TEN, depot));
        Driver driver = drivers.save(new Driver("Driver", "driver@example.com", "0711111111"));
        Driver other = drivers.save(new Driver("Other", "other.driver@example.com", "0722222222"));
        vehicles.assignDriver(first.getId(), driver.getId());
        // Exercise the entity guard directly: a rejected service transaction would be rollback-only.
        assertThrows(IllegalStateException.class, () -> second.setDriver(driver));
        assertThrows(IllegalStateException.class, () -> vehicles.assignDriver(first.getId(), other.getId()));
    }

    @Test
    void driverCanMoveAfterUnassignment() {
        Depot depot = newDepot();
        Vehicle first = vehicles.save(new Vehicle("First", BigDecimal.TEN, depot));
        Vehicle second = vehicles.save(new Vehicle("Second", BigDecimal.TEN, depot));
        Driver driver = drivers.save(new Driver("Driver", "driver@example.com", "0711111111"));
        vehicles.assignDriver(first.getId(), driver.getId());
        vehicles.unassignDriver(first.getId());
        entityManager.flush();
        vehicles.assignDriver(second.getId(), driver.getId());
        entityManager.flush();
        entityManager.clear();
        assertNull(vehicles.findById(first.getId()).getDriver());
        assertEquals(second.getId(), drivers.findById(driver.getId()).getVehicle().getId());
    }

    @Test
    void databaseRejectsSharingDriverEvenWhenJavaGuardIsBypassed() {
        Depot depot = newDepot();
        Driver driver = drivers.save(new Driver("Driver", "driver@example.com", "0711111111"));
        Vehicle first = vehicles.save(new Vehicle("First", BigDecimal.TEN, depot));
        Vehicle second = vehicles.save(new Vehicle("Second", BigDecimal.TEN, depot));
        vehicles.assignDriver(first.getId(), driver.getId());
        entityManager.flush();
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "update vehicles set driver_id = ? where id = ?", driver.getId(), second.getId()));
    }
}
