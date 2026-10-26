package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Shares columns with subclasses; this does not create a persons table.
@MappedSuperclass
@Getter
@NoArgsConstructor
public abstract class Person {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(nullable = false, length = 100)
    private String name;

    @Setter
    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Setter
    @Column(nullable = false, length = 30)
    private String phone;

    protected Person(String name, String email, String phone) {
        this.name = name;
        this.email = email;
        this.phone = phone;
    }
}
