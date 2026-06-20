package com.teamtrace.backend.repository;

import com.teamtrace.backend.entity.SystemAnnouncementRecipient;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SystemAnnouncementRecipientRepository extends JpaRepository<SystemAnnouncementRecipient, Long> {
    Optional<SystemAnnouncementRecipient> findByAnnouncementIdAndUserId(Long announcementId, Long userId);

    List<SystemAnnouncementRecipient> findByUserIdOrderByIdDesc(Long userId);

    long countByAnnouncementId(Long announcementId);

    long countByAnnouncementIdAndReadAtIsNotNull(Long announcementId);

    long countByAnnouncementIdAndConfirmedAtIsNotNull(Long announcementId);

    @Query("SELECT r FROM SystemAnnouncementRecipient r WHERE r.userId = :userId AND r.announcementId IN :announcementIds")
    List<SystemAnnouncementRecipient> findForUserAndAnnouncementIds(
            @Param("userId") Long userId,
            @Param("announcementIds") Collection<Long> announcementIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM SystemAnnouncementRecipient r WHERE r.announcementId = :announcementId")
    int deleteByAnnouncementId(@Param("announcementId") Long announcementId);
}
