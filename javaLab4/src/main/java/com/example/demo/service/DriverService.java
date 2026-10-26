package com.example.demo.service;

import com.example.demo.entity.Driver;
import com.example.demo.repository.DriverRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DriverService {
    private final DriverRepository repository;

    @Transactional
    public Driver save(Driver entity) {
        return repository.save(entity);
    }

    public Driver findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Driver not found: " + id));
    }

    public List<Driver> findAll() {
        return repository.findAll();
    }
}
