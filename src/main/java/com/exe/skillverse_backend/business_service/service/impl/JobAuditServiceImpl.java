package com.exe.skillverse_backend.business_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.business_service.entity.JobStatusAuditLog;
import com.exe.skillverse_backend.business_service.entity.enums.ShortTermApplicationStatus;
import com.exe.skillverse_backend.business_service.entity.enums.ShortTermJobStatus;
import com.exe.skillverse_backend.business_service.repository.JobStatusAuditLogRepository;
import com.exe.skillverse_backend.business_service.service.JobAuditService;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class JobAuditServiceImpl implements JobAuditService {

    private final JobStatusAuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Override
    public void logJobStatusChange(
            Long jobId,
            String previousStatus,
            String newStatus,
            Long userId,
            JobStatusAuditLog.AuditRole role,
            String reason) {

        log.info("Logging job status change: Job {} from {} to {} by user {}",
                jobId, previousStatus, newStatus, userId);

        User user = getUserById(userId);

        JobStatusAuditLog auditLog = JobStatusAuditLog.builder()
                .jobId(jobId)
                .previousStatus(previousStatus)
                .newStatus(newStatus)
                .changedBy(user)
                .changedByRole(role)
                .reason(reason)
                .build();

        auditLogRepository.save(auditLog);
    }

    @Override
    public void logShortTermJobStatusChange(
            Long shortTermJobId,
            ShortTermJobStatus previousStatus,
            ShortTermJobStatus newStatus,
            Long userId,
            JobStatusAuditLog.AuditRole role,
            String reason) {

        log.info("Logging short-term job status change: Job {} from {} to {} by user {}",
                shortTermJobId, previousStatus, newStatus, userId);

        User user = getUserById(userId);

        JobStatusAuditLog auditLog = JobStatusAuditLog.builder()
                .shortTermJobId(shortTermJobId)
                .previousStatus(previousStatus.name())
                .newStatus(newStatus.name())
                .changedBy(user)
                .changedByRole(role)
                .reason(reason)
                .build();

        auditLogRepository.save(auditLog);
    }

    @Override
    public void logApplicationStatusChange(
            Long applicationId,
            ShortTermApplicationStatus previousStatus,
            ShortTermApplicationStatus newStatus,
            Long userId,
            JobStatusAuditLog.AuditRole role,
            String reason) {

        log.info("Logging application status change: Application {} from {} to {} by user {}",
                applicationId, previousStatus, newStatus, userId);

        User user = getUserById(userId);

        JobStatusAuditLog auditLog = JobStatusAuditLog.builder()
                .applicationId(applicationId)
                .previousStatus(previousStatus.name())
                .newStatus(newStatus.name())
                .changedBy(user)
                .changedByRole(role)
                .reason(reason)
                .build();

        auditLogRepository.save(auditLog);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobStatusAuditLog> getAuditLogsForJob(Long jobId) {
        return auditLogRepository.findByJobIdOrderByCreatedAtDesc(jobId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobStatusAuditLog> getAuditLogsForShortTermJob(Long shortTermJobId) {
        return auditLogRepository.findByShortTermJobIdOrderByCreatedAtDesc(shortTermJobId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobStatusAuditLog> getAuditLogsForApplication(Long applicationId) {
        return auditLogRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId);
    }

    private User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with ID: " + userId));
    }
}
