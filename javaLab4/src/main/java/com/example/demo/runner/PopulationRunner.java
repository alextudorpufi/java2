package com.example.demo.runner;

import com.example.demo.seed.PopulationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("seed & !benchmark")
@RequiredArgsConstructor
public class PopulationRunner implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(PopulationRunner.class);
    private final PopulationService population;
    @Value("${app.seed.customers:1000}") private int customers;
    @Value("${app.seed.deliveries:10000}") private int deliveries;
    @Value("${app.seed.random-seed:42}") private long randomSeed;

    @Override
    public void run(String... args) {
        log.info("Appended DataFaker dataset: {}", population.populate(customers, deliveries, randomSeed));
    }
}
