package com.exe.skillverse_backend.roadmap_package_service.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.exe.skillverse_backend.ai_service.repository.RoadmapSessionRepository;
import com.exe.skillverse_backend.ai_service.repository.UserRoadmapProgressRepository;
import com.exe.skillverse_backend.auth_service.entity.PrimaryRole;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.career_taxonomy_service.entity.Domain;
import com.exe.skillverse_backend.career_taxonomy_service.entity.JobPosition;
import com.exe.skillverse_backend.career_taxonomy_service.entity.JobPositionTrack;
import com.exe.skillverse_backend.career_taxonomy_service.entity.JobPositionTrackSkill;
import com.exe.skillverse_backend.career_taxonomy_service.enums.TaxonomyStatus;
import com.exe.skillverse_backend.career_taxonomy_service.repository.DomainRepository;
import com.exe.skillverse_backend.career_taxonomy_service.repository.JobPositionRepository;
import com.exe.skillverse_backend.career_taxonomy_service.repository.JobPositionTrackRepository;
import com.exe.skillverse_backend.career_taxonomy_service.repository.JobPositionTrackSkillRepository;
import com.exe.skillverse_backend.course_service.repository.CourseRepository;
import com.exe.skillverse_backend.journey_service.entity.Journey;
import com.exe.skillverse_backend.journey_service.node_mentoring.repository.RoadmapNodeAssignmentRepository;
import com.exe.skillverse_backend.journey_service.repository.JourneyRepository;
import com.exe.skillverse_backend.roadmap_package_service.dto.request.RoadmapTemplateActivityRequest;
import com.exe.skillverse_backend.roadmap_package_service.dto.request.RoadmapTemplateNodeGroupRequest;
import com.exe.skillverse_backend.roadmap_package_service.dto.request.RoadmapTemplateNodeGroupSkillRequest;
import com.exe.skillverse_backend.roadmap_package_service.dto.request.RoadmapTemplateRequest;
import com.exe.skillverse_backend.roadmap_package_service.dto.request.RoadmapTemplateSkillBlockRequest;
import com.exe.skillverse_backend.roadmap_package_service.dto.response.RoadmapTemplateAllocationPreviewResponse;
import com.exe.skillverse_backend.roadmap_package_service.dto.response.RoadmapTemplateCourseCandidateResponse;
import com.exe.skillverse_backend.roadmap_package_service.dto.response.RoadmapTemplateValidationResponse;
import com.exe.skillverse_backend.roadmap_package_service.entity.RoadmapTemplateCourseLinkPolicy;
import com.exe.skillverse_backend.roadmap_package_service.repository.RoadmapTemplateActivityRepository;
import com.exe.skillverse_backend.roadmap_package_service.repository.RoadmapTemplateCourseRepository;
import com.exe.skillverse_backend.roadmap_package_service.repository.RoadmapTemplateNodeRepository;
import com.exe.skillverse_backend.roadmap_package_service.repository.RoadmapTemplateRepository;
import com.exe.skillverse_backend.roadmap_package_service.repository.RoadmapTemplateSkillBlockRepository;
import com.exe.skillverse_backend.shared.repository.SkillRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoadmapTemplateServiceImplTest {

    private static final Long ADMIN_ID = 1L;

    @Mock private UserRepository userRepository;
    @Mock private DomainRepository domainRepository;
    @Mock private JobPositionRepository jobPositionRepository;
    @Mock private JobPositionTrackRepository jobPositionTrackRepository;
    @Mock private JobPositionTrackSkillRepository jobPositionTrackSkillRepository;
    @Mock private SkillRepository skillRepository;
    @Mock private RoadmapTemplateRepository templateRepository;
    @Mock private RoadmapTemplateNodeRepository nodeRepository;
    @Mock private RoadmapTemplateCourseRepository courseRepository;
    @Mock private RoadmapTemplateSkillBlockRepository skillBlockRepository;
    @Mock private RoadmapTemplateActivityRepository activityRepository;
    @Mock private CourseRepository systemCourseRepository;
    @Mock private JourneyRepository journeyRepository;
    @Mock private RoadmapSessionRepository roadmapSessionRepository;
    @Mock private RoadmapNodeAssignmentRepository assignmentRepository;
    @Mock private UserRoadmapProgressRepository progressRepository;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private RoadmapTemplateServiceImpl service;

    @BeforeEach
    void setUp() {
        when(userRepository.findById(ADMIN_ID))
                .thenReturn(Optional.of(User.builder().id(ADMIN_ID).primaryRole(PrimaryRole.ADMIN).build()));
    }

    @Test
    void previewAllocationUsesLargestRemainderAndRespectsOverride() {
        RoadmapTemplateRequest request = baseRequest(10);
        request.setSkillBlocks(List.of(
                skillBlock(101L, "Java Spring Boot", 50D, null, null, null),
                skillBlock(102L, "React", 30D, null, null, null),
                skillBlock(103L, "Docker", 20D, null, null, 2)
        ));

        RoadmapTemplateAllocationPreviewResponse preview = service.previewAllocation(ADMIN_ID, request);

        assertThat(preview.getValid()).isTrue();
        assertThat(preview.getAllocatedNodeCount()).isEqualTo(10);
        assertThat(preview.getItems())
                .extracting(RoadmapTemplateAllocationPreviewResponse.Item::getAllocatedNodes)
                .containsExactly(5, 3, 2);
    }

    @Test
    void previewAllocationBuildsNormalizedSkillBlocksFromTrackWhenRequestHasNone() {
        RoadmapTemplateRequest request = baseRequest(10);
        when(jobPositionTrackSkillRepository.findByTrackIdOrderBySortOrderAsc(30L))
                .thenReturn(List.of(
                        JobPositionTrackSkill.builder()
                                .skillId(101L)
                                .requirementType(com.exe.skillverse_backend.career_taxonomy_service.enums.RequirementType.REQUIRED)
                                .weight(10)
                                .sortOrder(1)
                                .build(),
                        JobPositionTrackSkill.builder()
                                .skillId(102L)
                                .requirementType(com.exe.skillverse_backend.career_taxonomy_service.enums.RequirementType.IMPORTANT)
                                .weight(10)
                                .sortOrder(2)
                                .build(),
                        JobPositionTrackSkill.builder()
                                .skillId(103L)
                                .requirementType(com.exe.skillverse_backend.career_taxonomy_service.enums.RequirementType.NICE_TO_HAVE)
                                .weight(10)
                                .sortOrder(3)
                                .build()
                ));
        when(skillRepository.findAllById(java.util.Set.of(101L, 102L, 103L))).thenReturn(List.of(
                com.exe.skillverse_backend.shared.entity.Skill.builder().id(101L).name("Java").canonicalKey("JAVA").build(),
                com.exe.skillverse_backend.shared.entity.Skill.builder().id(102L).name("REST").canonicalKey("REST").build(),
                com.exe.skillverse_backend.shared.entity.Skill.builder().id(103L).name("Git").canonicalKey("GIT").build()
        ));

        RoadmapTemplateAllocationPreviewResponse preview = service.previewAllocation(ADMIN_ID, request);

        assertThat(preview.getItems()).hasSize(3);
        assertThat(preview.getItems()).extracting(RoadmapTemplateAllocationPreviewResponse.Item::getWeightPercent)
                .containsExactly(50D, 33.33D, 16.67D);
        assertThat(preview.getItems()).extracting(RoadmapTemplateAllocationPreviewResponse.Item::getEffectiveWeight)
                .containsExactly(30D, 20D, 10D);
        assertThat(preview.getItems()).extracting(RoadmapTemplateAllocationPreviewResponse.Item::getAllocatedNodes)
                .containsExactly(5, 3, 2);
    }

    @Test
    void validateTemplateReportsAllocationAndActivityErrors() {
        RoadmapTemplateRequest request = baseRequest(3);
        RoadmapTemplateSkillBlockRequest java = skillBlock(101L, "Java Spring Boot", 100D, null, null, 4);
        RoadmapTemplateActivityRequest activity = activity("REST API", "", "");
        activity.setMinLevel(null);
        java.setActivities(List.of(activity));
        request.setSkillBlocks(List.of(java));
        stubActiveTaxonomy(List.of(101L));

        RoadmapTemplateValidationResponse validation = service.validateTemplate(ADMIN_ID, request);

        assertThat(validation.getValid()).isFalse();
        assertThat(validation.getErrors())
                .anyMatch(error -> error.contains("Total module count must equal totalNodeCount"))
                .anyMatch(error -> error.contains("Every activity must define expectedOutput and rubric"))
                .anyMatch(error -> error.contains("Every activity must define minLevel"));
        assertThat(validation.getAllocation().getValid()).isTrue();
    }

    @Test
    void validateTemplateReportsInvalidActivityLevelBand() {
        RoadmapTemplateRequest request = baseRequest(3);
        RoadmapTemplateSkillBlockRequest java = skillBlock(101L, "Java Spring Boot", 100D, null, null, null);
        RoadmapTemplateActivityRequest activity = activity("REST API", "Build a REST API", "API passes contract tests");
        activity.setMinLevel(Journey.SkillLevel.ADVANCED);
        activity.setMaxLevel(Journey.SkillLevel.ELEMENTARY);
        java.setActivities(List.of(activity));
        request.setSkillBlocks(List.of(java));
        stubActiveTaxonomy(List.of(101L));

        RoadmapTemplateValidationResponse validation = service.validateTemplate(ADMIN_ID, request);

        assertThat(validation.getValid()).isFalse();
        assertThat(validation.getErrors())
                .anyMatch(error -> error.contains("Activity minLevel cannot be greater than maxLevel"));
    }

    @Test
    void validateTemplateBlocksWhenRequiredSkillIsNotCoveredByAnyModule() {
        RoadmapTemplateRequest request = baseRequest(1);
        RoadmapTemplateSkillBlockRequest java = skillBlock(101L, "Java Spring Boot", 100D, null, null, null);
        java.setActivities(List.of(activity("REST API & Backend Practices", "Build a REST API", "API passes contract tests")));
        request.setSkillBlocks(List.of(java));
        stubActiveTaxonomy(List.of(101L, 102L));

        RoadmapTemplateValidationResponse validation = service.validateTemplate(ADMIN_ID, request);

        assertThat(validation.getValid()).isFalse();
        assertThat(validation.getErrors())
                .anyMatch(error -> error.contains("Required skill is not covered by any module: 102"));
    }

    @Test
    void validateTemplateNormalizesSkillBlockWeightsAndWarnsForUncoveredImportantSkills() {
        RoadmapTemplateRequest request = baseRequest(1);
        request.setSkillBlocks(List.of(
                skillBlock(101L, "Java", 40D, null, null, null),
                skillBlock(102L, "REST", 40D, null, null, null),
                skillBlock(103L, "Testing", 40D, null, null, null)
        ));
        request.setNodeGroups(List.of(nodeGroup(101L)));

        when(domainRepository.findById(10L)).thenReturn(Optional.of(Domain.builder()
                .id(10L)
                .status(TaxonomyStatus.ACTIVE)
                .build()));
        when(jobPositionRepository.findById(20L)).thenReturn(Optional.of(JobPosition.builder()
                .id(20L)
                .domainId(10L)
                .status(TaxonomyStatus.ACTIVE)
                .build()));
        when(jobPositionTrackRepository.findById(30L)).thenReturn(Optional.of(JobPositionTrack.builder()
                .id(30L)
                .jobPositionId(20L)
                .status(TaxonomyStatus.ACTIVE)
                .build()));
        when(jobPositionTrackSkillRepository.findByTrackIdOrderBySortOrderAsc(30L))
                .thenReturn(List.of(
                        JobPositionTrackSkill.builder()
                                .skillId(101L)
                                .requirementType(com.exe.skillverse_backend.career_taxonomy_service.enums.RequirementType.REQUIRED)
                                .weight(10)
                                .sortOrder(1)
                                .build(),
                        JobPositionTrackSkill.builder()
                                .skillId(102L)
                                .requirementType(com.exe.skillverse_backend.career_taxonomy_service.enums.RequirementType.IMPORTANT)
                                .weight(10)
                                .sortOrder(2)
                                .build(),
                        JobPositionTrackSkill.builder()
                                .skillId(103L)
                                .requirementType(com.exe.skillverse_backend.career_taxonomy_service.enums.RequirementType.IMPORTANT)
                                .weight(5)
                                .sortOrder(3)
                                .build()
                ));

        RoadmapTemplateValidationResponse validation = service.validateTemplate(ADMIN_ID, request);

        assertThat(validation.getValid()).isTrue();
        assertThat(validation.getAllocation().getItems())
                .extracting(RoadmapTemplateAllocationPreviewResponse.Item::getWeightPercent)
                .containsExactly(50D, 33.33D, 16.67D);
        assertThat(validation.getWarnings())
                .anyMatch(warning -> warning.contains("Important skill is not covered by any module")
                        && warning.contains("102"))
                .anyMatch(warning -> warning.contains("Important skill is not covered by any module")
                        && warning.contains("103"));
    }

    @Test
    void getCourseCandidatesHybridMergesNewestAndPopularPublicRows() {
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        when(systemCourseRepository.findNewestPublicCourseCandidatesBySkill(101L, 3))
                .thenReturn(List.of(courseRow(1L, "Newest", createdAt, 5L), courseRow(2L, "Shared", createdAt, 8L)));
        when(systemCourseRepository.findPopularPublicCourseCandidatesBySkill(101L, 3))
                .thenReturn(List.of(courseRow(2L, "Shared", createdAt, 8L), courseRow(3L, "Popular", createdAt, 30L)));

        List<RoadmapTemplateCourseCandidateResponse> candidates = service.getCourseCandidates(
                ADMIN_ID, 101L, RoadmapTemplateCourseLinkPolicy.AUTO_HYBRID, 3);

        assertThat(candidates).extracting(RoadmapTemplateCourseCandidateResponse::getCourseId)
                .containsExactly(1L, 2L, 3L);
        assertThat(candidates).extracting(RoadmapTemplateCourseCandidateResponse::getEnrollmentCount)
                .containsExactly(5L, 8L, 30L);
    }

    private RoadmapTemplateRequest baseRequest(Integer totalNodeCount) {
        RoadmapTemplateRequest request = new RoadmapTemplateRequest();
        request.setDomainId(10L);
        request.setJobPositionId(20L);
        request.setJobPositionTrackId(30L);
        request.setTitle("Backend Engineer Template");
        request.setTotalNodeCount(totalNodeCount);
        return request;
    }

    private RoadmapTemplateSkillBlockRequest skillBlock(
            Long skillId, String skillName, Double weight, Integer min, Integer max, Integer override) {
        RoadmapTemplateSkillBlockRequest block = new RoadmapTemplateSkillBlockRequest();
        block.setSkillId(skillId);
        block.setSkillNameSnapshot(skillName);
        block.setWeightPercent(weight);
        block.setMinNodes(min);
        block.setMaxNodes(max);
        block.setNodeCountOverride(override);
        return block;
    }

    private RoadmapTemplateActivityRequest activity(String title, String expectedOutput, String rubric) {
        RoadmapTemplateActivityRequest activity = new RoadmapTemplateActivityRequest();
        activity.setTitle(title);
        activity.setExpectedOutput(expectedOutput);
        activity.setRubric(rubric);
        activity.setMinLevel(Journey.SkillLevel.BEGINNER);
        activity.setOrderIndex(0);
        return activity;
    }

    private RoadmapTemplateNodeGroupRequest nodeGroup(Long skillId) {
        RoadmapTemplateNodeGroupSkillRequest skill = new RoadmapTemplateNodeGroupSkillRequest();
        skill.setSkillId(skillId);
        skill.setRequirementType(com.exe.skillverse_backend.career_taxonomy_service.enums.RequirementType.REQUIRED);
        skill.setOrderIndex(1);

        RoadmapTemplateNodeGroupRequest group = new RoadmapTemplateNodeGroupRequest();
        group.setTitle("Core Module");
        group.setExpectedOutput("Build a working artifact");
        group.setCompletionCriteria("Artifact is submitted and reviewed");
        group.setOrderIndex(1);
        group.setSkills(List.of(skill));
        return group;
    }

    private void stubActiveTaxonomy(List<Long> allowedSkillIds) {
        when(domainRepository.findById(10L)).thenReturn(Optional.of(Domain.builder()
                .id(10L)
                .status(TaxonomyStatus.ACTIVE)
                .build()));
        when(jobPositionRepository.findById(20L)).thenReturn(Optional.of(JobPosition.builder()
                .id(20L)
                .domainId(10L)
                .status(TaxonomyStatus.ACTIVE)
                .build()));
        when(jobPositionTrackRepository.findById(30L)).thenReturn(Optional.of(JobPositionTrack.builder()
                .id(30L)
                .jobPositionId(20L)
                .status(TaxonomyStatus.ACTIVE)
                .build()));
        when(jobPositionTrackSkillRepository.findByTrackIdOrderBySortOrderAsc(30L))
                .thenReturn(allowedSkillIds.stream()
                        .map(skillId -> JobPositionTrackSkill.builder().skillId(skillId).build())
                        .toList());
    }

    private Object[] courseRow(Long id, String title, Instant createdAt, Long enrollmentCount) {
        return new Object[] {id, title, "JUNIOR", "Backend", Timestamp.from(createdAt), enrollmentCount};
    }
}
