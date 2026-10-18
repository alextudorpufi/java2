package com.example.library;

import java.util.Objects;

public record Member(long id, String name, Role role) {
    public enum Role { STUDENT, REGULAR, PREMIUM }

    public Member {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(role, "role");
    }
}
