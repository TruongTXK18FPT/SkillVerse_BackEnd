package com.exe.skillverse_backend.roadmap_package_service.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.exe.skillverse_backend.ai_service.entity.RoadmapSession;
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
import com.exe.skillverse_backend.journey_service.entity.TestResult;
import com.exe.skillverse_backend.journey_service.node_mentoring.repository.RoadmapNodeAssignmentRepository;
import com.exe.skillverse_backend.journey_service.repository.JourneyRepository;
import com.exe.skillverse_backend.roadmap_package_service.dto.request.RoadmapTemplateActivityRequest;
import com.exe.skillverse_backend.roadmap_package_service.dto.request.RoadmapTemplateNodeGroupRequest;
import com.exe.skillverse_backend.roadmap_package_service.dto.request.RoadmapTemplateNodeGroupSkillRequest;
import com.exe.skillverse_backend.roadmap_package_service.dto.request.RoadmapTemplateRequest;
import com.exe.skillverse_backend.roadmap_package_service.dto.request.RoadmapTemplateSkillBlockRequest;
import com.exe.skillverse_backend.roadmap_package_service.dto.response.RoadmapTemplateAllocationPreviewResponse;
import com.exe.skillverse_backend.roadmap_package_service.dto.response.RoadmapTemplateCourseCandidateResponse;
import com.exe.skillverse_backend.roadmap_package_service.dto.response.RoadmapTemplateNodeGroupResponse;
import com.exe.skillverse_backend.roadmap_package_service.dto.response.RoadmapTemplateResponse;
import com.exe.skillverse_backend.roadmap_package_service.dto.response.RoadmapTemplateValidationResponse;
import com.exe.skillverse_backend.roadmap_package_service.entity.RoadmapTemplate;
import com.exe.skillverse_backend.roadmap_package_service.entity.RoadmapTemplateCourseLinkPolicy;
import com.exe.skillverse_backend.roadmap_package_service.entity.RoadmapTemplateNodeGroup;
import com.exe.skillverse_backend.roadmap_package_service.entity.RoadmapTemplateNodeGroupSkill;
import com.exe.skillverse_backend.roadmap_package_service.entity.RoadmapTemplateStatus;
import com.exe.skillverse_backend.roadmap_package_service.repository.RoadmapTemplateActivityRepository;
import com.exe.skillverse_backend.roadmap_package_service.repository.RoadmapTemplateCourseRepository;
import com.exe.skillverse_backend.roadmap_package_service.repository.RoadmapTemplateNodeGroupRepository;
import com.exe.skillverse_backend.roadmap_package_service.repository.RoadmapTemplateNodeGroupSkillRepository;
import com.exe.skillverse_backend.roadmap_package_service.repository.RoadmapTemplateNodeRepository;
import com.exe.skillverse_backend.roadmap_package_service.repository.RoadmapTemplateRepository;
import com.exe.skillverse_backend.roadmap_package_service.repository.RoadmapTemplateSkillBlockRepository;
import com.exe.skillverse_backend.roadmap_package_service.service.RoadmapNodeAiEnrichmentService;
import com.exe.skillverse_backend.shared.entity.Skill;
import com.exe.skillverse_backend.shared.repository.SkillRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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
    @Mock private RoadmapTemplateNodeGroupRepository nodeGroupRepository;
    @Mock private RoadmapTemplateNodeGroupSkillRepository nodeGroupSkillRepository;
    @Mock private CourseRepository systemCourseRepository;
    @Mock private JourneyRepository journeyRepository;
    @Mock private RoadmapSessionRepository roadmapSessionRepository;
    @Mock private RoadmapNodeAssignmentRepository assignmentRepository;
    @Mock private UserRoadmapProgressRepository progressRepository;
    @Mock private ObjectMapper objectMapper;
    @Mock private RoadmapNodeAiEnrichmentService nodeAiEnrichmentService;

    @InjectMocks
    private RoadmapTemplateServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(userRepository.findById(ADMIN_ID))
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
    void createTemplateReturnsAiAndFinalAssignmentFields() {
        RoadmapTemplateRequest request = baseRequest(4);
        request.setAiEvidencePrompt("Review Java, Spring Boot, React, and Docker evidence first.");
        request.setFinalAssignmentInstructions("Build a fullstack task manager with React and Spring Boot.");
        request.setFinalAssignmentRubric("100 points: Java 15; Spring Boot 20; React 20; Docker 10.");
        stubActiveTaxonomy(List.of(101L));
        Skill javaSkill = Skill.builder().id(101L).name("Java").canonicalKey("JAVA").build();
        when(skillRepository.findAllById(java.util.Set.of(101L))).thenReturn(List.of(javaSkill));
        when(skillRepository.findById(101L)).thenReturn(Optional.of(javaSkill));
        when(templateRepository.save(any(RoadmapTemplate.class))).thenAnswer(invocation -> {
            RoadmapTemplate template = invocation.getArgument(0);
            template.setId(99L);
            return template;
        });
        when(skillBlockRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RoadmapTemplateResponse response = service.createTemplate(ADMIN_ID, request);

        assertThat(response.getAiEvidencePrompt()).isEqualTo(request.getAiEvidencePrompt());
        assertThat(response.getFinalAssignmentInstructions()).isEqualTo(request.getFinalAssignmentInstructions());
        assertThat(response.getFinalAssignmentRubric()).isEqualTo(request.getFinalAssignmentRubric());
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

    @Test
    void nodeGroupDtosPreservePinnedDocumentIdsForAdminTemplateContract() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String pinnedDocumentIds = "[12,18,21]";

        RoadmapTemplateNodeGroupRequest request = mapper.readValue("""
                {
                  "title": "Java Core Foundation",
                  "orderIndex": 1,
                  "skills": [],
                  "pinnedDocumentIds": "[12,18,21]"
                }
                """, RoadmapTemplateNodeGroupRequest.class);

        assertThat(request.getPinnedDocumentIds()).isEqualTo(pinnedDocumentIds);

        RoadmapTemplateNodeGroupResponse response = RoadmapTemplateNodeGroupResponse.builder()
                .title("Java Core Foundation")
                .orderIndex(1)
                .skills(List.of())
                .pinnedDocumentIds(pinnedDocumentIds)
                .build();

        assertThat(mapper.writeValueAsString(response)).contains("\"pinnedDocumentIds\":\"[12,18,21]\"");
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

    @Test
    void resolveSuggestedCourseIdsPreservesManualAndCapsAiSuggestions() {
        userRepository.findById(ADMIN_ID);

        Long skillId = 101L;
        List<Long> manualCourseIds = List.of(201L, 202L);

        // Scenario 1: MANUAL_ONLY policy
        List<Long> resultManualOnly = service.resolveSuggestedCourseIds(
                skillId, RoadmapTemplateCourseLinkPolicy.MANUAL_ONLY, 3, manualCourseIds);
        assertThat(resultManualOnly).containsExactly(201L, 202L);

        // Scenario 2: AUTO_HYBRID policy
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        when(systemCourseRepository.findNewestPublicCourseCandidatesBySkill(skillId, 3))
                .thenReturn(List.of(
                        courseRow(201L, "Duplicate Manual Course", createdAt, 5L),
                        courseRow(301L, "New Course 1", createdAt, 8L),
                        courseRow(302L, "New Course 2", createdAt, 12L)
                ));
        when(systemCourseRepository.findPopularPublicCourseCandidatesBySkill(skillId, 3))
                .thenReturn(List.of(
                        courseRow(302L, "New Course 2", createdAt, 12L),
                        courseRow(303L, "New Course 3", createdAt, 15L)
                ));

        // We ask for hybrid with autoCourseLimit = 3
        List<Long> resultHybrid = service.resolveSuggestedCourseIds(
                skillId, RoadmapTemplateCourseLinkPolicy.AUTO_HYBRID, 3, manualCourseIds);

        // manual courses must be preserved first: 201, 202
        // Then AI suggestions (301, 302, 303) are filtered (201 is manual, so ignored).
        // The remaining AI candidates are 301, 302, 303.
        // We must limit to exactly 2 optional AI suggestions, e.g., 301 and 302.
        // So the final list should be [201, 202, 301, 302].
        assertThat(resultHybrid).containsExactly(201L, 202L, 301L, 302L);
    }

    @Test
    void createRoadmapSessionFromNodeGroupsEnrichesFirstTwoSyncAndQueuesRemainingAsync() throws Exception {
        ObjectMapper realMapper = new ObjectMapper();
        ReflectionTestUtils.setField(service, "objectMapper", realMapper);
        ReflectionTestUtils.setField(service, "self", service);
        ReflectionTestUtils.setField(service, "roadmapEnrichmentTaskExecutor", (Executor) Runnable::run);
        ReflectionTestUtils.setField(service, "syncFirstNodes", 2);

        RoadmapBackgroundEnrichmentService backgroundEnrichmentService = mock(RoadmapBackgroundEnrichmentService.class);
        ReflectionTestUtils.setField(service, "backgroundEnrichmentService", backgroundEnrichmentService);

        User student = User.builder().id(99L).primaryRole(PrimaryRole.USER).build();
        Journey journey = Journey.builder()
                .id(10L)
                .user(student)
                .jobPositionTrackId(30L)
                .goal("PREPARE_JOB")
                .currentLevel(Journey.SkillLevel.INTERMEDIATE)
                .build();
        TestResult testResult = TestResult.builder()
                .evaluatedLevel(Journey.SkillLevel.INTERMEDIATE)
                .scorePercentage(75)
                .build();

        RoadmapTemplate template = RoadmapTemplate.builder()
                .id(50L)
                .jobPositionTrackId(30L)
                .title("Backend Java Track")
                .status(RoadmapTemplateStatus.PUBLISHED)
                .build();

        RoadmapTemplateNodeGroup g1 = RoadmapTemplateNodeGroup.builder()
                .id(1L).nodeKey("mod-1").title("Node 1").description("Blue 1").expectedOutput("Out 1").rubric("Rub 1").orderIndex(1).build();
        RoadmapTemplateNodeGroup g2 = RoadmapTemplateNodeGroup.builder()
                .id(2L).nodeKey("mod-2").title("Node 2").description("Blue 2").expectedOutput("Out 2").rubric("Rub 2").orderIndex(2).build();
        RoadmapTemplateNodeGroup g3 = RoadmapTemplateNodeGroup.builder()
                .id(3L).nodeKey("mod-3").title("Node 3").description("Blue 3").expectedOutput("Out 3").rubric("Rub 3")
                .lessonsJson("[{\"title\":\"L3\",\"description\":\"LD3\",\"learningObjective\":\"LO3\",\"estimatedMinutes\":30}]")
                .orderIndex(3).build();

        when(templateRepository.findFirstByJobPositionTrackIdAndStatusOrderByUpdatedAtDescCreatedAtDesc(
                30L, RoadmapTemplateStatus.PUBLISHED)).thenReturn(Optional.of(template));
        when(nodeGroupRepository.findByTemplateIdOrderByOrderIndexAscIdAsc(50L)).thenReturn(List.of(g1, g2, g3));
        when(nodeGroupSkillRepository.findByNodeGroupIdOrderByOrderIndexAscIdAsc(any()))
                .thenReturn(List.of(RoadmapTemplateNodeGroupSkill.builder().skillId(101L).skillNameSnapshot("Java").build()));
        when(roadmapSessionRepository.countActiveByUserId(99L)).thenReturn(0L);
        when(roadmapSessionRepository.save(any(RoadmapSession.class))).thenAnswer(inv -> {
            RoadmapSession s = inv.getArgument(0);
            s.setId(555L);
            return s;
        });

        when(nodeAiEnrichmentService.enrichNode(
                anyString(), any(), any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), any(), any()))
                .thenAnswer(inv -> new RoadmapNodeAiEnrichmentService.EnrichedNode(
                        "AI Enriched: " + inv.getArgument(0),
                        List.of("Obj"),
                        List.of("Ex"),
                        List.of("Crit"),
                        "AI Out",
                        "AI Rubric",
                        List.of(new RoadmapNodeAiEnrichmentService.EnrichedLesson("AI L", "AI D", "AI O", 40))
                ));

        Long sessionId = service.createRoadmapSessionFromPublishedTemplate(journey, testResult, List.of(), List.of());
        assertThat(sessionId).isEqualTo(555L);

        verify(nodeAiEnrichmentService, times(2)).enrichNode(
                anyString(), any(), any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), any(), any());

        ArgumentCaptor<RoadmapSession> sessionCaptor = ArgumentCaptor.forClass(RoadmapSession.class);
        verify(roadmapSessionRepository).save(sessionCaptor.capture());
        JsonNode root = realMapper.readTree(sessionCaptor.getValue().getRoadmapJson());
        JsonNode roadmapArr = root.path("roadmap");
        assertThat(roadmapArr).hasSize(3);
        assertThat(roadmapArr.get(0).path("enrichment_status").asText()).isEqualTo("COMPLETED");
        assertThat(roadmapArr.get(0).path("description").asText()).isEqualTo("AI Enriched: Node 1");
        assertThat(roadmapArr.get(1).path("enrichment_status").asText()).isEqualTo("COMPLETED");
        assertThat(roadmapArr.get(1).path("description").asText()).isEqualTo("AI Enriched: Node 2");
        assertThat(roadmapArr.get(2).path("enrichment_status").asText()).isEqualTo("PENDING");
        assertThat(roadmapArr.get(2).path("description").asText()).isEqualTo("Blue 3");
        assertThat(roadmapArr.get(2).path("lessons").get(0).path("title").asText()).isEqualTo("L3");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RoadmapBackgroundEnrichmentService.PendingNodeEnrichmentTask>> tasksCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(backgroundEnrichmentService).enrichRemainingNodesAsync(eq(555L), tasksCaptor.capture());
        assertThat(tasksCaptor.getValue()).hasSize(1);
        assertThat(tasksCaptor.getValue().get(0).nodeId()).isEqualTo("mod-3");
        assertThat(tasksCaptor.getValue().get(0).lessonsJson()).contains("L3");
    }
}
