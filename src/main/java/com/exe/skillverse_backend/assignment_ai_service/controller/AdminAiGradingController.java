package com.exe.skillverse_backend.assignment_ai_service.controller;

import com.exe.skillverse_backend.assignment_ai_service.dto.AiGradingStatsDTO;
import com.exe.skillverse_backend.assignment_ai_service.dto.AiSubmissionDTO;
import com.exe.skillverse_backend.assignment_ai_service.dto.AdminAiAssignmentConfigDTO;
import com.exe.skillverse_backend.assignment_ai_service.dto.AdminAiGovernanceStatsDTO;
import com.exe.skillverse_backend.assignment_ai_service.dto.AiEnabledUpdateRequestDTO;
import com.exe.skillverse_backend.assignment_ai_service.dto.CreateAuditLogRequestDTO;
import com.exe.skillverse_backend.assignment_ai_service.dto.GradingStyleUpdateRequestDTO;
import com.exe.skillverse_backend.assignment_ai_service.dto.PromptAuditLogDTO;
import com.exe.skillverse_backend.assignment_ai_service.dto.PromptOverrideRequestDTO;
import com.exe.skillverse_backend.assignment_ai_service.entity.AssignmentPromptAuditLog;
import com.exe.skillverse_backend.assignment_ai_service.repository.AssignmentPromptAuditLogRepository;
import com.exe.skillverse_backend.assignment_ai_service.repository.AssignmentSubmissionAiRepository;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.course_service.entity.Assignment;
import com.exe.skillverse_backend.course_service.entity.AssignmentSubmission;
import com.exe.skillverse_backend.course_service.entity.SubmissionCriteriaScore;
import com.exe.skillverse_backend.course_service.repository.AssignmentRepository;
import com.exe.skillverse_backend.course_service.repository.AssignmentSubmissionRepository;
import com.exe.skillverse_backend.course_service.repository.SubmissionCriteriaScoreRepository;
import com.exe.skillverse_backend.course_service.service.CourseLearningProgressService;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.CriteriaScoreDTO;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/ai-grading")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class AdminAiGradingController {
    private static final double LOW_CONFIDENCE_THRESHOLD = 0.6d;

    private final AssignmentSubmissionAiRepository aiRepository;
    private final AssignmentRepository assignmentRepository;
    private final AssignmentSubmissionRepository submissionRepository;
    private final SubmissionCriteriaScoreRepository criteriaScoreRepository;
    private final CourseLearningProgressService progressService;
    private final AssignmentPromptAuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    /**
     * GET /api/admin/ai-grading/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<AiGradingStatsDTO> getStats() {
        Double averageScoreDelta = aiRepository.findAverageScoreDelta();
        AiGradingStatsDTO stats = new AiGradingStatsDTO(
                aiRepository.countByIsAiGradedTrue(),
                aiRepository.countByIsAiGradedTrueAndMentorConfirmedTrue(),
                aiRepository.countByIsAiGradedTrueAndMentorConfirmedNull(),
                aiRepository.countByIsAiGradedTrueAndDisputeFlagTrue(),
                aiRepository.sumAiGradeAttemptCount(),
                aiRepository.countByIsAiGradedTrueAndAiConfidenceLessThan(LOW_CONFIDENCE_THRESHOLD),
                averageScoreDelta != null ? averageScoreDelta : 0d,
                aiRepository.countComparedSubmissions()
        );
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/governance-stats")
    public ResponseEntity<AdminAiGovernanceStatsDTO> getGovernanceStats() {
        AdminAiGovernanceStatsDTO stats = new AdminAiGovernanceStatsDTO(
                assignmentRepository.countByAiGradingEnabledTrue(),
                assignmentRepository.countByAiGradingEnabledTrueAndTrustAiEnabledTrue(),
                assignmentRepository.countCustomPromptEnabledAssignments(),
                assignmentRepository.countByAiGradingEnabledTrueAndGradingStyle("STRICT"),
                assignmentRepository.countByAiGradingEnabledTrueAndGradingStyle("LENIENT"),
                countRiskyTrustAssignments()
        );
        return ResponseEntity.ok(stats);
    }

    /**
     * GET /api/admin/ai-grading/submissions?page=0&size=20&status=all|confirmed|pending|disputed&search=
     */
    @GetMapping("/submissions")
    public ResponseEntity<Page<AiSubmissionDTO>> getSubmissions(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestParam(defaultValue = "exceptions") String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "true") boolean exceptionOnly,
            @RequestParam(defaultValue = "aiGradedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = buildSort(sortBy, direction);
        Pageable sortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
        Page<AssignmentSubmission> submissions = aiRepository.findAiGradedSubmissions(status, search, exceptionOnly, sortedPageable);

        Page<AiSubmissionDTO> dtos = submissions.map(submission -> toDto(submission, false));
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/assignments")
    public ResponseEntity<Page<AdminAiAssignmentConfigDTO>> getAssignments(
            @PageableDefault(size = 12) Pageable pageable,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean trustAiEnabled,
            @RequestParam(required = false) Boolean hasCustomPrompt,
            @RequestParam(required = false) String gradingStyle) {
        Pageable sortedPageable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "updatedAt", "id"));
        Page<Assignment> assignments = assignmentRepository.findAiGradingAssignments(
                search,
                trustAiEnabled,
                hasCustomPrompt,
                gradingStyle,
                sortedPageable
        );
        return ResponseEntity.ok(assignments.map(this::toAssignmentConfigDto));
    }

    @GetMapping("/assignments/{id}")
    public ResponseEntity<AdminAiAssignmentConfigDTO> getAssignmentDetail(@PathVariable("id") Long id) {
        Assignment assignment = assignmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("ASSIGNMENT_NOT_FOUND"));
        return ResponseEntity.ok(toAssignmentConfigDto(assignment));
    }

    /**
     * GET /api/admin/ai-grading/submissions/{id}
     */
    @GetMapping("/submissions/{id}")
    public ResponseEntity<AiSubmissionDTO> getSubmissionDetail(@PathVariable("id") Long id) {
        AssignmentSubmission sub = submissionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("SUBMISSION_NOT_FOUND"));
        return ResponseEntity.ok(toDto(sub, true));
    }

    /**
     * POST /api/admin/ai-grading/recalculate/{id}
     * Admin forces recalculation of isPassed for a submission.
     */
    @PostMapping("/recalculate/{id}")
    public ResponseEntity<Void> recalculateIsPassed(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal Jwt jwt) {
        AssignmentSubmission submission = submissionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("SUBMISSION_NOT_FOUND"));

        Assignment assignment = submission.getAssignment();
        List<SubmissionCriteriaScore> scores = criteriaScoreRepository
                .findBySubmissionId(submission.getId());

        List<CriteriaScoreDTO> criteriaScoreDTOs = scores.stream()
                .map(s -> new CriteriaScoreDTO(
                        s.getId(),
                        s.getCriteria().getId(),
                        s.getCriteria().getName(),
                        s.getScore(),
                        s.getCriteria().getMaxPoints(),
                        s.getCriteria().getPassingPoints(),
                        s.getScore().compareTo(s.getCriteria().getPassingPoints()) >= 0,
                        s.getFeedback()))
                .toList();

        BigDecimal total = scores.stream()
                .map(SubmissionCriteriaScore::getScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        boolean passed = computeIsPassed(assignment, criteriaScoreDTOs, total);
        boolean wasPassed = Boolean.TRUE.equals(submission.getIsPassed());
        submission.setIsPassed(passed);
        submissionRepository.save(submission);

        // Only recalculate progress if pass/fail changed
        if (wasPassed != passed) {
            progressService.recalculateCourseProgress(
                    assignment.getModule().getCourse().getId(),
                    submission.getUser().getId()
            );
        }

        log.info("Admin {} recalculated isPassed for submission {}: {} (was {})",
                extractUserId(jwt), id, passed, wasPassed);
        return ResponseEntity.ok().build();
    }

    // =====================================================
    // NEW ADMIN PROMPT MANAGEMENT ENDPOINTS
    // =====================================================

    /**
     * PUT /api/admin/ai-grading/assignments/{id}/ai-enabled
     * Bật/tắt AI grading cho 1 assignment
     */
    @PutMapping("/assignments/{id}/ai-enabled")
    public ResponseEntity<AdminAiAssignmentConfigDTO> updateAiEnabled(
            @PathVariable("id") Long id,
            @Valid @RequestBody AiEnabledUpdateRequestDTO body,
            @AuthenticationPrincipal Jwt jwt) {

        Assignment assignment = assignmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("ASSIGNMENT_NOT_FOUND"));

        String adminName = getAdminName(jwt);
        String before = assignment.getAiGradingEnabled() != null ? assignment.getAiGradingEnabled().toString() : "null";

        assignment.setAiGradingEnabled(body.enabled());
        assignment.setUpdatedAt(Instant.now());
        assignmentRepository.save(assignment);

        saveAuditLog(assignment, jwt, "AI_ENABLED_TOGGLED", before, body.enabled().toString());

        log.info("Admin {} {} AI grading for assignment {}", adminName,
                Boolean.TRUE.equals(body.enabled()) ? "enabled" : "disabled", id);

        return ResponseEntity.ok(toAssignmentConfigDto(assignment));
    }

    /**
     * PUT /api/admin/ai-grading/assignments/{id}/prompt
     * Override prompt của mentor
     */
    @PutMapping("/assignments/{id}/prompt")
    public ResponseEntity<AdminAiAssignmentConfigDTO> overridePrompt(
            @PathVariable("id") Long id,
            @Valid @RequestBody PromptOverrideRequestDTO body,
            @AuthenticationPrincipal Jwt jwt) {

        Assignment assignment = assignmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("ASSIGNMENT_NOT_FOUND"));

        String adminName = getAdminName(jwt);
        String before = assignment.getAiGradingPrompt();

        assignment.setAiGradingPrompt(body.prompt());
        assignment.setUpdatedAt(Instant.now());
        assignmentRepository.save(assignment);

        saveAuditLog(assignment, jwt, "PROMPT_OVERRIDE", before, body.prompt());

        log.info("Admin {} overrode prompt for assignment {} (length: {} chars)",
                adminName, id, body.prompt().length());

        return ResponseEntity.ok(toAssignmentConfigDto(assignment));
    }

    /**
     * PUT /api/admin/ai-grading/assignments/{id}/grading-style
     * Override grading style (STANDARD/STRICT/LENIENT)
     */
    @PutMapping("/assignments/{id}/grading-style")
    public ResponseEntity<AdminAiAssignmentConfigDTO> updateGradingStyle(
            @PathVariable("id") Long id,
            @Valid @RequestBody GradingStyleUpdateRequestDTO body,
            @AuthenticationPrincipal Jwt jwt) {

        Assignment assignment = assignmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("ASSIGNMENT_NOT_FOUND"));

        String adminName = getAdminName(jwt);
        String before = assignment.getGradingStyle();

        assignment.setGradingStyle(body.style());
        assignment.setUpdatedAt(Instant.now());
        assignmentRepository.save(assignment);

        saveAuditLog(assignment, jwt, "GRADING_STYLE_CHANGED", before, body.style());

        log.info("Admin {} changed grading style for assignment {}: {} -> {}",
                adminName, id, before, body.style());

        return ResponseEntity.ok(toAssignmentConfigDto(assignment));
    }

    /**
     * PUT /api/admin/ai-grading/assignments/{id}/disable-prompt
     * Tắt custom prompt — dùng system default thay thế
     */
    @PutMapping("/assignments/{id}/disable-prompt")
    public ResponseEntity<AdminAiAssignmentConfigDTO> disablePrompt(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal Jwt jwt) {

        Assignment assignment = assignmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("ASSIGNMENT_NOT_FOUND"));

        String adminName = getAdminName(jwt);
        String before = assignment.getAiGradingPrompt();

        assignment.setAiGradingPrompt(null);
        assignment.setUpdatedAt(Instant.now());
        assignmentRepository.save(assignment);

        saveAuditLog(assignment, jwt, "PROMPT_DISABLED", before, null);

        log.info("Admin {} disabled custom prompt for assignment {}", adminName, id);

        return ResponseEntity.ok(toAssignmentConfigDto(assignment));
    }

    /**
     * GET /api/admin/ai-grading/assignments/{id}/audit
     * Lấy audit log cho assignment (phân trang)
     */
    @GetMapping("/assignments/{id}/audit")
    public ResponseEntity<Page<PromptAuditLogDTO>> getAuditLog(
            @PathVariable("id") Long id,
            @PageableDefault(size = 20) Pageable pageable) {

        // Verify assignment exists
        if (!assignmentRepository.existsById(id)) {
            throw new NotFoundException("ASSIGNMENT_NOT_FOUND");
        }

        Page<AssignmentPromptAuditLog> logs = auditLogRepository
                .findByAssignmentIdOrderByCreatedAtDesc(id, pageable);

        Page<PromptAuditLogDTO> dtos = logs.map(log -> new PromptAuditLogDTO(
                log.getId(),
                log.getAssignment().getId(),
                log.getAssignment().getTitle(),
                log.getAdminId(),
                log.getAdminName(),
                log.getAction(),
                log.getBeforeValue(),
                log.getAfterValue(),
                log.getCreatedAt()
        ));

        return ResponseEntity.ok(dtos);
    }

    /**
     * POST /api/admin/ai-grading/audit-log
     * Ghi 1 audit entry cho assignment
     */
    @PostMapping("/audit-log")
    public ResponseEntity<PromptAuditLogDTO> createAuditLog(
            @Valid @RequestBody CreateAuditLogRequestDTO body,
            @AuthenticationPrincipal Jwt jwt) {

        Assignment assignment = assignmentRepository.findById(body.assignmentId())
                .orElseThrow(() -> new NotFoundException("ASSIGNMENT_NOT_FOUND"));

        AssignmentPromptAuditLog logEntry = AssignmentPromptAuditLog.builder()
                .assignment(assignment)
                .adminId(extractUserId(jwt))
                .adminName(getAdminName(jwt))
                .action(body.action())
                .beforeValue(body.beforeValue())
                .afterValue(body.afterValue())
                .createdAt(Instant.now())
                .build();

        AssignmentPromptAuditLog saved = auditLogRepository.save(logEntry);

        PromptAuditLogDTO dto = new PromptAuditLogDTO(
                saved.getId(),
                saved.getAssignment().getId(),
                saved.getAssignment().getTitle(),
                saved.getAdminId(),
                saved.getAdminName(),
                saved.getAction(),
                saved.getBeforeValue(),
                saved.getAfterValue(),
                saved.getCreatedAt()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    // =====================================================
    // PRIVATE HELPER METHODS
    // =====================================================

    private void saveAuditLog(Assignment assignment, Jwt jwt, String action, String beforeValue, String afterValue) {
        AssignmentPromptAuditLog logEntry = AssignmentPromptAuditLog.builder()
                .assignment(assignment)
                .adminId(extractUserId(jwt))
                .adminName(getAdminName(jwt))
                .action(action)
                .beforeValue(beforeValue)
                .afterValue(afterValue)
                .createdAt(Instant.now())
                .build();
        auditLogRepository.save(logEntry);
    }

    private String getAdminName(Jwt jwt) {
        String name = jwt.getClaimAsString("name");
        if (name != null && !name.isBlank()) return name;
        String fullName = jwt.getClaimAsString("fullName");
        if (fullName != null && !fullName.isBlank()) return fullName;
        Long userId = extractUserId(jwt);
        return userRepository.findById(userId)
                .map(User::getFullName)
                .orElse("Admin #" + userId);
    }

    private Sort buildSort(String sortBy, Sort.Direction direction) {
        return switch (sortBy) {
            case "confidence", "aiConfidence" -> Sort.by(direction, "aiConfidence");
            case "submittedAt" -> Sort.by(direction, "submittedAt");
            case "mentorScore", "actualScore" -> Sort.by(direction, "score");
            case "scoreDelta" -> JpaSort.unsafe(direction, "ABS(COALESCE(aiScore, 0) - COALESCE(score, 0))");
            default -> Sort.by(direction, "aiGradedAt");
        };
    }

    private AdminAiAssignmentConfigDTO toAssignmentConfigDto(Assignment assignment) {
        String prompt = assignment.getAiGradingPrompt();
        boolean hasCustomPrompt = prompt != null && !prompt.trim().isEmpty();
        Double averageScoreDelta = aiRepository.findAverageScoreDeltaByAssignmentId(assignment.getId());
        return new AdminAiAssignmentConfigDTO(
                assignment.getId(),
                assignment.getTitle(),
                assignment.getModule().getCourse().getTitle(),
                assignment.getModule().getTitle(),
                assignment.getModule().getCourse().getAuthor() != null ? assignment.getModule().getCourse().getAuthor().getId() : null,
                assignment.getModule().getCourse().getAuthor() != null ? assignment.getModule().getCourse().getAuthor().getFullName() : null,
                assignment.getSubmissionType() != null ? assignment.getSubmissionType().name() : null,
                assignment.getMaxScore(),
                assignment.getAiGradingEnabled(),
                assignment.getTrustAiEnabled(),
                assignment.getGradingStyle(),
                hasCustomPrompt,
                hasCustomPrompt ? prompt.trim().length() : 0,
                hasCustomPrompt ? buildPromptPreview(prompt) : null,
                aiRepository.countByAssignmentIdAndIsAiGradedTrue(assignment.getId()),
                aiRepository.countByAssignmentIdAndIsAiGradedTrueAndMentorConfirmedNull(assignment.getId()),
                aiRepository.countByAssignmentIdAndIsAiGradedTrueAndDisputeFlagTrue(assignment.getId()),
                aiRepository.countByAssignmentIdAndIsAiGradedTrueAndAiConfidenceLessThan(assignment.getId(), LOW_CONFIDENCE_THRESHOLD),
                averageScoreDelta != null ? averageScoreDelta : 0d
        );
    }

    private AiSubmissionDTO toDto(AssignmentSubmission sub, boolean includeDetail) {
        Assignment a = sub.getAssignment();
        List<CriteriaScoreDTO> criteriaScores = includeDetail
                ? criteriaScoreRepository.findBySubmissionId(sub.getId()).stream()
                        .map(score -> new CriteriaScoreDTO(
                                score.getId(),
                                score.getCriteria().getId(),
                                score.getCriteria().getName(),
                                score.getScore(),
                                score.getCriteria().getMaxPoints(),
                                score.getCriteria().getPassingPoints(),
                                score.getScore().compareTo(score.getCriteria().getPassingPoints()) >= 0,
                                score.getFeedback()))
                        .toList()
                : null;

        int passedCriteriaCount = criteriaScores == null
                ? 0
                : (int) criteriaScores.stream().filter(cs -> Boolean.TRUE.equals(cs.getPassed())).count();
        int failedCriteriaCount = criteriaScores == null
                ? 0
                : (int) criteriaScores.stream().filter(cs -> Boolean.FALSE.equals(cs.getPassed())).count();

        return new AiSubmissionDTO(
                sub.getId(),
                a.getId(),
                a.getTitle(),
                sub.getUser().getId(),
                sub.getUser().getFullName(),
                sub.getGradedBy() != null ? sub.getGradedBy().getId() : null,
                sub.getGradedBy() != null ? sub.getGradedBy().getFullName() : null,
                sub.getAiScore(),
                sub.getScore(),
                sub.getAiConfidence(),
                sub.getMentorConfirmed(),
                sub.getIsPassed(),
                sub.getDisputeFlag(),
                sub.getAiGradedAt(),
                sub.getGradedAt(),
                sub.getSubmittedAt(),
                a.getModule().getCourse().getTitle(),
                a.getModule().getTitle(),
                a.getSubmissionType().name(),
                a.getMaxScore(),
                sub.getAiGradeAttemptCount(),
                includeDetail ? sub.getAiFeedback() : null,
                includeDetail ? sub.getFeedback() : null,
                includeDetail ? sub.getDisputeReason() : null,
                sub.getAiScore() != null && sub.getScore() != null
                        ? sub.getAiScore().subtract(sub.getScore()).abs()
                        : null,
                includeDetail ? passedCriteriaCount : null,
                includeDetail ? failedCriteriaCount : null,
                criteriaScores
        );
    }

    private boolean computeIsPassed(Assignment assignment, List<CriteriaScoreDTO> criteriaScores, BigDecimal totalScore) {
        if (criteriaScores == null || criteriaScores.isEmpty()) {
            BigDecimal threshold = assignment.getPassingScore();
            if (threshold == null) {
                threshold = assignment.getMaxScore().multiply(BigDecimal.valueOf(0.7));
            }
            return totalScore.compareTo(threshold) >= 0;
        }

        for (CriteriaScoreDTO cs : criteriaScores) {
            if (Boolean.FALSE.equals(cs.getPassed())) {
                if (cs.getCriteriaId() != null) {
                    var criterion = assignment.getCriteria().stream()
                            .filter(c -> c.getId().equals(cs.getCriteriaId()))
                            .findFirst().orElse(null);
                    if (criterion != null && criterion.isRequired()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private Long extractUserId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }

    private String buildPromptPreview(String prompt) {
        String normalized = prompt == null ? "" : prompt.trim().replaceAll("\\s+", " ");
        return normalized.length() <= 140 ? normalized : normalized.substring(0, 140) + "...";
    }

    private long countRiskyTrustAssignments() {
        return assignmentRepository.findAiGradingAssignments(null, true, null, null, Pageable.unpaged())
                .stream()
                .filter(assignment ->
                        aiRepository.countByAssignmentIdAndIsAiGradedTrueAndDisputeFlagTrue(assignment.getId()) > 0
                                || aiRepository.countByAssignmentIdAndIsAiGradedTrueAndAiConfidenceLessThan(assignment.getId(), LOW_CONFIDENCE_THRESHOLD) > 0
                                || (aiRepository.findAverageScoreDeltaByAssignmentId(assignment.getId()) != null
                                    && aiRepository.findAverageScoreDeltaByAssignmentId(assignment.getId()) >= 2d))
                .count();
    }
}
