package com.exe.skillverse_backend.course_service.service;

import com.exe.skillverse_backend.course_service.dto.assignmentdto.*;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface AssignmentService {
    
    AssignmentDetailDTO createAssignment(Long moduleId, AssignmentCreateDTO dto, Long actorId);
    
    AssignmentDetailDTO updateAssignment(Long assignmentId, AssignmentUpdateDTO dto, Long actorId);
    
    AssignmentDetailDTO getAssignmentById(Long assignmentId);
    
    void deleteAssignment(Long assignmentId, Long actorId);

    AssignmentSubmissionDetailDTO submit(Long assignmentId, Long userId, AssignmentSubmissionCreateDTO dto);
    
    AssignmentSubmissionDetailDTO grade(Long submissionId, Long graderId, AssignmentGradeDTO grading);
    
    List<AssignmentSubmissionDetailDTO> listSubmissions(Long assignmentId, Pageable p);
    
    List<AssignmentSummaryDTO> listAssignmentsByModule(Long moduleId);
    
    /**
     * Get all submissions for a specific user on an assignment (all versions).
     */
    List<AssignmentSubmissionDetailDTO> getUserSubmissions(Long assignmentId, Long userId);
    
    /**
     * Get pending (ungraded) submissions for mentor grading dashboard.
     */
    List<AssignmentSubmissionDetailDTO> getPendingSubmissions(Long assignmentId, Long actorId);
    
    /**
     * Count pending submissions for badge display.
     */
    Long countPendingSubmissions(Long assignmentId, Long actorId);
}
