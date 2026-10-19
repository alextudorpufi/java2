package com.example.library;

import java.util.Objects;

// One book represents one physical copy. Availability is derived from active loans.
public record Book(long id, String title, Type type) {
    public enum Type { NORMAL, TEXTBOOK, REFERENCE }

    public Book(long id, String title) { this(id, title, Type.NORMAL); }

    public Book {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(type, "type");
    }
}
