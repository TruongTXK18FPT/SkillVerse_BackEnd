package com.exe.skillverse_backend.auth_service.repository;

import com.exe.skillverse_backend.auth_service.entity.InvalidatedToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface InvalidatedTokenRepository extends JpaRepository<InvalidatedToken, String> {

    boolean existsByJti(String jti);

    @Modifying
    @Query("DELETE FROM InvalidatedToken i WHERE i.invalidatedAt < :cutoffDate")
    void deleteOldInvalidatedTokens(@Param("cutoffDate") LocalDateTime cutoffDate);
}