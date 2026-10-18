package com.example.library;

import java.util.Objects;

// One book represents one physical copy. Availability is derived from active loans.
public record Book(long id, String title) {
    public Book {
        Objects.requireNonNull(title, "title");
    }
}
