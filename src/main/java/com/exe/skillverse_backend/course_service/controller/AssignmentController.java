package com.exe.skillverse_backend.course_service.controller;

import com.exe.skillverse_backend.course_service.dto.assignmentdto.AssignmentCreateDTO;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.AssignmentDetailDTO;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.AssignmentGradeDTO;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.AssignmentSubmissionCreateDTO;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.AssignmentSubmissionDetailDTO;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.AssignmentUpdateDTO;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.MentorSubmissionItemDTO;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.MentorSubmissionStatsDTO;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.PageResponse;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.PendingSubmissionItemDTO;
import com.exe.skillverse_backend.course_service.service.dto.AssignmentUpdateResultDTO;
import com.exe.skillverse_backend.course_service.service.AssignmentService;
import com.exe.skillverse_backend.shared.util.JwtUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assignments")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Assignment Management", description = "APIs for managing assignments and submissions")
public class AssignmentController {

    private final AssignmentService assignmentService;

    /**
     * Extract userId from JWT. Delegates to {@link JwtUtils#extractUserId(Jwt)}.
     * Tries the "userId" claim first, falls back to "sub" claimtest
     */
    private Long extractUserId(Jwt jwt) {
        return JwtUtils.extractUserId(jwt);
    }

    // ========== Mentor Dashboard Batch ==========
    @GetMapping("/mentor/pending-all")
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    @Operation(summary = "Get all pending submissions for the authenticated mentor across all courses (batch)")
    public ResponseEntity<List<PendingSubmissionItemDTO>> getAllPendingForMentor(
            @AuthenticationPrincipal Jwt jwt) {

        Long mentorId = extractUserId(jwt);
        List<PendingSubmissionItemDTO> items = assignmentService.getAllPendingForMentor(mentorId);
        return ResponseEntity.ok(items);
    }

    @GetMapping("/mentor/submissions")
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    @Operation(summary = "Get all newest submissions for the authenticated mentor across all courses")
    public ResponseEntity<List<MentorSubmissionItemDTO>> getAllMentorSubmissions(
            @AuthenticationPrincipal Jwt jwt) {

        Long mentorId = extractUserId(jwt);
        List<MentorSubmissionItemDTO> items = assignmentService.getAllMentorSubmissions(mentorId);
        return ResponseEntity.ok(items);
    }

