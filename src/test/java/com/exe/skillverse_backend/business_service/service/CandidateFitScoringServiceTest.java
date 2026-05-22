package com.exe.skillverse_backend.business_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.business_service.dto.request.CandidateSearchRequest;
import com.exe.skillverse_backend.business_service.entity.JobPosting;
import com.exe.skillverse_backend.portfolio_service.entity.ExternalCertificate;
import com.exe.skillverse_backend.portfolio_service.entity.GeneratedCV;
import com.exe.skillverse_backend.portfolio_service.entity.PortfolioExtendedProfile;
import com.exe.skillverse_backend.portfolio_service.entity.PortfolioProject;
import com.exe.skillverse_backend.portfolio_service.entity.UserVerifiedSkill;
import com.exe.skillverse_backend.portfolio_service.repository.ExternalCertificateRepository;
import com.exe.skillverse_backend.portfolio_service.repository.GeneratedCVRepository;
import com.exe.skillverse_backend.portfolio_service.repository.PortfolioProjectRepository;
import com.exe.skillverse_backend.portfolio_service.repository.UserVerifiedSkillRepository;
import com.exe.skillverse_backend.portfolio_service.service.PortfolioService;
import com.exe.skillverse_backend.student_skill_verification.entity.StudentSkillVerificationRequest;
import com.exe.skillverse_backend.student_skill_verification.entity.StudentVerificationStatus;
import com.exe.skillverse_backend.student_skill_verification.repository.StudentSkillVerificationRequestRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CandidateFitScoringServiceTest {

    @Mock
    private PortfolioProjectRepository projectRepository;
    @Mock
    private ExternalCertificateRepository certificateRepository;
    @Mock
    private UserVerifiedSkillRepository verifiedSkillRepository;
    @Mock
    private StudentSkillVerificationRequestRepository studentVerificationRepository;
    @Mock
    private GeneratedCVRepository cvRepository;
    @Mock
    private PortfolioService portfolioService;

    private CandidateFitScoringService service;

    @BeforeEach
    void setUp() {
        service = new CandidateFitScoringService(
                new ObjectMapper(),
                projectRepository,
                certificateRepository,
                verifiedSkillRepository,
                studentVerificationRepository,
                cvRepository,
                portfolioService
        );

        lenient().when(projectRepository.findByUserIdOrderByCompletionDateDesc(anyLong())).thenReturn(List.of());
        lenient().when(certificateRepository.findByUserIdOrderByIssueDateDesc(anyLong())).thenReturn(List.of());
        lenient().when(verifiedSkillRepository.findByUserIdOrderByVerifiedAtDesc(anyLong())).thenReturn(List.of());
        lenient().when(studentVerificationRepository.findApprovedByUserId(anyLong())).thenReturn(List.of());
        lenient().when(cvRepository.findByUserIdAndIsActiveTrue(anyLong())).thenReturn(Optional.empty());
        lenient().when(portfolioService.getPublicCompletedMissions(anyLong())).thenReturn(List.of());
    }

    @Test
    void declaredOnlySkillDoesNotCountAsVerifiedMatch() {
        var result = service.score(profile("[\"React\"]"), job("React", "Junior"), null, CandidateSearchRequest.builder().build());

        assertThat(result.getAnalysis().getRequiredSkillSignals()).hasSize(1);
        assertThat(result.getAnalysis().getRequiredSkillSignals().get(0).getStatus()).isEqualTo("DECLARED_ONLY");
        assertThat(result.getAnalysis().getVerifiedSkillMatchPercent()).isEqualTo(0.0);
        assertThat(result.getMatchedSkills()).isEmpty();
        assertThat(result.getUnmatchedSkills()).containsExactly("React");
    }

    @Test
    void mentorVerifiedSkillCountsAsFullVerifiedMatch() {
        when(verifiedSkillRepository.findByUserIdOrderByVerifiedAtDesc(10L)).thenReturn(List.of(
                UserVerifiedSkill.builder().userId(10L).skillName("REACT").verifiedByMentorId(99L).build()
        ));

        var result = service.score(profile("[\"React\"]"), job("React", "Junior"), null, CandidateSearchRequest.builder().build());

        assertThat(result.getAnalysis().getRequiredSkillSignals().get(0).getStatus()).isEqualTo("VERIFIED");
        assertThat(result.getAnalysis().getVerifiedSkillMatchPercent()).isEqualTo(1.0);
        assertThat(result.getMatchedSkills()).containsExactly("React");
    }

    @Test
    void adminApprovedSkillCountsAsFullVerifiedMatch() {
        User user = User.builder().id(10L).email("candidate@example.com").build();
        when(studentVerificationRepository.findApprovedByUserId(10L)).thenReturn(List.of(
                StudentSkillVerificationRequest.builder()
                        .user(user)
                        .skillName("React")
                        .status(StudentVerificationStatus.APPROVED)
                        .build()
        ));

        var result = service.score(profile("[\"React\"]"), job("React", "Junior"), null, CandidateSearchRequest.builder().build());

        assertThat(result.getAnalysis().getRequiredSkillSignals().get(0).getStatus()).isEqualTo("VERIFIED");
        assertThat(result.getAnalysis().getVerifiedSkillMatchPercent()).isEqualTo(1.0);
    }

    @Test
    void cvDeclaredSkillIsDeclaredOnlyAndDoesNotCountAsEvidence() {
        when(cvRepository.findByUserIdAndIsActiveTrue(10L)).thenReturn(Optional.of(
                GeneratedCV.builder()
                        .cvJson("{\"skills\":[{\"category\":\"Frontend\",\"skills\":[\"React\"]}]}")
                        .version(1)
                        .build()
        ));

        var result = service.score(profile("[]"), job("React", "Junior"), null, CandidateSearchRequest.builder().build());

        assertThat(result.getAnalysis().getRequiredSkillSignals().get(0).getStatus()).isEqualTo("DECLARED_ONLY");
        assertThat(result.getAnalysis().getEvidenceBackedSkillPercent()).isEqualTo(0.0);
    }

    @Test
    void portfolioProjectEvidenceCountsAsEvidenceBackedOnly() {
        when(projectRepository.findByUserIdOrderByCompletionDateDesc(10L)).thenReturn(List.of(
                PortfolioProject.builder()
                        .title("React dashboard")
                        .description("Built a React analytics dashboard")
                        .projectType(PortfolioProject.ProjectType.PERSONAL)
                        .tools(List.of("React"))
                        .build()
        ));

        var result = service.score(profile("[]"), job("React", "Junior"), null, CandidateSearchRequest.builder().build());

        assertThat(result.getAnalysis().getRequiredSkillSignals().get(0).getStatus()).isEqualTo("EVIDENCE_BACKED");
        assertThat(result.getAnalysis().getVerifiedSkillMatchPercent()).isEqualTo(0.0);
        assertThat(result.getAnalysis().getEvidenceBackedSkillPercent()).isEqualTo(1.0);
        assertThat(result.getMatchedSkills()).containsExactly("React");
    }

    @Test
    void projectUrlDoesNotCountAsEvidence() {
        when(projectRepository.findByUserIdOrderByCompletionDateDesc(10L)).thenReturn(List.of(
                PortfolioProject.builder()
                        .title("Dashboard")
                        .description("Analytics app")
                        .projectUrl("https://example.com/react")
                        .githubUrl("https://github.com/example/react")
                        .clientName("React Corp")
                        .projectType(PortfolioProject.ProjectType.PERSONAL)
                        .tools(List.of())
                        .build()
        ));

        var result = service.score(profile("[]"), job("React", "Junior"), null, CandidateSearchRequest.builder().build());

        assertThat(result.getAnalysis().getRequiredSkillSignals().get(0).getStatus()).isEqualTo("MISSING");
        assertThat(result.getAnalysis().getEvidenceBackedSkillPercent()).isEqualTo(0.0);
    }

    @Test
    void projectDescriptionOnlyCountsAsPossibleEvidence() {
        when(projectRepository.findByUserIdOrderByCompletionDateDesc(10L)).thenReturn(List.of(
                PortfolioProject.builder()
                        .title("Dashboard")
                        .description("Built screens with React")
                        .projectType(PortfolioProject.ProjectType.PERSONAL)
                        .tools(List.of())
                        .build()
        ));

        var result = service.score(profile("[]"), job("React", "Junior"), null, CandidateSearchRequest.builder().build());

        assertThat(result.getAnalysis().getRequiredSkillSignals().get(0).getStatus()).isEqualTo("POSSIBLE_EVIDENCE");
        assertThat(result.getAnalysis().getEvidenceBackedSkillPercent()).isEqualTo(0.0);
    }

    @Test
    void requiredSkillPercentagesAreSplitBySignalType() {
        when(verifiedSkillRepository.findByUserIdOrderByVerifiedAtDesc(10L)).thenReturn(List.of(
                UserVerifiedSkill.builder().userId(10L).skillName("React").build(),
                UserVerifiedSkill.builder().userId(10L).skillName("TypeScript").build()
        ));
        when(projectRepository.findByUserIdOrderByCompletionDateDesc(10L)).thenReturn(List.of(
                PortfolioProject.builder()
                        .title("API")
                        .projectType(PortfolioProject.ProjectType.PERSONAL)
                        .tools(List.of("Spring Boot"))
                        .build()
        ));
        JobPosting job = JobPosting.builder()
                .id(20L)
                .title("Full Stack Developer")
                .description("Build products")
                .primarySkill("React")
                .requiredSkills("[\"React\",\"TypeScript\",\"Spring Boot\",\"Docker\",\"AWS\"]")
                .experienceLevel("Junior")
                .isRemote(true)
                .build();

        var result = service.score(profile("[\"Docker\"]"), job, null, CandidateSearchRequest.builder().build());

        assertThat(result.getAnalysis().getVerifiedSkillMatchPercent()).isEqualTo(0.4);
        assertThat(result.getAnalysis().getEvidenceBackedSkillPercent()).isEqualTo(0.2);
        assertThat(result.getAnalysis().getDeclaredOnlySkillPercent()).isEqualTo(0.2);
        assertThat(result.getAnalysis().getMissingSkillPercent()).isEqualTo(0.2);
    }

    @Test
    void fresherStrictFlagsJuniorCandidateAsOverqualified() {
        PortfolioExtendedProfile profile = profile("[\"React\"]");
        profile.setProfessionalTitle("Junior Frontend Developer");
        profile.setYearsOfExperience(2);

        var result = service.score(profile, job("React", "Fresher"), null, CandidateSearchRequest.builder().build());

        assertThat(result.getAnalysis().getRequiredSeniority()).isEqualTo("FRESHER");
        assertThat(result.getAnalysis().getInferredSeniority()).isEqualTo("JUNIOR");
        assertThat(result.getAnalysis().getSeniorityPass()).isFalse();
        assertThat(result.getAnalysis().getOverqualified()).isTrue();
        assertThat(result.getAnalysis().getSeniorityDecision()).isEqualTo("FAIL_OVERQUALIFIED");
        assertThat(result.getAnalysis().getRiskFlags()).anyMatch(flag -> flag.contains("Fresher"));
    }

    @Test
    void fresherJobWithMissingSeniorityNeedsReview() {
        PortfolioExtendedProfile profile = profile("[\"React\"]");
        profile.setProfessionalTitle(null);
        profile.setYearsOfExperience(null);
        profile.setWorkExperiences(null);

        var result = service.score(profile, job("React", "Fresher"), null, CandidateSearchRequest.builder().build());

        assertThat(result.getAnalysis().getSeniorityDecision()).isEqualTo("NEEDS_REVIEW");
        assertThat(result.getAnalysis().getSeniorityPass()).isFalse();
    }

    @Test
    void seniorJobWithJuniorCandidateFailsSeniorityGate() {
        PortfolioExtendedProfile profile = profile("[\"React\"]");
        profile.setProfessionalTitle("Junior Frontend Developer");
        profile.setYearsOfExperience(2);

        var result = service.score(profile, job("React", "Senior"), null, CandidateSearchRequest.builder().build());

        assertThat(result.getAnalysis().getSeniorityDecision()).isEqualTo("FAIL_UNDERQUALIFIED");
        assertThat(result.getAnalysis().getSeniorityPass()).isFalse();
    }

    @Test
    void primarySkillWithoutProofCapsOverallScoreAndVerdict() {
        PortfolioExtendedProfile profile = profile("[\"React\"]");
        profile.setProfessionalTitle("Junior Frontend Developer");
        profile.setYearsOfExperience(2);
        profile.setTotalProjects(10);
        profile.setTotalCertificates(10);

        var result = service.score(profile, job("React", "Junior"), null, CandidateSearchRequest.builder().build());

        assertThat(result.getOverallScore()).isLessThanOrEqualTo(0.72);
        assertThat(result.getAnalysis().getFitVerdict()).isEqualTo("MISSING_CRITICAL_SKILLS");
    }

    @Test
    void primarySkillIsDedupedAgainstRequiredSkillsCaseInsensitively() {
        JobPosting job = JobPosting.builder()
                .id(20L)
                .title("Frontend Developer")
                .description("Build frontend products")
                .primarySkill("React")
                .requiredSkills("[\"react\",\"node.js\"]")
                .experienceLevel("Junior")
                .isRemote(true)
                .build();

        var result = service.score(profile("[]"), job, null, CandidateSearchRequest.builder().build());

        assertThat(result.getAnalysis().getRequiredSkillSignals())
                .extracting(signal -> signal.getSkill().toLowerCase())
                .containsExactly("react", "node.js");
        assertThat(result.getAnalysis().getRequiredSkillSignals().get(0).getPrimary()).isTrue();
    }

    @Test
    void businessMeaningUsesVietnameseCopy() {
        var result = service.score(profile("[\"React\"]"), job("React", "Junior"), null, CandidateSearchRequest.builder().build());

        assertThat(result.getAnalysis().getRequiredSkillSignals().get(0).getBusinessMeaning())
                .contains("ứng viên tự khai")
                .contains("chưa đủ để chứng minh năng lực");
    }

    private PortfolioExtendedProfile profile(String topSkills) {
        User user = User.builder().id(10L).email("candidate@example.com").build();
        return PortfolioExtendedProfile.builder()
                .userId(10L)
                .user(user)
                .fullName("Candidate")
                .professionalTitle("Frontend Developer")
                .topSkills(topSkills)
                .yearsOfExperience(1)
                .allowJobOffers(true)
                .build();
    }

    private JobPosting job(String primarySkill, String experienceLevel) {
        return JobPosting.builder()
                .id(20L)
                .title("Frontend Developer")
                .description("Build frontend products")
                .primarySkill(primarySkill)
                .requiredSkills("[\"" + primarySkill + "\"]")
                .experienceLevel(experienceLevel)
                .isRemote(true)
                .build();
    }
}
