package com.example.demo;

import com.example.demo.entity.Customer;
import com.example.demo.entity.Delivery;
import com.example.demo.search.DeliveryFilter;
import com.example.demo.seed.PopulationService;
import com.example.demo.service.*;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.datasource.url=jdbc:h2:mem:advanced;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.stat=OFF",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"
})
@ActiveProfiles("test")
class AdvancedTests {
    @Autowired CustomerService customers;
    @Autowired DeliveryService deliveries;
    @Autowired DeliverySearchService searches;
    @Autowired PopulationService population;
    @Autowired JdbcTemplate jdbc;
    @Autowired CacheManager caches;
    @Autowired EntityManagerFactory emf;
    @Autowired PlatformTransactionManager transactions;

    @BeforeEach
    void reset() {
        for (String table : new String[]{"deliveries", "vehicles", "drivers", "depots", "customers"}) {
            jdbc.update("delete from " + table);
        }
        caches.getCache("deliverySearches").clear();
        emf.unwrap(SessionFactory.class).getStatistics().clear();
    }

    private Customer customer(String name, String latitude) {
        return customers.save(new Customer(name, name + "@example.com", "0700000000", "Test address",
                new BigDecimal(latitude), new BigDecimal("26")));
    }

    private Delivery delivery(Customer customer, String quantity, String timestamp) {
        var delivery = new Delivery(customer, new BigDecimal(quantity), "Test");
        delivery.setTimestamp(OffsetDateTime.parse(timestamp));
        return deliveries.save(delivery);
    }

    @Test
    void optionalCriteriaCombineAndRespectDstDayBoundaries() {
        var inside = customer("Inside", "44.5");
        var outside = customer("Outside", "47");
        delivery(inside, "20", "2026-10-24T21:00:00Z"); // Bucharest midnight, start of 25-hour day.
        delivery(inside, "30", "2026-10-25T21:59:59Z");
        delivery(inside, "40", "2026-10-25T22:00:00Z"); // Next day, excluded.
        delivery(inside, "10", "2026-10-25T12:00:00Z"); // Equal to threshold, excluded.
        delivery(outside, "20", "2026-10-25T12:00:00Z");
        var filter = new DeliveryFilter(inside.getId(), new BigDecimal("44"), new BigDecimal("45"),
                new BigDecimal("25"), new BigDecimal("27"), BigDecimal.TEN,
                LocalDate.of(2026, 10, 25), ZoneId.of("Europe/Bucharest"));
        assertEquals(2, searches.search(filter, 0, 100).size());
        assertEquals(5, searches.search(DeliveryFilter.all(), 0, 100).size());
        assertEquals(1, searches.search(filter, 0, 1).size());
        assertNotEquals(searches.search(filter, 0, 1).getFirst().id(), searches.search(filter, 1, 1).getFirst().id());
        assertTrue(searches.search(filter, 2, 1).isEmpty());
        var areaOnly = new DeliveryFilter(null, new BigDecimal("44"), new BigDecimal("45"), null, null, null, null, null);
        assertEquals(4, searches.search(areaOnly, 0, 100).size());
        var customerOnly = new DeliveryFilter(outside.getId(), null, null, null, null, null, null, null);
        assertEquals(1, searches.search(customerOnly, 0, 100).size());
        var quantityOnly = new DeliveryFilter(null, null, null, null, null, new BigDecimal("30"), null, null);
        assertEquals(1, searches.search(quantityOnly, 0, 100).size());
    }

    @Test
    void cacheHitAvoidsSqlAndCommittedWritesInvalidateResults() {
        var customer = customer("Cached", "44");
        delivery(customer, "10", "2026-01-01T00:00:00Z");
        var stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        var first = searches.search(DeliveryFilter.all(), 0, 100);
        long afterFirst = stats.getPrepareStatementCount();
        assertTrue(afterFirst > 0);
        assertSame(first, searches.search(DeliveryFilter.all(), 0, 100));
        assertEquals(afterFirst, stats.getPrepareStatementCount());
        assertEquals(first, searches.searchUncached(DeliveryFilter.all(), 0, 100));
        assertTrue(stats.getPrepareStatementCount() > afterFirst);
        assertThrows(UnsupportedOperationException.class, first::clear);

        delivery(customer, "20", "2026-01-02T00:00:00Z");
        assertEquals(2, searches.search(DeliveryFilter.all(), 0, 100).size());
        customer.setName("Renamed");
        customers.save(customer);
        assertEquals("Renamed", searches.search(DeliveryFilter.all(), 0, 100).getFirst().customerName());
    }

    @Test
    void rolledBackWriteDoesNotEvictCommittedCacheEntry() {
        var customer = customer("Rollback", "44");
        var saved = delivery(customer, "10", "2026-01-01T00:00:00Z");
        var before = searches.search(DeliveryFilter.all(), 0, 100);
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactions).execute(status -> {
            var changed = deliveries.findById(saved.getId());
            changed.setQuantity(new BigDecimal("999"));
            deliveries.save(changed);
            throw new IllegalStateException("Deliberate rollback");
        }));
        assertSame(before, searches.search(DeliveryFilter.all(), 0, 100));
        assertEquals(0, new BigDecimal("10").compareTo(deliveries.findById(saved.getId()).getQuantity()));
    }

    @Test
    void datafakerPopulationAppendsValidRelatedRecordsAndEvictsCache() {
        assertTrue(searches.search(DeliveryFilter.all(), 0, 100).isEmpty());
        population.populate(4, 12, 42);
        assertEquals(12, searches.search(DeliveryFilter.all(), 0, 100).size());
        population.populate(2, 3, 42);
        assertEquals(6L, jdbc.queryForObject("select count(*) from customers", Long.class));
        assertEquals(15L, jdbc.queryForObject("select count(*) from deliveries", Long.class));
        assertEquals(40L, jdbc.queryForObject("select count(*) from vehicles where driver_id is not null", Long.class));
        assertEquals(10L, jdbc.queryForObject("select count(*) from depots", Long.class));
        assertEquals(0L, jdbc.queryForObject("select count(*) from deliveries where quantity <= 0", Long.class));
    }

    @Test
    void rejectsInvalidFiltersPaginationAndPopulationSizes() {
        assertThrows(IllegalArgumentException.class, () -> new DeliveryFilter(null,
                new BigDecimal("91"), null, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> searches.search(DeliveryFilter.all(), -1, 100));
        assertThrows(IllegalArgumentException.class, () -> searches.search(DeliveryFilter.all(), 0, 201));
        assertThrows(IllegalArgumentException.class, () -> population.populate(0, 10, 42));
    }
}
