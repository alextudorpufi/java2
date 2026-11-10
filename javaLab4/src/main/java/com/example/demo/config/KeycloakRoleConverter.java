package com.example.demo.config;

import com.example.demo.entity.Role;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.util.*;
import java.util.stream.Collectors;

public class KeycloakRoleConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private static final Set<String> ALLOWED = Arrays.stream(Role.values()).map(Enum::name).collect(Collectors.toSet());
    private final String audience;

    public KeycloakRoleConverter(String audience) { this.audience = audience; }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Set<String> roles = new HashSet<>();
        addRoles(roles, jwt.getClaim("realm_access"));
        Object resourceAccess = jwt.getClaim("resource_access");
        if (resourceAccess instanceof Map<?, ?> clients) addRoles(roles, clients.get(audience));
        var authorities = roles.stream().filter(ALLOWED::contains).sorted()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
        // The stable IdP subject identifies the caller; do not link accounts by editable username/email.
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }

    private void addRoles(Set<String> result, Object claim) {
        if (claim instanceof Map<?, ?> map && map.get("roles") instanceof Collection<?> roles) {
            roles.stream().filter(String.class::isInstance).map(String.class::cast).forEach(result::add);
        }
    }
}