    @GetMapping("/mentor/submissions/paged")
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    @Operation(summary = "Get paged newest submissions for the authenticated mentor across all courses")
    public ResponseEntity<Page<MentorSubmissionItemDTO>> getMentorSubmissionsPage(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "ALL") String filter,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 10) Pageable pageable) {

        Long mentorId = extractUserId(jwt);
        Page<MentorSubmissionItemDTO> items = assignmentService.getMentorSubmissionsPage(
                mentorId,
                filter,
                search,
                pageable
        );
        return ResponseEntity.ok(items);
    }

    @GetMapping("/mentor/submissions/stats")
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    @Operation(summary = "Get aggregated mentor submission stats (total/pending/graded/late)")
    public ResponseEntity<MentorSubmissionStatsDTO> getMentorSubmissionStats(
            @AuthenticationPrincipal Jwt jwt) {

        Long mentorId = extractUserId(jwt);
        MentorSubmissionStatsDTO stats = assignmentService.getMentorSubmissionStats(mentorId);
        return ResponseEntity.ok(stats);
    }

    // ========== Assignment Management ==========
    @PostMapping
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    @Operation(summary = "Create a new assignment for a module")
    public ResponseEntity<AssignmentDetailDTO> createAssignment(
            @Parameter(description = "Module ID") @RequestParam @NotNull Long moduleId,
            @Parameter(description = "Assignment creation data") @Valid @RequestBody AssignmentCreateDTO dto,
            @AuthenticationPrincipal Jwt jwt) {

        Long actorId = extractUserId(jwt);
        log.info("Creating assignment for module {} by user {}", moduleId, actorId);
        AssignmentDetailDTO created = assignmentService.createAssignment(moduleId, dto, actorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{assignmentId}")
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    @Operation(summary = "Update an existing assignment")
    public ResponseEntity<AssignmentDetailDTO> updateAssignment(
            @Parameter(description = "Assignment ID") @PathVariable @NotNull Long assignmentId,
            @Parameter(description = "Assignment update data") @Valid @RequestBody AssignmentUpdateDTO dto,
            @AuthenticationPrincipal Jwt jwt) {

        Long actorId = extractUserId(jwt);
        log.info("Updating assignment {} by user {}", assignmentId, actorId);
        AssignmentUpdateResultDTO result = assignmentService.updateAssignment(assignmentId, dto, actorId);
        return ResponseEntity.ok(result.assignment());
    }

    @GetMapping("/{assignmentId}")
    @Operation(summary = "Get assignment details by ID")
    public ResponseEntity<AssignmentDetailDTO> getAssignmentById(
            @Parameter(description = "Assignment ID") @PathVariable @NotNull Long assignmentId,
            @AuthenticationPrincipal Jwt jwt) {

        Long actorId = extractUserId(jwt);
        log.info("Getting assignment details for ID {}", assignmentId);
        AssignmentDetailDTO assignment = assignmentService.getAssignmentById(assignmentId, actorId);
        return ResponseEntity.ok(assignment);
    }

    @DeleteMapping("/{assignmentId}")
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    @Operation(summary = "Delete an assignment")
    public ResponseEntity<Void> deleteAssignment(
            @Parameter(description = "Assignment ID") @PathVariable @NotNull Long assignmentId,
            @AuthenticationPrincipal Jwt jwt) {

        Long actorId = extractUserId(jwt);
        log.info("Deleting assignment {} by user {}", assignmentId, actorId);
        assignmentService.deleteAssignment(assignmentId, actorId);
        return ResponseEntity.noContent().build();
    }

    // ========== Submission Management ==========
    @PostMapping("/{assignmentId}/submissions")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Submit an assignment")
    public ResponseEntity<AssignmentSubmissionDetailDTO> submitAssignment(
            @Parameter(description = "Assignment ID") @PathVariable @NotNull Long assignmentId,
            @Parameter(description = "Submission data") @Valid @RequestBody AssignmentSubmissionCreateDTO dto,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = extractUserId(jwt);
        log.info("User {} submitting assignment {}", userId, assignmentId);
        AssignmentSubmissionDetailDTO submission = assignmentService.submit(assignmentId, userId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(submission);
    }

    @PutMapping("/submissions/{submissionId}/grade")
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    @Operation(summary = "Grade an assignment submission")
    public ResponseEntity<AssignmentSubmissionDetailDTO> gradeSubmission(
            @Parameter(description = "Submission ID") @PathVariable @NotNull Long submissionId,
            @Parameter(description = "Grading data") @RequestBody(required = false) AssignmentGradeDTO grading,
            @Parameter(description = "Score (legacy)") @RequestParam(required = false) BigDecimal score,
            @Parameter(description = "Feedback (legacy)") @RequestParam(required = false) String feedback,
            @AuthenticationPrincipal Jwt jwt) {

        Long graderId = extractUserId(jwt);
        log.info("User {} grading submission {}", graderId, submissionId);
        AssignmentSubmissionDetailDTO graded = assignmentService.grade(submissionId, graderId, grading, score, feedback);
        return ResponseEntity.ok(graded);
    }

    @GetMapping("/{assignmentId}/submissions")
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    @Operation(summary = "List submissions for an assignment (mentor/admin only, newest per student)")
    public ResponseEntity<PageResponse<AssignmentSubmissionDetailDTO>> listSubmissions(
            @Parameter(description = "Assignment ID") @PathVariable @NotNull Long assignmentId,
            @PageableDefault(size = 20) Pageable pageable) {

        PageResponse<AssignmentSubmissionDetailDTO> submissions = assignmentService.listSubmissions(assignmentId, pageable);
        return ResponseEntity.ok(submissions);
    }

    @GetMapping("/{assignmentId}/submissions/mine")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get current user's submissions for an assignment (all versions)")
    public ResponseEntity<List<AssignmentSubmissionDetailDTO>> getMySubmissions(
            @Parameter(description = "Assignment ID") @PathVariable @NotNull Long assignmentId,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = extractUserId(jwt);
        log.info("Getting submissions for user {} on assignment {}", userId, assignmentId);
        List<AssignmentSubmissionDetailDTO> submissions = assignmentService.getUserSubmissions(assignmentId, userId);
        return ResponseEntity.ok(submissions);
    }

    @GetMapping("/{assignmentId}/submissions/pending")
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    @Operation(summary = "Get pending submissions for grading (mentor/admin)")
    public ResponseEntity<List<AssignmentSubmissionDetailDTO>> getPendingSubmissions(
            @Parameter(description = "Assignment ID") @PathVariable @NotNull Long assignmentId,
            @AuthenticationPrincipal Jwt jwt) {

        Long actorId = extractUserId(jwt);
        log.info("Getting pending submissions for assignment {} by user {}", assignmentId, actorId);
        List<AssignmentSubmissionDetailDTO> submissions = assignmentService.getPendingSubmissions(assignmentId, actorId);
        return ResponseEntity.ok(submissions);
    }

    @GetMapping("/{assignmentId}/submissions/pending/count")
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    @Operation(summary = "Count pending submissions for an assignment")
    public ResponseEntity<Long> countPendingSubmissions(
            @Parameter(description = "Assignment ID") @PathVariable @NotNull Long assignmentId,
            @AuthenticationPrincipal Jwt jwt) {

        Long actorId = extractUserId(jwt);
        log.info("Counting pending submissions for assignment {} by user {}", assignmentId, actorId);
        Long count = assignmentService.countPendingSubmissions(assignmentId, actorId);
        return ResponseEntity.ok(count);
    }

    /**
     * Download a submitted file with proper Content-Disposition header.
     * Authorization: only the student who submitted, the course author/mentor, or an admin may download.
     * GET /api/assignments/submissions/{submissionId}/download
     */
    @GetMapping("/submissions/{submissionId}/download")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Download submitted file",
               description = "Streams the submitted file with proper filename for download. " +
                       "Only the submitting student, course author/mentor, or admin may download.")
    public ResponseEntity<byte[]> downloadSubmissionFile(
            @Parameter(description = "Submission ID") @PathVariable @NotNull Long submissionId,
            @AuthenticationPrincipal Jwt jwt) throws IOException {

        Long actorId = JwtUtils.extractUserId(jwt);
        log.debug("[API] GET /api/assignments/submissions/{}/download by user {}", submissionId, actorId);
        return assignmentService.streamSubmissionFile(submissionId, actorId);
    }

    /**
     * Get the prior submission (attempt N-1) for a given submission.
     * Used by mentor grading UI to show AI feedback from the previous submission
     * when a student has resubmitted after an AI failure.
     * GET /api/assignments/submissions/{submissionId}/prior
     */
    @GetMapping("/submissions/{submissionId}/prior")
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    @Operation(summary = "Get prior submission (attempt N-1) for a given submission",
               description = "Returns the previous submission for the same student+assignment "
                           + "to surface AI feedback from before a resubmit.")
    public ResponseEntity<AssignmentSubmissionDetailDTO> getPriorSubmission(
            @Parameter(description = "Submission ID") @PathVariable @NotNull Long submissionId,
            @AuthenticationPrincipal Jwt jwt) {

        Long actorId = extractUserId(jwt);
        log.info("Getting prior submission for submission {} by actor {}", submissionId, actorId);
        AssignmentSubmissionDetailDTO prior = assignmentService.getPriorSubmission(submissionId, actorId);
        return ResponseEntity.ok(prior);
    }
}
