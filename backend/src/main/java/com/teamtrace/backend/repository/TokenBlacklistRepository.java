package com.teamtrace.backend.repository;

import com.teamtrace.backend.entity.TokenBlacklist;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TokenBlacklistRepository extends JpaRepository<TokenBlacklist, Long> {

    boolean existsByTokenHash(String tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM TokenBlacklist t WHERE t.expireAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}
