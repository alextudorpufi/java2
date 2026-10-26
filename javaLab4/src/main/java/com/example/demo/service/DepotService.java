package com.example.demo.service;

import com.example.demo.entity.Depot;
import com.example.demo.repository.DepotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DepotService {
    private final DepotRepository repository;

    @Transactional
    public Depot save(Depot entity) {
        return repository.save(entity);
    }

    public Depot findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Depot not found: " + id));
    }

    public List<Depot> findAll() {
        return repository.findAll();
    }
}
