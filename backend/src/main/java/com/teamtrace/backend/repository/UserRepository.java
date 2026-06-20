package com.teamtrace.backend.repository;

import com.teamtrace.backend.entity.User;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);
    Optional<User> findByEmail(String email);
    Optional<User> findByPhone(String phone);
    boolean existsByPhoneAndIsDeleted(String phone, Integer isDeleted);
    Optional<User> findByPhoneAndIsDeleted(String phone, Integer isDeleted);
    boolean existsByEmailAndIsDeleted(String email, Integer isDeleted);
    Optional<User> findByEmailAndIsDeleted(String email, Integer isDeleted);

    Page<User> findByRoleAndIsDeletedOrderByIdDesc(User.Role role, Integer isDeleted, Pageable pageable);
    Page<User> findByIsDeletedOrderByIdDesc(Integer isDeleted, Pageable pageable);

    long countByRoleAndIsDeleted(User.Role role, Integer isDeleted);

    long countByRoleInAndIsDeleted(java.util.Collection<User.Role> roles, Integer isDeleted);
    List<User> findByRoleInOrderByCreatedAtAscIdAsc(List<User.Role> roles);
    List<User> findByIsDeleted(Integer isDeleted);
    List<User> findByRoleAndIsDeleted(User.Role role, Integer isDeleted);
    List<User> findByRoleInAndWelcomeEmailSentAtIsNullAndIsDeletedOrderByCreatedAtAscIdAsc(
            List<User.Role> roles,
            Integer isDeleted,
            Pageable pageable);

    long countByRoleInAndWelcomeEmailSentAtIsNullAndIsDeleted(List<User.Role> roles, Integer isDeleted);

    @Query("SELECT COALESCE(MAX(u.ceremonyNo), 0) FROM User u WHERE u.role IN :roles")
    Long findMaxCeremonyNo(@Param("roles") List<User.Role> roles);

    @Query("""
            SELECT u
            FROM User u
            WHERE (:role IS NULL OR u.role = :role)
              AND (:status IS NULL OR u.status = :status)
              AND (:isDeleted IS NULL OR u.isDeleted = :isDeleted)
              AND (:phone IS NULL OR u.phone LIKE CONCAT('%', :phone, '%'))
              AND (:email IS NULL OR u.email LIKE CONCAT('%', :email, '%'))
              AND (:name IS NULL OR u.name LIKE CONCAT('%', :name, '%'))
            """)
    Page<User> searchUsers(
            @Param("role") User.Role role,
            @Param("status") Integer status,
            @Param("isDeleted") Integer isDeleted,
            @Param("phone") String phone,
            @Param("email") String email,
            @Param("name") String name,
            Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM User u WHERE u.isDeleted = 1 AND u.deletedAt < :cutoff")
    int deleteExpiredSoftDeletes(@Param("cutoff") LocalDateTime cutoff);
}
