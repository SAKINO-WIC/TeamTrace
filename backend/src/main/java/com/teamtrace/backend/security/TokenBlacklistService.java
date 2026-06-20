package com.teamtrace.backend.security;

import com.teamtrace.backend.entity.TokenBlacklist;
import com.teamtrace.backend.repository.TokenBlacklistRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TokenBlacklistService {

    private final TokenBlacklistRepository repository;
    private final ConcurrentHashMap<String, Boolean> blacklistCache = new ConcurrentHashMap<>();

    public TokenBlacklistService(TokenBlacklistRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void revoke(String token, Date expireAt) {
        if (token == null || token.isBlank() || expireAt == null) {
            return;
        }
        String hash = sha256(token);
        LocalDateTime expiry = expireAt.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        TokenBlacklist entry = new TokenBlacklist();
        entry.setTokenHash(hash);
        entry.setExpireAt(expiry);
        repository.save(entry);
        blacklistCache.put(hash, true);
    }

    @Transactional(readOnly = true)
    public boolean isRevoked(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String hash = sha256(token);
        // Check in-memory cache first to avoid DB hit on every request
        if (blacklistCache.containsKey(hash)) {
            return true;
        }
        boolean revoked = repository.existsByTokenHash(hash);
        if (revoked) {
            blacklistCache.put(hash, true);
        }
        return revoked;
    }

    @Scheduled(fixedDelay = 3600000) // Every hour
    @Transactional
    public void cleanupExpired() {
        repository.deleteExpired(LocalDateTime.now());
        blacklistCache.clear();
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
