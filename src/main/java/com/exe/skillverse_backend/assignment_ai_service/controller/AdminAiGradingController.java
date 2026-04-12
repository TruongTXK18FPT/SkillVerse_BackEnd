package com.exe.skillverse_backend.assignment_ai_service.controller;

import com.exe.skillverse_backend.assignment_ai_service.dto.AiGradingStatsDTO;
import com.exe.skillverse_backend.assignment_ai_service.dto.AiSubmissionDTO;
import com.exe.skillverse_backend.assignment_ai_service.dto.AdminAiAssignmentConfigDTO;
import com.exe.skillverse_backend.assignment_ai_service.dto.AdminAiGovernanceStatsDTO;
import com.exe.skillverse_backend.assignment_ai_service.repository.AssignmentSubmissionAiRepository;
import com.exe.skillverse_backend.course_service.entity.Assignment;
import com.exe.skillverse_backend.course_service.entity.AssignmentSubmission;
import com.exe.skillverse_backend.course_service.entity.SubmissionCriteriaScore;
import com.exe.skillverse_backend.course_service.repository.AssignmentRepository;
import com.exe.skillverse_backend.course_service.repository.AssignmentSubmissionRepository;
import com.exe.skillverse_backend.course_service.repository.SubmissionCriteriaScoreRepository;
import com.exe.skillverse_backend.course_service.service.CourseLearningProgressService;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.CriteriaScoreDTO;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
