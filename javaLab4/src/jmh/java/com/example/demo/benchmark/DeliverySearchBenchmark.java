package com.example.demo.benchmark;

import com.example.demo.DemoApplication;
import com.example.demo.search.DeliveryFilter;
import com.example.demo.search.DeliveryView;
import com.example.demo.seed.PopulationService;
import com.example.demo.service.DeliverySearchService;
import org.openjdk.jmh.annotations.*;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(2)
@Threads(1)
public class DeliverySearchBenchmark {
    /** h2 is self-contained; postgres uses DB_URL, DB_USERNAME, DB_PASSWORD and existing data. */
    @Param({"h2"})
    public String database;
    private ConfigurableApplicationContext context;
    private DeliverySearchService service;
    private DeliveryFilter filter;

    @Setup(Level.Trial)
    public void setup() {
        if (!database.equals("h2") && !database.equals("postgres")) {
            throw new IllegalArgumentException("database must be h2 or postgres");
        }
        var builder = new SpringApplicationBuilder(DemoApplication.class)
                .web(WebApplicationType.NONE).profiles("benchmark");
        var args = new java.util.ArrayList<>(List.of(
                "--spring.main.banner-mode=off", "--logging.level.root=ERROR", "--debug=false",
                "--spring.jpa.show-sql=false", "--logging.level.org.hibernate.SQL=OFF",
                "--spring.jpa.properties.hibernate.generate_statistics=false",
                "--spring.sql.init.mode=never"));
        if (database.equals("h2")) {
            args.addAll(List.of("--spring.datasource.url=jdbc:h2:mem:benchmark;MODE=PostgreSQL",
                    "--spring.datasource.driver-class-name=org.h2.Driver", "--spring.datasource.username=sa",
                    "--spring.datasource.password=", "--spring.jpa.hibernate.ddl-auto=create-drop"));
        } else {
            args.add("--spring.jpa.hibernate.ddl-auto=validate");
        }
        context = builder.run(args.toArray(String[]::new));
        if (database.equals("h2")) {
            context.getBean(PopulationService.class).populate(1000, 10000, 42);
        }
        Long count = context.getBean(JdbcTemplate.class).queryForObject("select count(*) from deliveries", Long.class);
        if (count == null || count < 10000) throw new IllegalStateException("Populate at least 10000 deliveries before benchmarking");
        service = context.getBean(DeliverySearchService.class); // Calls go through Spring's cache proxy.
        filter = new DeliveryFilter(null, null, null, null, null, new BigDecimal("100"), null, null);
        var baseline = service.searchUncached(filter, 0, 100);
        if (baseline.isEmpty() || !baseline.equals(service.search(filter, 0, 100))) {
            throw new IllegalStateException("Benchmark requires matching nonempty results");
        }
    }

    @Benchmark
    public List<DeliveryView> uncached() { return service.searchUncached(filter, 0, 100); }

    @Benchmark
    public List<DeliveryView> cached() { return service.search(filter, 0, 100); }

    @TearDown(Level.Trial)
    public void close() { if (context != null) context.close(); }
}
