package com.example.demo.seed;

import lombok.RequiredArgsConstructor;
import net.datafaker.Faker;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PopulationService {
    private final JdbcTemplate jdbc;

    /** Appends a new dataset; never deletes existing records. Each call is atomic. */
    @Transactional
    @CacheEvict(value = "deliverySearches", allEntries = true)
    public PopulationResult populate(int customerCount, int deliveryCount, long randomSeed) {
        if (customerCount < 1 || customerCount > 100_000 || deliveryCount < 1 || deliveryCount > 1_000_000) {
            throw new IllegalArgumentException("Customers must be 1..100000 and deliveries 1..1000000");
        }
        var random = new Random(randomSeed);
        var faker = new Faker(Locale.ENGLISH, random);
        String run = UUID.randomUUID().toString();
        List<Long> depots = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            depots.add(insert("insert into depots(name, latitude, longitude) values (?, ?, ?)",
                    "Depot " + faker.address().city(), latitude(random), longitude(random)));
        }
        for (int i = 0; i < 20; i++) {
            long driver = insert("insert into drivers(name, email, phone) values (?, ?, ?)",
                    faker.name().fullName(), "driver." + run + "." + i + "@example.com", phone(random));
            insert("insert into vehicles(name, capacity, depot_id, available, driver_id) values (?, ?, ?, ?, ?)",
                    "Van " + (i + 1), BigDecimal.valueOf(1000 + random.nextInt(4000)),
                    depots.get(i % depots.size()), true, driver);
        }
        List<Long> customers = new ArrayList<>(customerCount);
        for (int i = 0; i < customerCount; i++) {
            customers.add(insert("insert into customers(name, email, phone, address, latitude, longitude) values (?, ?, ?, ?, ?, ?)",
                    faker.name().fullName(), "customer." + run + "." + i + "@example.com", phone(random),
                    faker.address().fullAddress(), latitude(random), longitude(random)));
        }
        // Bound memory use and JDBC batches; IDENTITY entities otherwise inhibit ORM insert batching.
        OffsetDateTime start = OffsetDateTime.parse("2026-01-01T00:00:00Z");
        List<Object[]> batch = new ArrayList<>(500);
        for (int i = 0; i < deliveryCount; i++) {
            batch.add(new Object[]{customers.get(random.nextInt(customers.size())),
                    BigDecimal.valueOf(1 + random.nextInt(50000), 2), faker.lorem().sentence(),
                    start.plusSeconds(random.nextInt(365 * 24 * 60 * 60))});
            if (batch.size() == 500 || i == deliveryCount - 1) {
                jdbc.batchUpdate("insert into deliveries(customer_id, quantity, notes, timestamp) values (?, ?, ?, ?)", batch);
                batch.clear();
            }
        }
        return new PopulationResult(5, 20, 20, customerCount, deliveryCount);
    }

    private long insert(String sql, Object... values) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, new String[]{"id"});
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            return statement;
        }, keys);
        return java.util.Objects.requireNonNull(keys.getKey()).longValue();
    }

    private static BigDecimal latitude(Random random) {
        return BigDecimal.valueOf(44000000L + random.nextInt(1000000), 6);
    }

    private static BigDecimal longitude(Random random) {
        return BigDecimal.valueOf(25000000L + random.nextInt(2000000), 6);
    }

    private static String phone(Random random) { return "07" + String.format(Locale.ROOT, "%08d", random.nextInt(100000000)); }

    public record PopulationResult(int depots, int drivers, int vehicles, int customers, int deliveries) {}
}
