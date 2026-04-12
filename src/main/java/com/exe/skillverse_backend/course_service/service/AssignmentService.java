package com.exe.skillverse_backend.course_service.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.AssignmentCreateDTO;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.AssignmentDetailDTO;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.AssignmentGradeDTO;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.AssignmentSubmissionCreateDTO;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.AssignmentSubmissionDetailDTO;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.AssignmentSummaryDTO;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.AssignmentUpdateDTO;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.MentorSubmissionItemDTO;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.MentorSubmissionStatsDTO;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.PageResponse;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.PendingSubmissionItemDTO;
import com.exe.skillverse_backend.course_service.service.dto.AssignmentUpdateResultDTO;

public interface AssignmentService {
    
    AssignmentDetailDTO createAssignment(Long moduleId, AssignmentCreateDTO dto, Long actorId);
    
    AssignmentUpdateResultDTO updateAssignment(Long assignmentId, AssignmentUpdateDTO dto, Long actorId);
    
    AssignmentDetailDTO getAssignmentById(Long assignmentId, Long actorId);
    
    void deleteAssignment(Long assignmentId, Long actorId);

    AssignmentSubmissionDetailDTO submit(Long assignmentId, Long userId, AssignmentSubmissionCreateDTO dto);
    
    AssignmentSubmissionDetailDTO grade(Long submissionId, Long graderId, AssignmentGradeDTO grading, BigDecimal legacyScore, String legacyFeedback);
    
    PageResponse<AssignmentSubmissionDetailDTO> listSubmissions(Long assignmentId, Pageable p);
    
    List<AssignmentSummaryDTO> listAssignmentsByModule(Long moduleId, Long actorId);
    
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

    /**
     * Get ALL pending submissions across all courses/modules/assignments
     * owned by the given mentor. Single-query batch load.
     */
    List<PendingSubmissionItemDTO> getAllPendingForMentor(Long mentorId);

    /**
     * Get all newest submissions across all courses/modules/assignments
     * owned by the given mentor.
     */
    List<MentorSubmissionItemDTO> getAllMentorSubmissions(Long mentorId);
    Page<MentorSubmissionItemDTO> getMentorSubmissionsPage(Long mentorId, String filter, String search, Pageable pageable);
    MentorSubmissionStatsDTO getMentorSubmissionStats(Long mentorId);

    /**
     * Stream a submitted file as a downloadable file with proper Content-Disposition header.
     */
    ResponseEntity<byte[]> streamSubmissionFile(Long submissionId) throws IOException;
}
