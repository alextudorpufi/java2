package com.example.demo.service;

import com.example.demo.entity.AppUser;
import com.example.demo.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;

@Service
@Profile("!keycloak")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class RefreshTokenService {
    private final JdbcTemplate jdbc;
    private final AppUserRepository users;
    private final TokenService tokens;
    private final TransactionTemplate transaction;
    private final long lifetime;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(JdbcTemplate jdbc, AppUserRepository users, TokenService tokens,
                               PlatformTransactionManager manager,
                               @Value("${app.jwt.refresh-ttl-seconds:604800}") long lifetime) {
        if (lifetime < 60 || lifetime > 2592000) throw new IllegalArgumentException("Refresh TTL must be 60..2592000 seconds");
        this.jdbc = jdbc;
        this.users = users;
        this.tokens = tokens;
        this.transaction = new TransactionTemplate(manager);
        this.lifetime = lifetime;
    }

    public TokenPair issue(Authentication authentication) {
        return transaction.execute(status -> {
            AppUser user = users.findByUsername(authentication.getName()).filter(AppUser::isEnabled)
                    .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
            String session = UUID.randomUUID().toString();
            Instant expiry = Instant.now().plusSeconds(lifetime);
            jdbc.update("insert into refresh_sessions(id, user_id, expires_at, revoked) values (?, ?, ?, false)",
                    session, user.getId(), OffsetDateTime.ofInstant(expiry, ZoneOffset.UTC));
            return pair(user, session, expiry);
        });
    }

    public TokenPair refresh(String rawToken) {
        String hash = hash(rawToken);
        TokenPair result = transaction.execute(status -> {
            Session session = lockSession(hash);
            if (session == null || session.revoked()) return null;
            boolean consumed = Boolean.TRUE.equals(jdbc.queryForObject(
                    "select consumed from refresh_tokens where token_hash = ?", Boolean.class, hash));
            var user = users.findById(session.userId()).filter(AppUser::isEnabled);
            if (consumed || !session.expiresAt().isAfter(Instant.now()) || user.isEmpty()) {
                // Commit revocation even though the request will return 401 (outside this transaction).
                revoke(session.id());
                return null;
            }
            jdbc.update("update refresh_tokens set consumed = true where token_hash = ?", hash);
            return pair(user.get(), session.id(), session.expiresAt());
        });
        if (result == null) throw new BadCredentialsException("Invalid refresh token");
        return result;
    }

    public void logout(String rawToken) {
        transaction.executeWithoutResult(status -> {
            Session session = lockSession(hash(rawToken));
            if (session != null) revoke(session.id());
        });
    }

    private Session lockSession(String hash) {
        var ids = jdbc.queryForList("select session_id from refresh_tokens where token_hash = ?", String.class, hash);
        if (ids.isEmpty()) return null;
        // Every rotation/logout/replay for a login locks the same row, including concurrent requests.
        var sessions = jdbc.query("select id, user_id, expires_at, revoked from refresh_sessions where id = ? for update",
                (row, number) -> new Session(row.getString("id"), row.getLong("user_id"),
                        row.getObject("expires_at", OffsetDateTime.class).toInstant(), row.getBoolean("revoked")), ids.getFirst());
        return sessions.isEmpty() ? null : sessions.getFirst();
    }

    private void revoke(String session) {
        jdbc.update("update refresh_sessions set revoked = true where id = ?", session);
    }

    private TokenPair pair(AppUser user, String session, Instant expiry) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("insert into refresh_tokens(token_hash, session_id, consumed) values (?, ?, false)", hash(refresh), session);
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(user.getUsername(), null,
                user.getRoles().stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role.name())).toList());
        var access = tokens.issue(authentication);
        return new TokenPair(access.accessToken(), access.tokenType(), access.expiresIn(), refresh,
                Math.max(0, Duration.between(Instant.now(), expiry).toSeconds()));
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private record Session(String id, Long userId, Instant expiresAt, boolean revoked) {}
    public record TokenPair(String accessToken, String tokenType, long expiresIn,
                            String refreshToken, long refreshExpiresIn) {
        @Override
        public String toString() {
            return "TokenPair[tokenType=" + tokenType + ", expiresIn=" + expiresIn
                    + ", refreshExpiresIn=" + refreshExpiresIn + ", tokens=REDACTED]";
        }
    }
}
