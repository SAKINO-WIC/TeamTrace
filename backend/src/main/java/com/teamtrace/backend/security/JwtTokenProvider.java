package com.teamtrace.backend.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long expirationSeconds;
    private final TokenBlacklistService tokenBlacklistService;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-seconds}") long expirationSeconds,
            TokenBlacklistService tokenBlacklistService
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = expirationSeconds;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    public String generateToken(Long userId, String email, String phone, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("phone", phone)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(secretKey)
                .compact();
    }

    public Long extractUserId(String token) {
        String subject = parseClaims(token).getSubject();
        return Long.parseLong(subject);
    }

    public String extractRole(String token) {
        Object role = parseClaims(token).get("role");
        return role == null ? null : String.valueOf(role);
    }

    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return !tokenBlacklistService.isRevoked(token);
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    public Date extractExpiration(String token) {
        return parseClaims(token).getExpiration();
    }

    public String resolveBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return null;
        }
        String prefix = "Bearer ";
        if (!authorizationHeader.startsWith(prefix)) {
            return null;
        }
        return authorizationHeader.substring(prefix.length()).trim();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
