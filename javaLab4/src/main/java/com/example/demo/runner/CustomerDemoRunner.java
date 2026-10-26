package com.example.demo.runner;

import com.example.demo.entity.Customer;
import com.example.demo.service.CustomerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

import java.math.BigDecimal;

@Component
@Profile("!seed & !benchmark")
public class CustomerDemoRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CustomerDemoRunner.class);
    private final CustomerService customerService;

    public CustomerDemoRunner(CustomerService customerService) {
        this.customerService = customerService;
    }

    @Override
    public void run(String... args) {
        // Reuse the demo record on subsequent starts to avoid duplicate emails.
        Customer saved = customerService.findByEmail("customer.demo@example.com")
                .orElseGet(() -> customerService.save(new Customer(
                        "Demo Customer",
                        "customer.demo@example.com",
                        "+40700000000",
                        "Demo address, Bucharest",
                        new BigDecimal("44.426800"),
                        new BigDecimal("26.102500")
                )));

        Customer retrieved = customerService.findById(saved.getId());

        if (!saved.getEmail().equals(retrieved.getEmail())) {
            throw new IllegalStateException("Retrieved customer email does not match");
        }

        log.info("Customer repository test passed: id={}, name={}, email={}",
                retrieved.getId(), retrieved.getName(), retrieved.getEmail());
    }
}
