package com.example.demo.dto;

import com.example.demo.entity.AppUser;
import com.example.demo.entity.Role;
import java.util.Set;

public record UserResponse(Long id, String username, Set<Role> roles) {
    public static UserResponse from(AppUser user) {
        return new UserResponse(user.getId(), user.getUsername(), Set.copyOf(user.getRoles()));
    }
}
