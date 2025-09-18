package com.exe.skillverse_backend.shared.service;

import com.exe.skillverse_backend.admin_service.dto.response.AuditLogDto;
import com.exe.skillverse_backend.shared.entity.AuditLog;
import com.exe.skillverse_backend.shared.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Logs an action to the audit log
     * 
     * @param userId     - ID of the user performing the action (can be null for
     *                   system actions)
     * @param action     - Action being performed (e.g., CREATE, UPDATE, DELETE,
     *                   LOGIN, LOGOUT)
     * @param objectType - Type of object being acted upon (e.g., USER, COURSE,
     *                   MENTOR)
     * @param objectId   - ID of the object being acted upon (can be null)
     * @param details    - Additional details about the action (JSON or text)
     */
    @Transactional
    public void logAction(Long userId, String action, String objectType, String objectId, String details) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setUserId(userId);
            auditLog.setAction(action);
            auditLog.setObjectType(objectType);
            auditLog.setObjectId(objectId != null ? Long.parseLong(objectId) : null);
            auditLog.setDetails(details);
            auditLog.setTimestamp(LocalDateTime.now());

            auditLogRepository.save(auditLog);

            log.debug("Audit log created: userId={}, action={}, objectType={}, objectId={}",
                    userId, action, objectType, objectId);
        } catch (Exception e) {
            log.error("Failed to create audit log: userId={}, action={}, objectType={}, objectId={}, error={}",
                    userId, action, objectType, objectId, e.getMessage());
            // Don't throw exception to avoid breaking the main business logic
        }
    }

    /**
     * Overloaded method for Long objectId
     */
    @Transactional
    public void logAction(Long userId, String action, String objectType, Long objectId, String details) {
        logAction(userId, action, objectType, objectId != null ? objectId.toString() : null, details);
    }

    /**
     * Logs system actions (no user associated)
     */
    @Transactional
    public void logSystemAction(String action, String objectType, String objectId, String details) {
        logAction(null, action, objectType, objectId, details);
    }

    /**
     * Get audit logs for a specific user
     */
    public List<AuditLog> getUserAuditLogs(Long userId) {
        return auditLogRepository.findByUserIdOrderByTimestampDesc(userId);
    }

    /**
     * Get audit logs for a specific object
     */
    public List<AuditLog> getObjectAuditLogs(String objectType, Long objectId) {
        return auditLogRepository.findByObjectTypeAndObjectIdOrderByTimestampDesc(objectType, objectId);
    }

    /**
     * Get audit logs by action type
     */
    public List<AuditLog> getAuditLogsByAction(String action) {
        return auditLogRepository.findByActionOrderByTimestampDesc(action);
    }

    /**
     * Get recent audit logs (last 100 records)
     */
    public List<AuditLog> getRecentAuditLogs() {
        return auditLogRepository.findTop100ByOrderByTimestampDesc();
    }

    /**
     * Get recent audit logs as DTOs (last 100 records) - for API responses
     */
    public List<AuditLogDto> getRecentAuditLogsDto() {
        List<AuditLog> auditLogs = auditLogRepository.findTop100ByOrderByTimestampDesc();
        return auditLogs.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * Get user audit logs as DTOs
     */
    public List<AuditLogDto> getUserAuditLogsDto(Long userId) {
        List<AuditLog> auditLogs = auditLogRepository.findByUserIdOrderByTimestampDesc(userId);
        return auditLogs.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * Get object audit logs as DTOs
     */
    public List<AuditLogDto> getObjectAuditLogsDto(String objectType, Long objectId) {
        List<AuditLog> auditLogs = auditLogRepository.findByObjectTypeAndObjectIdOrderByTimestampDesc(objectType, objectId);
        return auditLogs.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * Get audit logs by action as DTOs
     */
    public List<AuditLogDto> getAuditLogsByActionDto(String action) {
        List<AuditLog> auditLogs = auditLogRepository.findByActionOrderByTimestampDesc(action);
        return auditLogs.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * Get audit logs by date range as DTOs
     */
    public List<AuditLogDto> getAuditLogsByDateRangeDto(LocalDateTime startDate, LocalDateTime endDate) {
        List<AuditLog> auditLogs = auditLogRepository.findByTimestampBetween(startDate, endDate);
        return auditLogs.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * Convert AuditLog entity to DTO
     */
    private AuditLogDto convertToDto(AuditLog auditLog) {
        return AuditLogDto.builder()
                .id(auditLog.getId())
                .userId(auditLog.getUserId())
                .userEmail(auditLog.getUser() != null ? auditLog.getUser().getEmail() : null)
                .userFullName(auditLog.getUser() != null ? 
                    (auditLog.getUser().getFirstName() + " " + auditLog.getUser().getLastName()).trim() : null)
                .action(auditLog.getAction())
                .objectType(auditLog.getObjectType())
                .objectId(auditLog.getObjectId())
                .details(auditLog.getDetails())
                .timestamp(auditLog.getTimestamp())
                .build();
    }

    /**
     * Get audit logs within a date range
     */
    public List<AuditLog> getAuditLogsByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return auditLogRepository.findByTimestampBetween(startDate, endDate);
    }
}