package com.exe.skillverse_backend.journey_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.journey_service.entity.Journey;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.JourneyCompletionReport;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.JourneyOutputAssessment;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.RoadmapNodeAssignment;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.RoadmapNodeSubmission;
import com.exe.skillverse_backend.journey_service.node_mentoring.repository.JourneyCompletionReportRepository;
import com.exe.skillverse_backend.journey_service.node_mentoring.repository.JourneyOutputAssessmentRepository;
import com.exe.skillverse_backend.journey_service.node_mentoring.repository.RoadmapNodeAssignmentRepository;
import com.exe.skillverse_backend.journey_service.node_mentoring.repository.RoadmapNodeSubmissionRepository;
import com.exe.skillverse_backend.journey_service.repository.JourneyRepository;
import com.exe.skillverse_backend.journey_service.dto.response.JourneyVerificationDetailResponse;
import com.exe.skillverse_backend.journey_service.dto.response.JourneyVerificationDetailResponse.NodeSubmissionDetail;
import com.exe.skillverse_backend.journey_service.dto.response.JourneyVerificationDetailResponse.OutputAssessmentDetail;
import com.exe.skillverse_backend.journey_service.service.PublicJourneyVerificationService;
import com.exe.skillverse_backend.portfolio_service.repository.PortfolioExtendedProfileRepository;
import com.exe.skillverse_backend.shared.exception.ApiException;
import com.exe.skillverse_backend.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PublicJourneyVerificationServiceImpl implements PublicJourneyVerificationService {

    private final JourneyRepository journeyRepository;
    private final JourneyCompletionReportRepository completionReportRepository;
    private final RoadmapNodeSubmissionRepository submissionRepository;
    private final RoadmapNodeAssignmentRepository assignmentRepository;
    private final JourneyOutputAssessmentRepository outputAssessmentRepository;
    private final UserRepository userRepository;
    private final PortfolioExtendedProfileRepository extendedProfileRepository;

    @Override
    @Transactional(readOnly = true)
    public JourneyVerificationDetailResponse getVerificationDetails(Long journeyId) {
        Journey journey = journeyRepository.findById(journeyId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Journey not found: " + journeyId));

        if (journey.getStatus() != Journey.JourneyStatus.COMPLETED_VERIFIED 
                && journey.getStatus() != Journey.JourneyStatus.COMPLETED_UNVERIFIED) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Only completed journeys can be viewed publicly");
        }

        boolean isCompletedUnverified = journey.getStatus() == Journey.JourneyStatus.COMPLETED_UNVERIFIED;

        JourneyCompletionReport report = completionReportRepository.findFirstByJourneyIdOrderByConfirmedAtDesc(journeyId)
                .filter(r -> r.getGateDecision() == JourneyCompletionReport.GateDecision.PASS)
                .orElse(null);

        User mentor = null;
        if (report != null && report.getMentorId() != null) {
            mentor = userRepository.findById(report.getMentorId()).orElse(null);
        }

        List<RoadmapNodeSubmission> submissions = submissionRepository.findByJourneyId(journeyId)
                .stream()
                .filter(s -> isCompletedUnverified 
                        || s.getVerificationStatus() == RoadmapNodeSubmission.VerificationStatus.VERIFIED 
                        || s.getVerificationStatus() == RoadmapNodeSubmission.VerificationStatus.APPROVED)
                .collect(Collectors.toList());

        List<NodeSubmissionDetail> nodeDetails = submissions.stream().map(sub -> {
            String nodeTitle = "Node " + sub.getNodeId();
            if (sub.getAssignmentId() != null) {
                Optional<RoadmapNodeAssignment> assignmentOpt = assignmentRepository.findById(sub.getAssignmentId());
                if (assignmentOpt.isPresent() && assignmentOpt.get().getTitle() != null) {
                    nodeTitle = assignmentOpt.get().getTitle();
                }
            }
            return NodeSubmissionDetail.builder()
                    .nodeId(sub.getNodeId())
                    .nodeTitle(nodeTitle)
                    .submissionText(sub.getSubmissionText())
                    .evidenceUrl(sub.getEvidenceUrl())
                    .mentorFeedback(sub.getMentorFeedback())
                    .submittedAt(sub.getSubmittedAt())
                    .verifiedAt(sub.getUpdatedAt())
                    .build();
        }).collect(Collectors.toList());

        JourneyOutputAssessment assessment = outputAssessmentRepository.findFirstByJourneyIdOrderBySubmittedAtDesc(journeyId)
                .orElse(null);

        OutputAssessmentDetail assessmentDetail = null;
        if (assessment != null && (isCompletedUnverified 
                || assessment.getAssessmentStatus() == JourneyOutputAssessment.AssessmentStatus.APPROVED)) {
            assessmentDetail = OutputAssessmentDetail.builder()
                    .submissionText(assessment.getSubmissionText())
                    .evidenceUrl(assessment.getEvidenceUrl())
                    .feedback(assessment.getFeedback())
                    .score(assessment.getScore())
                    .assessmentStatus(assessment.getAssessmentStatus().name())
                    .assessedAt(assessment.getAssessedAt())
                    .build();
        }

        // Look up mentor's portfolio slug
        String mentorSlug = null;
        if (mentor != null) {
            mentorSlug = extendedProfileRepository.findByUserId(mentor.getId())
                    .map(p -> p.getCustomUrlSlug())
                    .orElse(null);
        }

        return JourneyVerificationDetailResponse.builder()
                .journeyId(journey.getId())
                .skillName(journey.getSkillName())
                .completedAt(journey.getCompletedAt())
                .mentorId(mentor != null ? mentor.getId() : null)
                .mentorName(mentor != null ? mentor.getFullName() : null)
                .mentorAvatarUrl(mentor != null ? mentor.getAvatarUrl() : null)
                .mentorTitle(null)
                .mentorProfileSlug(mentorSlug)
                .gateCompletionNote(report != null ? report.getCompletionNote() : null)
                .nodes(nodeDetails)
                .finalAssessment(assessmentDetail)
                .build();
    }
}
