package com.exe.skillverse_backend.auth_service.repository;

import com.exe.skillverse_backend.auth_service.entity.PrimaryRole;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.entity.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    interface UserSecurityInfo {
        LocalDateTime getPasswordChangedAt();
        UserStatus getStatus();
    }

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByEmailAndStatus(String email, UserStatus status);
    
    // Admin user management queries
    List<User> findByPrimaryRole(PrimaryRole primaryRole);
    
    List<User> findByStatus(UserStatus status);
    
    List<User> findByPrimaryRoleAndStatus(PrimaryRole primaryRole, UserStatus status);
    
    Long countByPrimaryRole(PrimaryRole primaryRole);
    
     Long countByStatus(UserStatus status);

    /**
     * Find oldest users (longest active on platform)
     */
    @Query("SELECT u FROM User u ORDER BY u.createdAt ASC")
    List<User> findOldestUsers(org.springframework.data.domain.Pageable pageable);
    
    @Query("SELECT u FROM User u WHERE " +
           "LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))")
    List<User> searchUsers(@Param("search") String search);
    
    @Query("SELECT u FROM User u WHERE " +
           "(:role IS NULL OR u.primaryRole = :role) AND " +
           "(:status IS NULL OR u.status = :status) AND " +
           "(LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')))")
    List<User> findUsersWithFilters(
        @Param("role") PrimaryRole role,
        @Param("status") UserStatus status,
        @Param("search") String search
    );

    /**
     * [OPTIMIZED] Find user by email with roles eagerly fetched.
     * Use this for authentication to avoid LazyInitializationException.
     * Prevents N+1 when roles are needed.
     */
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.roles WHERE u.email = :email")
    Optional<User> findByEmailWithRoles(@Param("email") String email);

    /**
     * [OPTIMIZED] Find user by ID with roles eagerly fetched.
     * Use when roles are needed for authorization checks.
     */
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.roles WHERE u.id = :id")
    Optional<User> findByIdWithRoles(@Param("id") Long id);

    /**
     * [OPTIMIZED] Get only passwordChangedAt for JWT validation.
     * Avoids loading entire User entity for every request.
     * Returns null if user not found or passwordChangedAt is null.
     */
    @Query("SELECT u.passwordChangedAt FROM User u WHERE u.id = :userId")
    Optional<LocalDateTime> findPasswordChangedAtById(@Param("userId") Long userId);

    /**
     * [OPTIMIZED] Get security-relevant user fields for JWT validation in one query.
     */
    @Query("SELECT u.passwordChangedAt as passwordChangedAt, u.status as status FROM User u WHERE u.id = :userId")
    Optional<UserSecurityInfo> findSecurityInfoById(@Param("userId") Long userId);
}
