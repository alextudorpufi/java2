package com.example.demo;

import com.example.demo.entity.Depot;
import com.example.demo.repository.DepotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Optional fixture available only on the test classpath, after Flyway has migrated. */
@Component
@Profile("api-demo")
@RequiredArgsConstructor
public class ApiDemoRunner implements CommandLineRunner {
    private final DepotRepository depots;

    @Override
    public void run(String... args) {
        if (depots.count() == 0) {
            depots.save(new Depot("API demo depot", new BigDecimal("44.426800"), new BigDecimal("26.102500")));
        }
    }
}
