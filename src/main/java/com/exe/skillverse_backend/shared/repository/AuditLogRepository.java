package com.exe.skillverse_backend.shared.repository;

import com.exe.skillverse_backend.shared.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    // Find audit logs by user
    List<AuditLog> findByUserIdOrderByTimestampDesc(Long userId);

    // Find audit logs by object type and ID
    List<AuditLog> findByObjectTypeAndObjectIdOrderByTimestampDesc(String objectType, Long objectId);

    // Find audit logs by action
    List<AuditLog> findByActionOrderByTimestampDesc(String action);

    // Find audit logs within a date range
    @Query("SELECT a FROM AuditLog a WHERE a.timestamp BETWEEN :startDate AND :endDate ORDER BY a.timestamp DESC")
    List<AuditLog> findByTimestampBetween(@Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    // Find recent audit logs (last N records)
    List<AuditLog> findTop100ByOrderByTimestampDesc();
}