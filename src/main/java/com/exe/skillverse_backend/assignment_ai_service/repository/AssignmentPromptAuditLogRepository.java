package com.exe.skillverse_backend.assignment_ai_service.repository;

import com.exe.skillverse_backend.assignment_ai_service.entity.AssignmentPromptAuditLog;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface AssignmentPromptAuditLogRepository extends JpaRepository<AssignmentPromptAuditLog, Long> {

    @Transactional(readOnly = true)
    @Query("SELECT log FROM AssignmentPromptAuditLog log WHERE log.assignment.id = :assignmentId ORDER BY log.createdAt DESC")
    Page<AssignmentPromptAuditLog> findByAssignmentIdOrderByCreatedAtDesc(
            @Param("assignmentId") Long assignmentId,
            Pageable pageable);

    @Transactional(readOnly = true)
    @Query("SELECT log FROM AssignmentPromptAuditLog log WHERE log.assignment.id = :assignmentId ORDER BY log.createdAt DESC")
    List<AssignmentPromptAuditLog> findAllByAssignmentId(@Param("assignmentId") Long assignmentId);

    @Transactional(readOnly = true)
    long countByAssignmentId(Long assignmentId);
}