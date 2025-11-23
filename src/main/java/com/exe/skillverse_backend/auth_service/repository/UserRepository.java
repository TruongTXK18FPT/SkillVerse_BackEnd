package com.exe.skillverse_backend.auth_service.repository;

import com.exe.skillverse_backend.auth_service.entity.PrimaryRole;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.entity.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByEmailAndStatus(String email, UserStatus status);
    
    // Admin user management queries
    List<User> findByPrimaryRole(PrimaryRole primaryRole);
    
    List<User> findByStatus(UserStatus status);
    
    List<User> findByPrimaryRoleAndStatus(PrimaryRole primaryRole, UserStatus status);
    
    Long countByPrimaryRole(PrimaryRole primaryRole);
    
    Long countByStatus(UserStatus status);
    
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
}
