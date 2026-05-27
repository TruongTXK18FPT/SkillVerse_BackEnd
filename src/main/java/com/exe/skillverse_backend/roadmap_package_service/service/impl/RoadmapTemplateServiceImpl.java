package com.exe.skillverse_backend.roadmap_package_service.service.impl;

import com.exe.skillverse_backend.ai_service.entity.RoadmapSession;
import com.exe.skillverse_backend.ai_service.entity.UserRoadmapProgress;
import com.exe.skillverse_backend.ai_service.repository.RoadmapSessionRepository;
import com.exe.skillverse_backend.ai_service.repository.UserRoadmapProgressRepository;
import com.exe.skillverse_backend.auth_service.entity.PrimaryRole;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.career_taxonomy_service.entity.Domain;
import com.exe.skillverse_backend.career_taxonomy_service.entity.JobPosition;
import com.exe.skillverse_backend.career_taxonomy_service.entity.JobPositionTrack;
import com.exe.skillverse_backend.career_taxonomy_service.entity.JobPositionTrackSkill;
import com.exe.skillverse_backend.career_taxonomy_service.enums.RequirementType;
import com.exe.skillverse_backend.career_taxonomy_service.enums.TaxonomyStatus;
import com.exe.skillverse_backend.career_taxonomy_service.repository.DomainRepository;
import com.exe.skillverse_backend.career_taxonomy_service.repository.JobPositionRepository;
import com.exe.skillverse_backend.career_taxonomy_service.repository.JobPositionTrackRepository;
import com.exe.skillverse_backend.career_taxonomy_service.repository.JobPositionTrackSkillRepository;
import com.exe.skillverse_backend.course_service.repository.CourseRepository;
import com.exe.skillverse_backend.journey_service.entity.Journey;
import com.exe.skillverse_backend.journey_service.entity.TestResult;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.RoadmapNodeAssignment;
import com.exe.skillverse_backend.journey_service.node_mentoring.repository.RoadmapNodeAssignmentRepository;
import com.exe.skillverse_backend.journey_service.repository.JourneyRepository;
import com.exe.skillverse_backend.roadmap_package_service.dto.request.RoadmapTemplateActivityRequest;
import com.exe.skillverse_backend.roadmap_package_service.dto.request.RoadmapTemplateCourseRequest;
import com.exe.skillverse_backend.roadmap_package_service.dto.request.RoadmapTemplateNodeGroupRequest;
import com.exe.skillverse_backend.roadmap_package_service.dto.request.RoadmapTemplateNodeGroupSkillRequest;
import com.exe.skillverse_backend.roadmap_package_service.dto.request.RoadmapTemplateNodeRequest;
import com.exe.skillverse_backend.roadmap_package_service.dto.request.RoadmapTemplateRequest;
import com.exe.skillverse_backend.roadmap_package_service.dto.request.RoadmapTemplateSkillBlockRequest;
import com.exe.skillverse_backend.roadmap_package_service.dto.response.RoadmapTemplateActivityResponse;
import com.exe.skillverse_backend.roadmap_package_service.dto.response.RoadmapTemplateAllocationPreviewResponse;
import com.exe.skillverse_backend.roadmap_package_service.dto.response.RoadmapTemplateCourseCandidateResponse;
import com.exe.skillverse_backend.roadmap_package_service.dto.response.RoadmapTemplateCourseResponse;
import com.exe.skillverse_backend.roadmap_package_service.dto.response.RoadmapTemplateNodeGroupResponse;
import com.exe.skillverse_backend.roadmap_package_service.dto.response.RoadmapTemplateNodeResponse;
import com.exe.skillverse_backend.roadmap_package_service.dto.response.RoadmapTemplateResponse;
import com.exe.skillverse_backend.roadmap_package_service.dto.response.RoadmapTemplateSkillBlockResponse;
import com.exe.skillverse_backend.roadmap_package_service.dto.response.RoadmapTemplateValidationResponse;
import com.exe.skillverse_backend.roadmap_package_service.constant.RoadmapEvidenceAiReviewDefaults;
import com.exe.skillverse_backend.roadmap_package_service.entity.RoadmapTemplate;
import com.exe.skillverse_backend.roadmap_package_service.entity.RoadmapTemplateActivity;
import com.exe.skillverse_backend.roadmap_package_service.entity.RoadmapTemplateCourse;
import com.exe.skillverse_backend.roadmap_package_service.entity.RoadmapTemplateCourseLinkPolicy;
import com.exe.skillverse_backend.roadmap_package_service.entity.RoadmapTemplateGenerationMode;
import com.exe.skillverse_backend.roadmap_package_service.entity.RoadmapTemplateKnowledgePolicy;
import com.exe.skillverse_backend.roadmap_package_service.entity.RoadmapTemplateNode;
import com.exe.skillverse_backend.roadmap_package_service.entity.RoadmapTemplateNodeGroup;
import com.exe.skillverse_backend.roadmap_package_service.entity.RoadmapTemplateNodeGroupSkill;
import com.exe.skillverse_backend.roadmap_package_service.entity.RoadmapTemplateSkillBlock;
import com.exe.skillverse_backend.roadmap_package_service.entity.RoadmapTemplateStatus;
import com.exe.skillverse_backend.roadmap_package_service.repository.RoadmapTemplateActivityRepository;
import com.exe.skillverse_backend.roadmap_package_service.repository.RoadmapTemplateCourseRepository;
import com.exe.skillverse_backend.roadmap_package_service.repository.RoadmapTemplateNodeGroupRepository;
import com.exe.skillverse_backend.roadmap_package_service.repository.RoadmapTemplateNodeGroupSkillRepository;
import com.exe.skillverse_backend.roadmap_package_service.repository.RoadmapTemplateNodeRepository;
import com.exe.skillverse_backend.roadmap_package_service.repository.RoadmapTemplateRepository;
import com.exe.skillverse_backend.roadmap_package_service.service.RoadmapNodeSkillWeightCalculator;
import com.exe.skillverse_backend.roadmap_package_service.repository.RoadmapTemplateSkillBlockRepository;
import com.exe.skillverse_backend.roadmap_package_service.service.RoadmapSkillPriorityCalculator;
import com.exe.skillverse_backend.roadmap_package_service.service.RoadmapSkillPriorityCalculator.SkillPriority;
import com.exe.skillverse_backend.roadmap_package_service.service.RoadmapTemplateService;
import com.exe.skillverse_backend.shared.entity.Skill;
import com.exe.skillverse_backend.shared.exception.ApiException;
import com.exe.skillverse_backend.shared.exception.ErrorCode;
import com.exe.skillverse_backend.shared.repository.SkillRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.exe.skillverse_backend.roadmap_package_service.service.RoadmapNodeAiEnrichmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Qualifier;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoadmapTemplateServiceImpl implements RoadmapTemplateService {

    private static final int MAX_CONCURRENT_ACTIVE_ROADMAPS = 5;

    private static final String GENERATION_MODE_TEMPLATE_GUIDED = "TEMPLATE_GUIDED";

    private record AllocationResult(
            boolean valid,
            List<String> errors,
            List<RoadmapTemplateAllocationPreviewResponse.Item> items
    ) {
    }

    private record RuntimeRoadmap(String roadmapJson, List<RuntimeRoadmapNode> nodes) {
    }

    private record RuntimeRoadmapNode(
            String id,
            Long templateSkillBlockId,
            Long skillId,
            String skillName,
            String title,
            String description,
            String expectedOutput,
            String rubric,
            String difficulty,
            Journey.SkillLevel minLevel,
            Journey.SkillLevel maxLevel,
            boolean targetLevelMatch,
            String activitySource,
            String reason,
            Double estimatedHours,
            List<Long> suggestedCourseIds,
            String skillRequirementsJson,
            List<String> learningObjectives,
            List<String> practicalExercises,
            List<String> successCriteria,
            String pinnedDocumentIds,
            String nodeType,
            String parentNodeKey,
            List<RoadmapNodeAiEnrichmentService.EnrichedLesson> lessons
    ) {
    }

    private static record EnrichedRuntimeNodeV1(
            RoadmapTemplateNode node,
            RoadmapNodeAiEnrichmentService.EnrichedNode enriched,
            String difficulty,
            int estimatedMinutes
    ) {
    }

    private final UserRepository userRepository;
    private final DomainRepository domainRepository;
    private final JobPositionRepository jobPositionRepository;
    private final JobPositionTrackRepository jobPositionTrackRepository;
    private final JobPositionTrackSkillRepository jobPositionTrackSkillRepository;
    private final SkillRepository skillRepository;
    private final RoadmapTemplateRepository templateRepository;
    private final RoadmapTemplateNodeRepository nodeRepository;
    private final RoadmapTemplateCourseRepository courseRepository;
    private final RoadmapTemplateSkillBlockRepository skillBlockRepository;
    private final RoadmapTemplateActivityRepository activityRepository;
    private final RoadmapTemplateNodeGroupRepository nodeGroupRepository;
    private final RoadmapTemplateNodeGroupSkillRepository nodeGroupSkillRepository;
    private final CourseRepository systemCourseRepository;
    private final JourneyRepository journeyRepository;
    private final RoadmapSessionRepository roadmapSessionRepository;
    private final RoadmapNodeAssignmentRepository assignmentRepository;
    private final UserRoadmapProgressRepository progressRepository;
    private final ObjectMapper objectMapper;
    private final RoadmapNodeAiEnrichmentService nodeAiEnrichmentService;

    @Autowired
    @Lazy
    private RoadmapTemplateServiceImpl self;

    @Autowired
    @Qualifier("roadmapEnrichmentTaskExecutor")
    private Executor roadmapEnrichmentTaskExecutor;


    @Override
    @Transactional
    public RoadmapTemplateResponse createTemplate(Long actorId, RoadmapTemplateRequest request) {
        requireAdmin(actorId);
        User admin = requireUser(actorId);
        validateTemplateTaxonomy(request);
        normalizeSkillBlocksFromTrack(request);
        RoadmapTemplate template = RoadmapTemplate.builder()
                .createdByAdminId(actorId)
                .updatedByAdminId(actorId)
                .status(RoadmapTemplateStatus.DRAFT)
                .build();
        applyTemplateFields(template, request);
        RoadmapTemplate saved = templateRepository.save(template);
        replaceTemplateChildren(saved, request);
        return toTemplateResponse(saved);
    }

    @Override
    @Transactional
    public RoadmapTemplateResponse updateTemplate(Long actorId, Long templateId, RoadmapTemplateRequest request) {
        requireAdmin(actorId);
        validateTemplateTaxonomy(request);
        normalizeSkillBlocksFromTrack(request);
        RoadmapTemplate template = requireTemplate(templateId);
        if (template.getStatus() == RoadmapTemplateStatus.ARCHIVED) {
            throw new ApiException(ErrorCode.CONFLICT, "Archived templates cannot be edited");
        }
        template.setUpdatedByAdminId(actorId);
        applyTemplateFields(template, request);
        RoadmapTemplate saved = templateRepository.save(template);
        courseRepository.deleteByTemplateId(saved.getId());
        nodeGroupSkillRepository.deleteByTemplateId(saved.getId());
        nodeGroupRepository.deleteByTemplateId(saved.getId());
        activityRepository.deleteByTemplateId(saved.getId());
        skillBlockRepository.deleteByTemplateId(saved.getId());
        nodeRepository.deleteByTemplateId(saved.getId());
        replaceTemplateChildren(saved, request);
        return toTemplateResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoadmapTemplateResponse> listAdminTemplates(Long actorId, Long domainId, Long jobPositionId,
                                                            Long jobPositionTrackId, RoadmapTemplateStatus status) {
        requireAdmin(actorId);
        return templateRepository.searchAdminTemplates(domainId, jobPositionId, jobPositionTrackId, status).stream()
                .map(this::toTemplateResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoadmapTemplateResponse> getMyTemplates(Long actorId) {
        User actor = requireUser(actorId);
        if (isAdmin(actor)) {
            return templateRepository.searchAdminTemplates(null, null, null, null).stream()
                    .map(this::toTemplateResponse)
                    .toList();
        }
        return List.of();
    }

    @Override
    @Transactional(readOnly = true)
    public RoadmapTemplateResponse getTemplate(Long actorId, Long templateId) {
        RoadmapTemplate template = requireTemplate(templateId);
        if (!isAdmin(requireUser(actorId)) && template.getStatus() != RoadmapTemplateStatus.PUBLISHED
                && template.getStatus() != RoadmapTemplateStatus.APPROVED) {
            throw new ApiException(ErrorCode.FORBIDDEN, "You cannot access this roadmap template");
        }
        return toTemplateResponse(template);
    }

    @Override
    @Transactional
    public RoadmapTemplateResponse submitTemplate(Long actorId, Long templateId) {
        requireAdmin(actorId);
        RoadmapTemplate template = requireTemplate(templateId);
        validateTemplateCanPublish(template);
        template.setStatus(RoadmapTemplateStatus.PUBLISHED);
        template.setUpdatedByAdminId(actorId);
        return toTemplateResponse(templateRepository.save(template));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoadmapTemplateResponse> getSubmittedTemplates() {
        return templateRepository.findByStatusOrderByCreatedAtDesc(RoadmapTemplateStatus.SUBMITTED).stream()
                .map(this::toTemplateResponse)
                .toList();
    }

    @Override
    @Transactional
    public RoadmapTemplateResponse approveTemplate(Long adminId, Long templateId) {
        requireAdmin(adminId);
        RoadmapTemplate template = requireTemplate(templateId);
        validateTemplateCanPublish(template);
        template.setStatus(RoadmapTemplateStatus.PUBLISHED);
        template.setUpdatedByAdminId(adminId);
        return toTemplateResponse(templateRepository.save(template));
    }

    @Override
    @Transactional
    public RoadmapTemplateResponse rejectTemplate(Long adminId, Long templateId) {
        requireAdmin(adminId);
        RoadmapTemplate template = requireTemplate(templateId);
        template.setStatus(RoadmapTemplateStatus.DRAFT);
        template.setUpdatedByAdminId(adminId);
        return toTemplateResponse(templateRepository.save(template));
    }

    @Override
    @Transactional
    public RoadmapTemplateResponse publishTemplate(Long adminId, Long templateId) {
        requireAdmin(adminId);
        RoadmapTemplate template = requireTemplate(templateId);
        validateTemplateCanPublish(template);
        template.setStatus(RoadmapTemplateStatus.PUBLISHED);
        template.setUpdatedByAdminId(adminId);
        return toTemplateResponse(templateRepository.save(template));
    }

    @Override
    @Transactional
    public RoadmapTemplateResponse archiveTemplate(Long adminId, Long templateId) {
        requireAdmin(adminId);
        RoadmapTemplate template = requireTemplate(templateId);
        template.setStatus(RoadmapTemplateStatus.ARCHIVED);
        template.setUpdatedByAdminId(adminId);
        return toTemplateResponse(templateRepository.save(template));
    }

    @Override
    @Transactional
    public void deleteTemplate(Long adminId, Long templateId) {
        requireAdmin(adminId);
        RoadmapTemplate template = requireTemplate(templateId);
        if (template.getStatus() != RoadmapTemplateStatus.ARCHIVED) {
            throw new ApiException(ErrorCode.CONFLICT, "Chỉ có thể xóa vĩnh viễn mẫu lộ trình đã lưu trữ");
        }
        templateRepository.delete(template);
    }

    @Override
    @Transactional(readOnly = true)
    public RoadmapTemplateAllocationPreviewResponse previewAllocation(Long actorId, RoadmapTemplateRequest request) {
        requireAdmin(actorId);
        normalizeSkillBlocksFromTrack(request);
        return allocateFromRequest(request);
    }

    @Override
    @Transactional(readOnly = true)
    public RoadmapTemplateValidationResponse validateTemplate(Long actorId, RoadmapTemplateRequest request) {
        requireAdmin(actorId);
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        try {
            validateTemplateTaxonomy(request);
            normalizeSkillBlocksFromTrack(request);
        } catch (ApiException ex) {
            errors.add(ex.getMessage());
        }
        RoadmapTemplateAllocationPreviewResponse allocation = allocateFromRequest(request);
        if (request.getNodeGroups() == null || request.getNodeGroups().isEmpty()) {
            errors.addAll(defaultList(allocation.getErrors()));
        } else {
            warnings.addAll(defaultList(allocation.getErrors()));
        }
        validateV2Content(request, errors, warnings, allocation);
        return RoadmapTemplateValidationResponse.builder()
                .valid(errors.isEmpty())
                .errors(errors)
                .warnings(warnings)
                .allocation(allocation)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoadmapTemplateCourseCandidateResponse> getCourseCandidates(
            Long actorId, Long skillId, RoadmapTemplateCourseLinkPolicy policy, Integer limit) {
        requireAdmin(actorId);
        int safeLimit = Math.max(1, Math.min(limit != null ? limit : 5, 20));
        RoadmapTemplateCourseLinkPolicy safePolicy = policy != null ? policy : RoadmapTemplateCourseLinkPolicy.AUTO_HYBRID;
        List<Object[]> rows;
        if (safePolicy == RoadmapTemplateCourseLinkPolicy.AUTO_POPULAR) {
            rows = systemCourseRepository.findPopularPublicCourseCandidatesBySkill(skillId, safeLimit);
        } else if (safePolicy == RoadmapTemplateCourseLinkPolicy.AUTO_NEWEST) {
            rows = systemCourseRepository.findNewestPublicCourseCandidatesBySkill(skillId, safeLimit);
        } else if (safePolicy == RoadmapTemplateCourseLinkPolicy.MANUAL_ONLY) {
            rows = List.of();
        } else {
            List<Object[]> newest = systemCourseRepository.findNewestPublicCourseCandidatesBySkill(skillId, safeLimit);
            List<Object[]> popular = systemCourseRepository.findPopularPublicCourseCandidatesBySkill(skillId, safeLimit);
            Map<Long, Object[]> merged = new LinkedHashMap<>();
            newest.forEach(row -> merged.put(asLong(row[0]), row));
            popular.forEach(row -> merged.putIfAbsent(asLong(row[0]), row));
            rows = merged.values().stream().limit(safeLimit).toList();
        }
        return rows.stream().map(this::toCourseCandidateResponse).toList();
    }

    @Override
    public Long createRoadmapSessionFromPublishedTemplate(Journey journey, TestResult testResult,
                                                          List<Map<String, Object>> skillGaps,
                                                          List<Map<String, Object>> strengths) {
        if (journey == null || journey.getUser() == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Journey context is required to generate roadmap from template");
        }
        if (journey.getJobPositionTrackId() == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Journey is missing job position track");
        }

        RoadmapTemplate template = templateRepository
                .findFirstByJobPositionTrackIdAndStatusOrderByUpdatedAtDescCreatedAtDesc(
                        journey.getJobPositionTrackId(), RoadmapTemplateStatus.PUBLISHED)
                .orElseThrow(() -> new ApiException(ErrorCode.CONFLICT,
                        "Admin has not published a roadmap template for this job position track"));

        List<RoadmapTemplateNodeGroup> nodeGroups = nodeGroupRepository.findByTemplateIdOrderByOrderIndexAscIdAsc(template.getId());
        if (!nodeGroups.isEmpty()) {
            return createRoadmapSessionFromNodeGroups(template, nodeGroups, journey, testResult, skillGaps, strengths);
        }

        List<RoadmapTemplateSkillBlock> v2Blocks = skillBlockRepository.findByTemplateIdOrderByIdAsc(template.getId());
        if (!v2Blocks.isEmpty()) {
            return createRoadmapSessionFromV2Template(template, v2Blocks, journey, testResult, skillGaps, strengths);
        }

        List<RoadmapTemplateNode> nodes = nodeRepository.findByTemplateIdOrderByOrderIndexAscIdAsc(template.getId());
        if (nodes.isEmpty()) {
            throw new ApiException(ErrorCode.CONFLICT, "Published roadmap template has no nodes");
        }

        if (roadmapSessionRepository.countActiveByUserId(journey.getUser().getId()) >= MAX_CONCURRENT_ACTIVE_ROADMAPS) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Bạn đang học tối đa 5 lộ trình cùng lúc. Hãy hoàn thành, tạm dừng hoặc xóa một lộ trình trước khi tạo mới.");
        }

        String studentLevel = resolveStudentLevel(journey, testResult);
        Set<String> gapNames = extractProfileSkillNames(skillGaps);
        Set<String> strengthNames = extractProfileSkillNames(strengths);
        
        List<CompletableFuture<EnrichedRuntimeNodeV1>> futures = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            RoadmapTemplateNode node = nodes.get(i);
            boolean gapMatched = profileMatchesNode(gapNames, node);
            boolean strengthMatched = profileMatchesNode(strengthNames, node);

            CompletableFuture<EnrichedRuntimeNodeV1> future = CompletableFuture.supplyAsync(() -> {
                RoadmapNodeAiEnrichmentService.EnrichedNode enriched = nodeAiEnrichmentService.enrichNode(
                        node.getTitle(),
                        node.getDescription(),
                        node.getExpectedOutput(),
                        node.getRubric(),
                        node.getSkillNameSnapshot(),
                        studentLevel,
                        journey.getGoal(),
                        gapMatched,
                        strengthMatched,
                        node.getPinnedDocumentIds()
                );
                int personalizedMinutes = personalizeMinutes(node, studentLevel, gapMatched);
                String difficulty = personalizeDifficulty(node, studentLevel, gapMatched, strengthMatched);
                return new EnrichedRuntimeNodeV1(node, enriched, difficulty, personalizedMinutes);
            }, roadmapEnrichmentTaskExecutor);

            futures.add(future);
        }

        // Chờ tất cả hoàn thành song song (ThreadPoolTaskExecutor khống chế 3 luồng đồng thời)
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<EnrichedRuntimeNodeV1> enrichedNodes = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

        String roadmapJson = buildEnrichedRoadmapJson(template, enrichedNodes, journey, testResult, skillGaps, strengths);
        RoadmapSession session = RoadmapSession.builder()
                .user(journey.getUser())
                .title(template.getTitle())
                .schemaVersion(2)
                .originalGoal(template.getTitle())
                .validatedGoal(firstNonBlank(template.getDescription(), template.getTitle()))
                .goal(template.getTitle())
                .duration("Admin template guided")
                .experienceLevel(studentLevel)
                .learningStyle("Assessment-aware admin template")
                .roadmapType("career")
                .roadmapMode(GENERATION_MODE_TEMPLATE_GUIDED)
                .roadmapTemplateId(template.getId())
                .jobPositionTrackId(template.getJobPositionTrackId())
                .generationMode(GENERATION_MODE_TEMPLATE_GUIDED)
                .templateSnapshotJson(writeJson(toTemplateResponse(template)))
                .target(resolveRuntimeTarget(template, journey))
                .finalObjective("Complete admin-defined job-position roadmap with verified evidence")
                .totalNodes(nodes.size())
                .totalEstimatedHours(nodes.stream().map(RoadmapTemplateNode::getEstimatedHours).filter(Objects::nonNull)
                        .mapToDouble(Double::doubleValue).sum())
                .difficultyLevel(resolveDominantDifficulty(nodes))
                .isPremiumGenerated(false)
                .status(RoadmapSession.RoadmapStatus.ACTIVE)
                .roadmapJson(roadmapJson)
                .build();

        List<RoadmapNodeAssignment> assignments = buildRuntimeAssignmentsV1(journey, session, enrichedNodes);
        List<UserRoadmapProgress> progresses = buildInitialProgress(session, nodes);

        return self.saveRoadmapSessionAndData(
                session,
                journey,
                assignments,
                progresses,
                template.getId(),
                template.getJobPositionTrackId(),
                writeJson(extractSkillIds(nodes))
        );
    }

    private List<RoadmapNodeAssignment> buildRuntimeAssignmentsV1(
            Journey journey, 
            RoadmapSession session, 
            List<EnrichedRuntimeNodeV1> enrichedNodes) {
        return enrichedNodes.stream()
                .map(ern -> RoadmapNodeAssignment.builder()
                        .journeyId(journey.getId())
                        .roadmapSessionId(session != null ? session.getId() : null)
                        .nodeId(resolveNodeId(ern.node()))
                        .nodeSkillId(ern.node().getSkillId())
                        .roadmapTemplateNodeId(ern.node().getId())
                        .assignmentSource(RoadmapNodeAssignment.AssignmentSource.TEMPLATE)
                        .title(ern.node().getTitle())
                        .description(formatExercisesToMarkdown(ern.enriched().getPracticalExercises(), ern.enriched().getDescription()))
                        .expectedOutput(ern.enriched().getExpectedOutput())
                        .rubric(ern.enriched().getRubric())
                        .createdBy(journey.getUser().getId())
                        .build())
                .toList();
    }

    private Long createRoadmapSessionFromV2Template(
            RoadmapTemplate template,
            List<RoadmapTemplateSkillBlock> blocks,
            Journey journey,
            TestResult testResult,
            List<Map<String, Object>> skillGaps,
            List<Map<String, Object>> strengths) {
        if (roadmapSessionRepository.countActiveByUserId(journey.getUser().getId()) >= MAX_CONCURRENT_ACTIVE_ROADMAPS) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Student already has the maximum number of active roadmaps. Pause, complete, or delete one roadmap first.");
        }

        RuntimeRoadmap runtime = buildRuntimeRoadmapFromSkillBlocks(template, blocks, journey, testResult, skillGaps, strengths);
        double totalHours = runtime.nodes().stream()
                .map(RuntimeRoadmapNode::estimatedHours)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();

        RoadmapSession session = RoadmapSession.builder()
                .user(journey.getUser())
                .title(template.getTitle())
                .schemaVersion(2)
                .originalGoal(template.getTitle())
                .validatedGoal(firstNonBlank(template.getGlobalLearningGoal(), template.getDescription(), template.getTitle()))
                .goal(template.getTitle())
                .duration("Admin V3 template guided")
                .experienceLevel(resolveStudentLevel(journey, testResult))
                .learningStyle("Assessment-aware admin template allocation")
                .roadmapType("career")
                .roadmapMode(GENERATION_MODE_TEMPLATE_GUIDED)
                .roadmapTemplateId(template.getId())
                .jobPositionTrackId(template.getJobPositionTrackId())
                .generationMode(GENERATION_MODE_TEMPLATE_GUIDED)
                .templateSnapshotJson(writeJson(toTemplateResponse(template)))
                .target(resolveRuntimeTarget(template, journey))
                .finalObjective(firstNonBlank(template.getOutputStandard(), "Complete admin-defined job-position roadmap with verified evidence"))
                .totalNodes(runtime.nodes().size())
                .totalEstimatedHours(totalHours)
                .difficultyLevel(resolveDominantDifficultyRuntime(runtime.nodes()))
                .isPremiumGenerated(false)
                .status(RoadmapSession.RoadmapStatus.ACTIVE)
                .roadmapJson(runtime.roadmapJson())
                .build();

        List<RoadmapNodeAssignment> assignments = buildRuntimeAssignmentsFromRuntime(journey, session, runtime.nodes());
        List<UserRoadmapProgress> progresses = buildInitialProgressFromRuntime(session, runtime.nodes());

        return self.saveRoadmapSessionAndData(
                session,
                journey,
                assignments,
                progresses,
                template.getId(),
                template.getJobPositionTrackId(),
                writeJson(extractSkillIdsFromBlocks(blocks))
        );
    }

    private Long createRoadmapSessionFromNodeGroups(
            RoadmapTemplate template,
            List<RoadmapTemplateNodeGroup> nodeGroups,
            Journey journey,
            TestResult testResult,
            List<Map<String, Object>> skillGaps,
            List<Map<String, Object>> strengths) {
        if (roadmapSessionRepository.countActiveByUserId(journey.getUser().getId()) >= MAX_CONCURRENT_ACTIVE_ROADMAPS) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Student already has the maximum number of active roadmaps. Pause, complete, or delete one roadmap first.");
        }

        RuntimeRoadmap runtime = buildRuntimeRoadmapFromNodeGroups(template, nodeGroups, journey, testResult, skillGaps, strengths);
        double totalHours = runtime.nodes().stream()
                .map(RuntimeRoadmapNode::estimatedHours)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();

        RoadmapSession session = RoadmapSession.builder()
                .user(journey.getUser())
                .title(template.getTitle())
                .schemaVersion(2)
                .originalGoal(template.getTitle())
                .validatedGoal(firstNonBlank(template.getGlobalLearningGoal(), template.getDescription(), template.getTitle()))
                .goal(template.getTitle())
                .duration("Admin module template guided")
                .experienceLevel(resolveStudentLevel(journey, testResult))
                .learningStyle("Assessment-aware admin module template")
                .roadmapType("career")
                .roadmapMode(GENERATION_MODE_TEMPLATE_GUIDED)
                .roadmapTemplateId(template.getId())
                .jobPositionTrackId(template.getJobPositionTrackId())
                .generationMode(GENERATION_MODE_TEMPLATE_GUIDED)
                .templateSnapshotJson(writeJson(toTemplateResponse(template)))
                .target(resolveRuntimeTarget(template, journey))
                .finalObjective(firstNonBlank(template.getOutputStandard(), "Complete admin-defined job-position roadmap with verified evidence"))
                .totalNodes(runtime.nodes().size())
                .totalEstimatedHours(totalHours)
                .difficultyLevel(resolveDominantDifficultyRuntime(runtime.nodes()))
                .isPremiumGenerated(false)
                .status(RoadmapSession.RoadmapStatus.ACTIVE)
                .roadmapJson(runtime.roadmapJson())
                .build();

        List<RoadmapNodeAssignment> assignments = buildRuntimeAssignmentsFromRuntime(journey, session, runtime.nodes());
        List<UserRoadmapProgress> progresses = buildInitialProgressFromRuntime(session, runtime.nodes());

        return self.saveRoadmapSessionAndData(
                session,
                journey,
                assignments,
                progresses,
                template.getId(),
                template.getJobPositionTrackId(),
                writeJson(extractSkillIdsFromRuntime(runtime.nodes()))
        );
    }



    private void applyTemplateFields(RoadmapTemplate template, RoadmapTemplateRequest request) {
        template.setDomainId(request.getDomainId());
        template.setJobPositionId(request.getJobPositionId());
        template.setJobPositionTrackId(request.getJobPositionTrackId());
        template.setTitle(request.getTitle());
        template.setDescription(request.getDescription());
        template.setTargetRole(request.getTargetRole());
        template.setTargetLevel(request.getTargetLevel());
        template.setTargetRoleSnapshot(firstNonBlank(request.getTargetRoleSnapshot(), request.getTargetRole()));
        template.setTargetLevelSnapshot(firstNonBlank(request.getTargetLevelSnapshot(), request.getTargetLevel()));
        boolean hasV2Blocks = (request.getSkillBlocks() != null && !request.getSkillBlocks().isEmpty())
                || (request.getNodeGroups() != null && !request.getNodeGroups().isEmpty());
        template.setTotalNodeCount(request.getTotalNodeCount());
        template.setGenerationMode(request.getGenerationMode() != null
                ? request.getGenerationMode()
                : hasV2Blocks ? RoadmapTemplateGenerationMode.TEMPLATE_AI_GUIDED : RoadmapTemplateGenerationMode.LEGACY_STATIC);
        template.setKnowledgePolicy(request.getKnowledgePolicy() != null
                ? request.getKnowledgePolicy()
                : RoadmapTemplateKnowledgePolicy.TEMPLATE_ONLY);
        template.setGlobalLearningGoal(request.getGlobalLearningGoal());
        template.setAudienceLevel(request.getAudienceLevel());
        template.setOutputStandard(request.getOutputStandard());
        template.setAssessmentPolicy(request.getAssessmentPolicy());
        template.setTemplateInstructions(request.getTemplateInstructions());
        template.setConstraintsJson(request.getConstraintsJson());
        boolean isCreate = template.getId() == null;
        if (request.getAiEvidenceReviewEnabled() != null) {
            template.setAiEvidenceReviewEnabled(request.getAiEvidenceReviewEnabled());
        } else if (isCreate) {
            template.setAiEvidenceReviewEnabled(true);
        }
        if (request.getAiAutoPassEnabled() != null) {
            template.setAiAutoPassEnabled(request.getAiAutoPassEnabled());
        } else if (isCreate) {
            template.setAiAutoPassEnabled(false);
        }
        template.setAiAutoPassMinScorePercent(request.getAiAutoPassMinScorePercent() != null ? request.getAiAutoPassMinScorePercent() : RoadmapEvidenceAiReviewDefaults.AI_AUTO_PASS_MIN_SCORE_PERCENT);
        template.setAiAutoPassMinConfidence(request.getAiAutoPassMinConfidence() != null ? request.getAiAutoPassMinConfidence() : RoadmapEvidenceAiReviewDefaults.AI_AUTO_PASS_MIN_CONFIDENCE);
        template.setAiManualReviewBelowConfidence(request.getAiManualReviewBelowConfidence() != null ? request.getAiManualReviewBelowConfidence() : RoadmapEvidenceAiReviewDefaults.AI_MANUAL_REVIEW_BELOW_CONFIDENCE);
        String prompt = request.getAiEvidencePrompt();
        if (isCreate && (prompt == null || prompt.trim().isEmpty())) {
            prompt = RoadmapEvidenceAiReviewDefaults.DEFAULT_AI_EVIDENCE_PROMPT;
        }
        template.setAiEvidencePrompt(prompt);
        template.setFinalAssignmentInstructions(request.getFinalAssignmentInstructions());
        template.setFinalAssignmentRubric(request.getFinalAssignmentRubric());
    }

    private void replaceTemplateChildren(RoadmapTemplate template, RoadmapTemplateRequest request) {
        if (request.getNodes() != null) {
            for (RoadmapTemplateNodeRequest nodeRequest : request.getNodes()) {
                RoadmapTemplateNode node = buildNode(template, nodeRequest);
                nodeRepository.save(node);
            }
        }

        // Build global weights map for dynamic calculation
        java.util.Map<Long, Double> globalWeights = new java.util.HashMap<>();
        if (request.getSkillBlocks() != null) {
            for (RoadmapTemplateSkillBlockRequest blockRequest : request.getSkillBlocks()) {
                if (blockRequest.getSkillId() != null) {
                    globalWeights.put(blockRequest.getSkillId(), blockRequest.getWeightPercent() != null ? blockRequest.getWeightPercent() : 10.0D);
                }
            }
        }

        if (request.getSkillBlocks() != null) {
            for (RoadmapTemplateSkillBlockRequest blockRequest : request.getSkillBlocks()) {
                RoadmapTemplateSkillBlock block = skillBlockRepository.save(buildSkillBlock(template, blockRequest));
                for (RoadmapTemplateActivityRequest activityRequest : defaultList(blockRequest.getActivities())) {
                    RoadmapTemplateActivity activity = activityRepository.save(buildActivity(template, block, activityRequest));
                    block.getActivities().add(activity);
                }
            }
        }
        if (request.getNodeGroups() != null) {
            for (RoadmapTemplateNodeGroupRequest groupRequest : request.getNodeGroups()) {
                RoadmapTemplateNodeGroup group = nodeGroupRepository.save(buildNodeGroup(template, groupRequest));
                
                // 1. Gather all skills inside this Node Group for calculation
                List<RoadmapNodeSkillWeightCalculator.SkillInput> inputs = new java.util.ArrayList<>();
                for (RoadmapTemplateNodeGroupSkillRequest skillRequest : defaultList(groupRequest.getSkills())) {
                    Double globalWeight = globalWeights.getOrDefault(skillRequest.getSkillId(), 10.0D);
                    inputs.add(new RoadmapNodeSkillWeightCalculator.SkillInput(
                            skillRequest.getSkillId(),
                            globalWeight,
                            skillRequest.getRequirementType()
                    ));
                }
                
                // 2. Perform the auto-weight calculation
                List<RoadmapNodeSkillWeightCalculator.CalculatedWeight> calculatedWeights = 
                        RoadmapNodeSkillWeightCalculator.calculate(groupRequest.getDifficulty(), inputs);
                
                java.util.Map<Long, Double> calculatedMap = calculatedWeights.stream()
                        .collect(java.util.stream.Collectors.toMap(
                                RoadmapNodeSkillWeightCalculator.CalculatedWeight::skillId,
                                RoadmapNodeSkillWeightCalculator.CalculatedWeight::weightInNode,
                                (a, b) -> a
                        ));
                
                // 3. Save Node Group Skills with calculated weights
                for (RoadmapTemplateNodeGroupSkillRequest skillRequest : defaultList(groupRequest.getSkills())) {
                    Double calculatedWeight = calculatedMap.getOrDefault(skillRequest.getSkillId(), 100.0D / Math.max(1, groupRequest.getSkills().size()));
                    RoadmapTemplateNodeGroupSkill skill = nodeGroupSkillRepository.save(buildNodeGroupSkill(group, skillRequest, calculatedWeight));
                    group.getSkills().add(skill);
                }
            }
        }
        if (request.getCourses() != null) {
            for (RoadmapTemplateCourseRequest courseRequest : request.getCourses()) {
                RoadmapTemplateCourse course = buildCourse(template, courseRequest);
                courseRepository.save(course);
            }
        }
    }

    private RoadmapTemplateNode buildNode(RoadmapTemplate template, RoadmapTemplateNodeRequest request) {
        Skill skill = null;
        if (request.getSkillId() != null) {
            skill = skillRepository.findById(request.getSkillId())
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Skill not found: " + request.getSkillId()));
        }
        return RoadmapTemplateNode.builder()
                .template(template)
                .parentNodeId(request.getParentNodeId())
                .nodeKey(request.getNodeKey())
                .title(request.getTitle())
                .description(request.getDescription())
                .orderIndex(request.getOrderIndex())
                .skillId(request.getSkillId())
                .skillNameSnapshot(firstNonBlank(request.getSkillNameSnapshot(), skill != null ? skill.getName() : null))
                .skillCanonicalKeySnapshot(firstNonBlank(request.getSkillCanonicalKeySnapshot(), skill != null ? skill.getCanonicalKey() : null))
                .requirementType(request.getRequirementType())
                .importanceLevel(request.getImportanceLevel())
                .difficulty(firstNonBlank(request.getDifficulty(), "medium"))
                .estimatedHours(request.getEstimatedHours())
                .expectedOutput(request.getExpectedOutput())
                .rubric(request.getRubric())
                .build();
    }

    private RoadmapTemplateSkillBlock buildSkillBlock(RoadmapTemplate template, RoadmapTemplateSkillBlockRequest request) {
        Skill skill = skillRepository.findById(request.getSkillId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Skill not found: " + request.getSkillId()));
        return RoadmapTemplateSkillBlock.builder()
                .template(template)
                .skillId(request.getSkillId())
                .skillNameSnapshot(firstNonBlank(request.getSkillNameSnapshot(), skill.getName()))
                .skillCanonicalKeySnapshot(firstNonBlank(request.getSkillCanonicalKeySnapshot(), skill.getCanonicalKey()))
                .weightPercent(request.getWeightPercent() != null ? request.getWeightPercent() : 0D)
                .minNodes(request.getMinNodes())
                .maxNodes(request.getMaxNodes())
                .nodeCountOverride(request.getNodeCountOverride())
                .learningGoals(request.getLearningGoals())
                .requiredTopics(request.getRequiredTopics())
                .activityInstructions(request.getActivityInstructions())
                .exerciseTypes(request.getExerciseTypes())
                .successCriteria(request.getSuccessCriteria())
                .ragQueryHint(request.getRagQueryHint())
                .courseLinkPolicy(request.getCourseLinkPolicy() != null
                        ? request.getCourseLinkPolicy()
                        : RoadmapTemplateCourseLinkPolicy.AUTO_HYBRID)
                .autoCourseLimit(normalizeAutoCourseLimit(request.getAutoCourseLimit()))
                .ragEnabled(!Boolean.FALSE.equals(request.getRagEnabled()))
                .build();
    }

    private RoadmapTemplateActivity buildActivity(
            RoadmapTemplate template, RoadmapTemplateSkillBlock block, RoadmapTemplateActivityRequest request) {
        return RoadmapTemplateActivity.builder()
                .template(template)
                .skillBlock(block)
                .title(request.getTitle())
                .description(request.getDescription())
                .exerciseType(request.getExerciseType())
                .expectedOutput(request.getExpectedOutput())
                .rubric(request.getRubric())
                .difficulty(firstNonBlank(request.getDifficulty(), "medium"))
                .minLevel(request.getMinLevel())
                .maxLevel(request.getMaxLevel())
                .estimatedHours(request.getEstimatedHours())
                .prerequisiteHint(request.getPrerequisiteHint())
                .aiPromptHint(request.getAiPromptHint())
                .skillRequirementsJson(request.getSkillRequirementsJson())
                .orderIndex(request.getOrderIndex() != null ? request.getOrderIndex() : 1)
                .build();
    }

    private RoadmapTemplateNodeGroup buildNodeGroup(RoadmapTemplate template, RoadmapTemplateNodeGroupRequest request) {
        return RoadmapTemplateNodeGroup.builder()
                .template(template)
                .nodeKey(firstNonBlank(request.getNodeKey(), "module-" + request.getOrderIndex()))
                .title(request.getTitle())
                .description(request.getDescription())
                .learningObjectives(request.getLearningObjectives())
                .lessonsJson(request.getLessonsJson())
                .exercisesJson(request.getExercisesJson())
                .completionCriteria(request.getCompletionCriteria())
                .expectedOutput(request.getExpectedOutput())
                .rubric(request.getRubric())
                .difficulty(firstNonBlank(request.getDifficulty(), "medium"))
                .estimatedHours(request.getEstimatedHours())
                .aiPromptHint(request.getAiPromptHint())
                .nodeType(request.getNodeType())
                .parentNodeKey(request.getParentNodeKey())
                .orderIndex(request.getOrderIndex() != null ? request.getOrderIndex() : 1)
                .build();
    }

    private RoadmapTemplateNodeGroupSkill buildNodeGroupSkill(
            RoadmapTemplateNodeGroup group, RoadmapTemplateNodeGroupSkillRequest request, Double calculatedWeight) {
        Skill skill = skillRepository.findById(request.getSkillId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Skill not found: " + request.getSkillId()));
        return RoadmapTemplateNodeGroupSkill.builder()
                .nodeGroup(group)
                .skillId(request.getSkillId())
                .skillNameSnapshot(firstNonBlank(request.getSkillNameSnapshot(), skill.getName()))
                .skillCanonicalKeySnapshot(firstNonBlank(request.getSkillCanonicalKeySnapshot(), skill.getCanonicalKey()))
                .requirementType(request.getRequirementType() != null
                        ? request.getRequirementType().normalized()
                        : RequirementType.REQUIRED)
                .weightInNode(calculatedWeight)
                .orderIndex(request.getOrderIndex() != null ? request.getOrderIndex() : 1)
                .build();
    }

    private RoadmapTemplateCourse buildCourse(RoadmapTemplate template, RoadmapTemplateCourseRequest request) {
        RoadmapTemplateNode node = null;
        if (request.getTemplateNodeId() != null) {
            node = nodeRepository.findById(request.getTemplateNodeId())
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Template node not found: " + request.getTemplateNodeId()));
            if (!Objects.equals(node.getTemplateId(), template.getId())) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "Template node does not belong to template");
            }
        }
        return RoadmapTemplateCourse.builder()
                .template(template)
                .templateNode(node)
                .courseId(request.getCourseId())
                .skillId(request.getSkillId())
                .displayOrder(request.getDisplayOrder())
                .required(Boolean.TRUE.equals(request.getRequired()))
                .build();
    }

    private void normalizeSkillBlocksFromTrack(RoadmapTemplateRequest request) {
        if (request == null || request.getJobPositionTrackId() == null) {
            return;
        }
        List<SkillPriority> priorities = skillPriorities(request.getJobPositionTrackId());
        if (priorities.isEmpty()) {
            return;
        }
        Map<Long, RoadmapTemplateSkillBlockRequest> existingBySkill = defaultList(request.getSkillBlocks()).stream()
                .filter(block -> block.getSkillId() != null)
                .collect(Collectors.toMap(
                        RoadmapTemplateSkillBlockRequest::getSkillId,
                        block -> block,
                        (first, ignored) -> first,
                        LinkedHashMap::new));
        List<RoadmapTemplateSkillBlockRequest> normalized = new ArrayList<>();
        for (SkillPriority priority : priorities) {
            RoadmapTemplateSkillBlockRequest block = existingBySkill.get(priority.skillId());
            if (block == null) {
                block = new RoadmapTemplateSkillBlockRequest();
                block.setSkillId(priority.skillId());
            }
            block.setSkillNameSnapshot(firstNonBlank(block.getSkillNameSnapshot(), priority.skillName()));
            block.setSkillCanonicalKeySnapshot(firstNonBlank(block.getSkillCanonicalKeySnapshot(), priority.canonicalKey()));
            block.setWeightPercent(priority.weightPercent());
            if (block.getMinNodes() == null) {
                block.setMinNodes(priority.requirementType() == RequirementType.REQUIRED ? 1 : 0);
            }
            if (block.getCourseLinkPolicy() == null) {
                block.setCourseLinkPolicy(RoadmapTemplateCourseLinkPolicy.AUTO_HYBRID);
            }
            if (block.getAutoCourseLimit() == null) {
                block.setAutoCourseLimit(2);
            }
            if (block.getRagEnabled() == null) {
                block.setRagEnabled(true);
            }
            normalized.add(block);
        }
        request.setSkillBlocks(normalized);
    }

    private Map<Long, SkillPriority> skillPriorityByTrack(Long trackId) {
        return skillPriorities(trackId).stream()
                .collect(Collectors.toMap(
                        SkillPriority::skillId,
                        priority -> priority,
                        (a, b) -> a,
                        LinkedHashMap::new));
    }

    private List<SkillPriority> skillPriorities(Long trackId) {
        if (trackId == null) {
            return List.of();
        }
        List<JobPositionTrackSkill> trackSkills = jobPositionTrackSkillRepository.findByTrackIdOrderBySortOrderAsc(trackId);
        if (trackSkills == null || trackSkills.isEmpty()) {
            return List.of();
        }
        Set<Long> skillIds = trackSkills.stream()
                .map(JobPositionTrackSkill::getSkillId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Iterable<Skill> foundSkills = skillRepository.findAllById(skillIds);
        Map<Long, Skill> skillsById = new LinkedHashMap<>();
        if (foundSkills != null) {
            foundSkills.forEach(skill -> {
                if (skill != null && skill.getId() != null) {
                    skillsById.put(skill.getId(), skill);
                }
            });
        }
        return RoadmapSkillPriorityCalculator.calculate(trackSkills, skillsById);
    }

    private void validateTemplateTaxonomy(RoadmapTemplateRequest request) {
        Domain domain = domainRepository.findById(request.getDomainId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Domain not found: " + request.getDomainId()));
        if (domain.getStatus() != TaxonomyStatus.ACTIVE) {
            throw new ApiException(ErrorCode.CONFLICT, "Domain must be active before using it in a roadmap template");
        }

        JobPosition jobPosition = jobPositionRepository.findById(request.getJobPositionId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Job position not found: " + request.getJobPositionId()));
        if (!Objects.equals(jobPosition.getDomainId(), request.getDomainId())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Job position does not belong to selected domain");
        }
        if (jobPosition.getStatus() != TaxonomyStatus.ACTIVE) {
            throw new ApiException(ErrorCode.CONFLICT, "Job position must be active before using it in a roadmap template");
        }

        JobPositionTrack track = jobPositionTrackRepository.findById(request.getJobPositionTrackId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Job position track not found: " + request.getJobPositionTrackId()));
        if (!Objects.equals(track.getJobPositionId(), request.getJobPositionId())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Job position track does not belong to selected job position");
        }
        if (track.getStatus() != TaxonomyStatus.ACTIVE) {
            throw new ApiException(ErrorCode.CONFLICT, "Job position track must be active before using it in a roadmap template");
        }

        Set<Long> allowedSkillIds = jobPositionTrackSkillRepository.findByTrackIdOrderBySortOrderAsc(track.getId()).stream()
                .map(JobPositionTrackSkill::getSkillId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (allowedSkillIds.isEmpty()) {
            throw new ApiException(ErrorCode.CONFLICT, "Job position track must define at least one skill first");
        }
        for (RoadmapTemplateNodeRequest node : defaultList(request.getNodes())) {
            if (node.getSkillId() == null) {
                continue;
            }
            if (!allowedSkillIds.contains(node.getSkillId())) {
                throw new ApiException(ErrorCode.BAD_REQUEST,
                        "Template node skill must belong to the selected job position track: " + node.getSkillId());
            }
        }
        for (RoadmapTemplateSkillBlockRequest block : defaultList(request.getSkillBlocks())) {
            if (!allowedSkillIds.contains(block.getSkillId())) {
                throw new ApiException(ErrorCode.BAD_REQUEST,
                        "Template skill block must belong to the selected job position track: " + block.getSkillId());
            }
        }
        for (RoadmapTemplateNodeGroupRequest group : defaultList(request.getNodeGroups())) {
            for (RoadmapTemplateNodeGroupSkillRequest skill : defaultList(group.getSkills())) {
                if (!allowedSkillIds.contains(skill.getSkillId())) {
                    throw new ApiException(ErrorCode.BAD_REQUEST,
                            "Node group skill must belong to the selected job position track: " + skill.getSkillId());
                }
            }
        }
    }

    private void validateTemplateCanPublish(RoadmapTemplate template) {
        List<RoadmapTemplateNodeGroup> nodeGroups = nodeGroupRepository.findByTemplateIdOrderByOrderIndexAscIdAsc(template.getId());
        if (!nodeGroups.isEmpty()) {
            List<String> errors = validatePersistedNodeGroupsTemplate(template, nodeGroups);
            if (!errors.isEmpty()) {
                throw new ApiException(ErrorCode.CONFLICT, String.join("; ", errors));
            }
            return;
        }

        List<RoadmapTemplateSkillBlock> blocks = skillBlockRepository.findByTemplateIdOrderByIdAsc(template.getId());
        if (!blocks.isEmpty()) {
            List<String> errors = validatePersistedV2Template(template, blocks);
            if (!errors.isEmpty()) {
                throw new ApiException(ErrorCode.CONFLICT, String.join("; ", errors));
            }
            return;
        }

        List<RoadmapTemplateNode> nodes = nodeRepository.findByTemplateIdOrderByOrderIndexAscIdAsc(template.getId());
        if (nodes.isEmpty()) {
            throw new ApiException(ErrorCode.CONFLICT, "Template must contain at least one node before publish");
        }
        if (template.getDomainId() == null || template.getJobPositionId() == null || template.getJobPositionTrackId() == null) {
            throw new ApiException(ErrorCode.CONFLICT, "Template must be linked to domain, job position, and job position track");
        }
        for (RoadmapTemplateNode node : nodes) {
            if (node.getSkillId() == null) {
                throw new ApiException(ErrorCode.CONFLICT, "Every template node must be linked to a track skill");
            }
            if (node.getExpectedOutput() == null || node.getExpectedOutput().isBlank()
                    || node.getRubric() == null || node.getRubric().isBlank()) {
                throw new ApiException(ErrorCode.CONFLICT,
                        "Every template node must define expected output and rubric before publish");
            }
        }
    }

    private RoadmapTemplateAllocationPreviewResponse allocateFromRequest(RoadmapTemplateRequest request) {
        List<RoadmapTemplateSkillBlockRequest> blocks = defaultList(request.getSkillBlocks());
        if (blocks.isEmpty()) {
            return RoadmapTemplateAllocationPreviewResponse.builder()
                    .totalNodeCount(request.getTotalNodeCount())
                    .allocatedNodeCount(0)
                    .valid(true)
                    .errors(List.of())
                    .items(List.of())
                    .build();
        }
        Map<Long, SkillPriority> priorities = skillPriorityByTrack(request.getJobPositionTrackId());
        List<AllocationInput> inputs = blocks.stream()
                .map(block -> allocationInput(
                        block.getSkillId(),
                        block.getSkillNameSnapshot(),
                        block.getWeightPercent(),
                        block.getMinNodes(),
                        block.getMaxNodes(),
                        block.getNodeCountOverride(),
                        priorities.get(block.getSkillId())))
                .toList();
        AllocationResult result = allocateInternal(request.getTotalNodeCount(), inputs);
        return toAllocationPreview(request.getTotalNodeCount(), result);
    }

    private AllocationResult allocateFromBlocks(Long jobPositionTrackId, Integer totalNodeCount, List<RoadmapTemplateSkillBlock> blocks) {
        Map<Long, SkillPriority> priorities = skillPriorityByTrack(jobPositionTrackId);
        List<AllocationInput> inputs = defaultList(blocks).stream()
                .map(block -> allocationInput(
                        block.getSkillId(),
                        block.getSkillNameSnapshot(),
                        block.getWeightPercent(),
                        block.getMinNodes(),
                        block.getMaxNodes(),
                        block.getNodeCountOverride(),
                        priorities.get(block.getSkillId())))
                .toList();
        return allocateInternal(totalNodeCount, inputs);
    }

    private AllocationInput allocationInput(
            Long skillId,
            String skillName,
            Double weightPercent,
            Integer minNodes,
            Integer maxNodes,
            Integer nodeCountOverride,
            SkillPriority priority) {
        Double normalizedWeightPercent = priority != null ? priority.weightPercent() : weightPercent;
        return new AllocationInput(
                skillId,
                firstNonBlank(skillName, priority != null ? priority.skillName() : null),
                normalizedWeightPercent,
                priority != null ? priority.requirementType() : null,
                priority != null ? priority.trackWeight() : null,
                priority != null ? priority.requirementMultiplier() : null,
                priority != null ? priority.effectiveWeight() : null,
                normalizedWeightPercent,
                minNodes,
                maxNodes,
                nodeCountOverride);
    }

    private AllocationResult allocateInternal(Integer requestedTotalNodeCount, List<AllocationInput> inputs) {
        List<String> errors = new ArrayList<>();
        int totalNodeCount = requestedTotalNodeCount != null ? requestedTotalNodeCount : 0;
        if (totalNodeCount <= 0) {
            errors.add("totalNodeCount must be greater than 0 for V2 templates");
        }
        if (inputs.isEmpty()) {
            errors.add("At least one skill block is required");
        }
        double totalWeight = inputs.stream().map(AllocationInput::weightPercent).filter(Objects::nonNull).mapToDouble(Double::doubleValue).sum();
        if (totalWeight <= 0) {
            errors.add("Total skill weight must be greater than 0");
        }

        Map<Long, Integer> allocated = new LinkedHashMap<>();
        int fixedTotal = 0;
        for (AllocationInput input : inputs) {
            if (input.nodeCountOverride() != null) {
                if (input.nodeCountOverride() < 0) {
                    errors.add("nodeCountOverride cannot be negative for skill " + input.skillId());
                }
                allocated.put(input.skillId(), Math.max(0, input.nodeCountOverride()));
                fixedTotal += Math.max(0, input.nodeCountOverride());
            }
            if (input.minNodes() != null && input.maxNodes() != null && input.minNodes() > input.maxNodes()) {
                errors.add("minNodes cannot be greater than maxNodes for skill " + input.skillId());
            }
        }
        int remaining = Math.max(0, totalNodeCount - fixedTotal);
        List<AllocationShare> shares = inputs.stream()
                .filter(input -> input.nodeCountOverride() == null)
                .map(input -> new AllocationShare(input, totalWeight > 0 ? remaining * safeWeight(input) / totalWeight : 0D))
                .sorted(Comparator.comparingDouble(AllocationShare::fraction).reversed())
                .toList();
        int floorTotal = 0;
        for (AllocationShare share : shares) {
            int base = (int) Math.floor(share.rawShare());
            allocated.put(share.input().skillId(), base);
            floorTotal += base;
        }
        int remainder = remaining - floorTotal;
        for (int i = 0; i < remainder && i < shares.size(); i++) {
            AllocationShare share = shares.get(i);
            allocated.compute(share.input().skillId(), (k, v) -> (v == null ? 0 : v) + 1);
        }

        for (AllocationInput input : inputs) {
            int value = allocated.getOrDefault(input.skillId(), 0);
            if (input.minNodes() != null && value < input.minNodes()) {
                errors.add("Allocated nodes below minNodes for skill " + input.skillId());
            }
            if (input.maxNodes() != null && value > input.maxNodes()) {
                errors.add("Allocated nodes above maxNodes for skill " + input.skillId());
            }
        }

        Map<Long, Double> rawBySkill = shares.stream()
                .collect(Collectors.toMap(s -> s.input().skillId(), AllocationShare::rawShare, (a, b) -> a, LinkedHashMap::new));
        List<RoadmapTemplateAllocationPreviewResponse.Item> items = inputs.stream()
                .map(input -> RoadmapTemplateAllocationPreviewResponse.Item.builder()
                        .skillId(input.skillId())
                        .skillName(input.skillName())
                        .weightPercent(input.weightPercent())
                        .requirementType(input.requirementType())
                        .trackWeight(input.trackWeight())
                        .requirementMultiplier(input.requirementMultiplier())
                        .effectiveWeight(input.effectiveWeight())
                        .normalizedWeightPercent(input.normalizedWeightPercent())
                        .minNodes(input.minNodes())
                        .maxNodes(input.maxNodes())
                        .nodeCountOverride(input.nodeCountOverride())
                        .allocatedNodes(allocated.getOrDefault(input.skillId(), 0))
                        .rawShare(input.nodeCountOverride() != null ? Double.valueOf(input.nodeCountOverride()) : rawBySkill.getOrDefault(input.skillId(), 0D))
                        .build())
                .toList();
        int allocatedTotal = items.stream().map(RoadmapTemplateAllocationPreviewResponse.Item::getAllocatedNodes)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        return new AllocationResult(errors.isEmpty(), errors, items);
    }

    private RoadmapTemplateAllocationPreviewResponse toAllocationPreview(Integer totalNodeCount, AllocationResult result) {
        int allocated = result.items().stream()
                .map(RoadmapTemplateAllocationPreviewResponse.Item::getAllocatedNodes)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        return RoadmapTemplateAllocationPreviewResponse.builder()
                .totalNodeCount(totalNodeCount)
                .allocatedNodeCount(allocated)
                .valid(result.valid())
                .errors(result.errors())
                .items(result.items())
                .build();
    }

    private void validateV2Content(
            RoadmapTemplateRequest request,
            List<String> errors,
            List<String> warnings,
            RoadmapTemplateAllocationPreviewResponse allocation) {
        if (request.getNodeGroups() != null && !request.getNodeGroups().isEmpty()) {
            validateNodeGroupRequests(request, errors, warnings);
            return;
        }

        List<ModuleSkillCoverage> moduleCoverages = new ArrayList<>();
        int moduleCount = defaultList(request.getSkillBlocks()).stream()
                .map(RoadmapTemplateSkillBlockRequest::getActivities)
                .filter(Objects::nonNull)
                .mapToInt(List::size)
                .sum();
        if (request.getTotalNodeCount() != null && request.getTotalNodeCount() > 0
                && moduleCount != request.getTotalNodeCount()) {
            errors.add("Total module count must equal totalNodeCount: totalNodeCount="
                    + request.getTotalNodeCount() + ", modules=" + moduleCount);
        }

        for (RoadmapTemplateSkillBlockRequest block : defaultList(request.getSkillBlocks())) {
            List<RoadmapTemplateActivityRequest> activities = defaultList(block.getActivities());
            if (activities.isEmpty()) {
                warnings.add("Skill priority has no module coverage yet: " + block.getSkillId());
            }
            for (RoadmapTemplateActivityRequest activity : activities) {
                List<NodeSkillRef> nodeSkills = parseActivitySkillRequirements(
                        activity.getSkillRequirementsJson(),
                        block.getSkillId(),
                        block.getSkillNameSnapshot(),
                        block.getSkillCanonicalKeySnapshot());
                moduleCoverages.add(new ModuleSkillCoverage(activity.getTitle(), nodeSkills));
                if (nodeSkills.isEmpty()) {
                    errors.add("Every module must include at least one skill: " + activity.getTitle());
                }
                if (nodeSkills.size() > 7) {
                    errors.add("Module has too many skills and should be split: " + activity.getTitle());
                }
                if (nodeSkills.size() == 1 && !isSpecialSingleSkillModule(activity.getTitle())) {
                    warnings.add("Module has only one skill; consider grouping related skills: " + activity.getTitle());
                }
                if (activity.getExpectedOutput() == null || activity.getExpectedOutput().isBlank()
                        || activity.getRubric() == null || activity.getRubric().isBlank()) {
                    errors.add("Every activity must define expectedOutput and rubric: " + activity.getTitle());
                }
                if (activity.getMinLevel() == null) {
                    errors.add("Every activity must define minLevel: " + activity.getTitle());
                }
                if (activity.getMinLevel() != null && activity.getMaxLevel() != null
                        && levelRank(activity.getMinLevel()) > levelRank(activity.getMaxLevel())) {
                    errors.add("Activity minLevel cannot be greater than maxLevel: " + activity.getTitle());
                }
            }
        }
        validateSkillCoverage(request.getJobPositionTrackId(), moduleCoverages, errors, warnings);
    }

    private void validateNodeGroupRequests(
            RoadmapTemplateRequest request,
            List<String> errors,
            List<String> warnings) {
        List<ModuleSkillCoverage> moduleCoverages = new ArrayList<>();
        List<RoadmapTemplateNodeGroupRequest> groups = defaultList(request.getNodeGroups());
        if (request.getTotalNodeCount() != null && request.getTotalNodeCount() > 0
                && groups.size() != request.getTotalNodeCount()) {
            errors.add("Total node group count must equal totalNodeCount: totalNodeCount="
                    + request.getTotalNodeCount() + ", nodeGroups=" + groups.size());
        }
        for (RoadmapTemplateNodeGroupRequest group : groups) {
            String title = firstNonBlank(group.getTitle(), group.getNodeKey(), "Module");
            List<NodeSkillRef> skills = defaultList(group.getSkills()).stream()
                    .map(skill -> new NodeSkillRef(
                            skill.getSkillId(),
                            skill.getSkillNameSnapshot(),
                            skill.getSkillCanonicalKeySnapshot(),
                            skill.getRequirementType() != null ? skill.getRequirementType().normalized() : RequirementType.REQUIRED))
                    .toList();
            moduleCoverages.add(new ModuleSkillCoverage(title, skills));
            if (group.getTitle() == null || group.getTitle().isBlank()) {
                errors.add("Every node group must define title");
            }
            if (skills.isEmpty()) {
                errors.add("Every node group must include at least one skill: " + title);
            }
            if (skills.size() > 7) {
                errors.add("Node group has too many skills and should be split: " + title);
            } else if (skills.size() > 5) {
                warnings.add("Node group has more than 5 skills; review scope: " + title);
            }
            if (skills.size() == 1 && !isSpecialSingleSkillModule(title)) {
                warnings.add("Node group has only one skill; consider grouping related skills: " + title);
            }
            if (group.getExpectedOutput() == null || group.getExpectedOutput().isBlank()) {
                errors.add("Every node group must define expectedOutput: " + title);
            }
            if (group.getCompletionCriteria() == null || group.getCompletionCriteria().isBlank()) {
                errors.add("Every node group must define completionCriteria: " + title);
            }
        }
        validateSkillCoverage(request.getJobPositionTrackId(), moduleCoverages, errors, warnings);
    }

    private List<String> validatePersistedNodeGroupsTemplate(
            RoadmapTemplate template,
            List<RoadmapTemplateNodeGroup> groups) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<ModuleSkillCoverage> moduleCoverages = new ArrayList<>();
        if (template.getTotalNodeCount() != null && template.getTotalNodeCount() > 0
                && groups.size() != template.getTotalNodeCount()) {
            errors.add("Total node group count must equal totalNodeCount: totalNodeCount="
                    + template.getTotalNodeCount() + ", nodeGroups=" + groups.size());
        }
        for (RoadmapTemplateNodeGroup group : groups) {
            List<RoadmapTemplateNodeGroupSkill> persistedSkills =
                    nodeGroupSkillRepository.findByNodeGroupIdOrderByOrderIndexAscIdAsc(group.getId());
            List<NodeSkillRef> skills = persistedSkills.stream()
                    .map(skill -> new NodeSkillRef(
                            skill.getSkillId(),
                            skill.getSkillNameSnapshot(),
                            skill.getSkillCanonicalKeySnapshot(),
                            skill.getRequirementType()))
                    .toList();
            moduleCoverages.add(new ModuleSkillCoverage(group.getTitle(), skills));
            if (group.getTitle() == null || group.getTitle().isBlank()) {
                errors.add("Every node group must define title");
            }
            if (skills.isEmpty()) {
                errors.add("Every node group must include at least one skill: " + group.getTitle());
            }
            if (skills.size() > 7) {
                errors.add("Node group has too many skills and should be split: " + group.getTitle());
            }
            if (group.getExpectedOutput() == null || group.getExpectedOutput().isBlank()) {
                errors.add("Every node group must define expectedOutput: " + group.getTitle());
            }
            if (group.getCompletionCriteria() == null || group.getCompletionCriteria().isBlank()) {
                errors.add("Every node group must define completionCriteria: " + group.getTitle());
            }
        }
        validateSkillCoverage(template.getJobPositionTrackId(), moduleCoverages, errors, warnings);
        return errors;
    }

    private List<String> validatePersistedV2Template(RoadmapTemplate template, List<RoadmapTemplateSkillBlock> blocks) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        RoadmapTemplateAllocationPreviewResponse preview = toAllocationPreview(
                template.getTotalNodeCount(),
                allocateFromBlocks(template.getJobPositionTrackId(), template.getTotalNodeCount(), blocks));
        errors.addAll(defaultList(preview.getErrors()));
        List<ModuleSkillCoverage> moduleCoverages = new ArrayList<>();
        int moduleCount = 0;
        for (RoadmapTemplateSkillBlock block : defaultList(blocks)) {
            List<RoadmapTemplateActivity> activities = activityRepository.findBySkillBlockIdOrderByOrderIndexAscIdAsc(block.getId());
            moduleCount += activities.size();
            if (activities.isEmpty()) {
                warnings.add("Skill priority has no module coverage yet: " + block.getSkillId());
            }
            for (RoadmapTemplateActivity activity : activities) {
                List<NodeSkillRef> nodeSkills = parseActivitySkillRequirements(
                        activity.getSkillRequirementsJson(),
                        block.getSkillId(),
                        block.getSkillNameSnapshot(),
                        block.getSkillCanonicalKeySnapshot());
                moduleCoverages.add(new ModuleSkillCoverage(activity.getTitle(), nodeSkills));
                if (nodeSkills.isEmpty()) {
                    errors.add("Every module must include at least one skill: " + activity.getTitle());
                }
                if (nodeSkills.size() > 7) {
                    errors.add("Module has too many skills and should be split: " + activity.getTitle());
                }
                if (activity.getExpectedOutput() == null || activity.getExpectedOutput().isBlank()
                        || activity.getRubric() == null || activity.getRubric().isBlank()) {
                    errors.add("Every activity must define expectedOutput and rubric: " + activity.getTitle());
                }
                if (activity.getMinLevel() == null) {
                    errors.add("Every activity must define minLevel: " + activity.getTitle());
                }
                if (activity.getMinLevel() != null && activity.getMaxLevel() != null
                        && levelRank(activity.getMinLevel()) > levelRank(activity.getMaxLevel())) {
                    errors.add("Activity minLevel cannot be greater than maxLevel: " + activity.getTitle());
                }
            }
        }
        if (template.getTotalNodeCount() != null && template.getTotalNodeCount() > 0
                && moduleCount != template.getTotalNodeCount()) {
            errors.add("Total module count must equal totalNodeCount: totalNodeCount="
                    + template.getTotalNodeCount() + ", modules=" + moduleCount);
        }
        validateSkillCoverage(template.getJobPositionTrackId(), moduleCoverages, errors, warnings);
        return errors;
    }

    private record AllocationInput(
            Long skillId,
            String skillName,
            Double weightPercent,
            RequirementType requirementType,
            Integer trackWeight,
            Double requirementMultiplier,
            Double effectiveWeight,
            Double normalizedWeightPercent,
            Integer minNodes,
            Integer maxNodes,
            Integer nodeCountOverride
    ) {
    }

    private record AllocationShare(AllocationInput input, Double rawShare) {
        double fraction() {
            double raw = rawShare != null ? rawShare : 0D;
            return raw - Math.floor(raw);
        }
    }

    private record NodeSkillRef(Long skillId, String skillName, String canonicalKey, RequirementType requirementType) {
    }

    private record ModuleSkillCoverage(String moduleTitle, List<NodeSkillRef> skills) {
    }

    private List<NodeSkillRef> parseActivitySkillRequirements(
            String skillRequirementsJson,
            Long fallbackSkillId,
            String fallbackSkillName,
            String fallbackCanonicalKey) {
        List<NodeSkillRef> parsed = new ArrayList<>();
        if (skillRequirementsJson != null && !skillRequirementsJson.isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(skillRequirementsJson);
                if (root != null && root.isArray()) {
                    for (JsonNode item : root) {
                        if (item == null || item.isNull()) {
                            continue;
                        }
                        Long skillId = readLong(item, "skill_id", "skillId");
                        String skillName = readText(item, "skill_name", "skillName", "name");
                        String canonicalKey = readText(item, "canonical_key", "canonicalKey");
                        if (skillId == null && skillName == null && canonicalKey == null) {
                            continue;
                        }
                        parsed.add(new NodeSkillRef(
                                skillId,
                                skillName,
                                canonicalKey,
                                RequirementType.fromValue(readText(item, "requirement_type", "requirementType", "importance"))));
                    }
                }
            } catch (Exception ignored) {
                log.warn("Invalid module skillRequirementsJson; using fallback skill {}", fallbackSkillId);
            }
        }
        if (parsed.isEmpty() && fallbackSkillId != null) {
            parsed.add(new NodeSkillRef(fallbackSkillId, fallbackSkillName, fallbackCanonicalKey, RequirementType.REQUIRED));
        }
        return parsed;
    }

    private void validateSkillCoverage(
            Long trackId,
            List<ModuleSkillCoverage> modules,
            List<String> errors,
            List<String> warnings) {
        if (trackId == null) {
            return;
        }
        List<JobPositionTrackSkill> trackSkills = jobPositionTrackSkillRepository.findByTrackIdOrderBySortOrderAsc(trackId);
        Set<Long> coveredSkillIds = defaultList(modules).stream()
                .flatMap(module -> defaultList(module.skills()).stream())
                .map(NodeSkillRef::skillId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (JobPositionTrackSkill trackSkill : defaultList(trackSkills)) {
            if (trackSkill.getSkillId() == null) {
                continue;
            }
            RequirementType requirementType = trackSkill.getRequirementType() != null
                    ? trackSkill.getRequirementType().normalized()
                    : RequirementType.REQUIRED;
            if (requirementType == RequirementType.REQUIRED && !coveredSkillIds.contains(trackSkill.getSkillId())) {
                errors.add("Required skill is not covered by any module: " + trackSkill.getSkillId());
            } else if (requirementType == RequirementType.IMPORTANT && !coveredSkillIds.contains(trackSkill.getSkillId())) {
                warnings.add("Important skill is not covered by any module and should be reviewed: "
                        + trackSkill.getSkillId());
            } else if (requirementType == RequirementType.NICE_TO_HAVE && !coveredSkillIds.contains(trackSkill.getSkillId())) {
                warnings.add("Nice-to-have skill is not covered and can be omitted if roadmap is already long: "
                        + trackSkill.getSkillId());
            }
        }

        long singleSkillModules = defaultList(modules).stream()
                .filter(module -> defaultList(module.skills()).size() == 1)
                .count();
        if (modules.size() >= 3 && singleSkillModules >= Math.ceil(modules.size() * 0.7D)) {
            warnings.add("Roadmap looks like a skill checklist. Group related skills into practical learning modules.");
        }
    }

    private Long readLong(JsonNode node, String... fieldNames) {
        if (node == null || fieldNames == null) {
            return null;
        }
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (value == null || value.isMissingNode() || value.isNull()) {
                continue;
            }
            if (value.isNumber()) {
                return value.asLong();
            }
            String text = value.asText(null);
            if (text == null || text.isBlank()) {
                continue;
            }
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String readText(JsonNode node, String... fieldNames) {
        if (node == null || fieldNames == null) {
            return null;
        }
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (value != null && !value.isMissingNode() && !value.isNull()) {
                String text = value.asText(null);
                if (text != null && !text.isBlank()) {
                    return text.trim();
                }
            }
        }
        return null;
    }

    private boolean isSpecialSingleSkillModule(String title) {
        String normalized = title == null ? "" : title.toLowerCase();
        return normalized.contains("testing basics")
                || normalized.contains("kiem thu co ban")
                || normalized.contains("onboarding")
                || normalized.contains("capstone");
    }

    private List<RoadmapNodeAssignment> buildRuntimeAssignments(Journey journey, RoadmapSession session, List<RoadmapTemplateNode> nodes) {
        return nodes.stream()
                .map(node -> RoadmapNodeAssignment.builder()
                        .journeyId(journey.getId())
                        .roadmapSessionId(session.getId())
                        .nodeId(resolveNodeId(node))
                        .nodeSkillId(node.getSkillId())
                        .roadmapTemplateNodeId(node.getId())
                        .assignmentSource(RoadmapNodeAssignment.AssignmentSource.TEMPLATE)
                        .title(node.getTitle())
                        .description(node.getDescription())
                        .expectedOutput(node.getExpectedOutput())
                        .rubric(node.getRubric())
                        .createdBy(journey.getUser().getId())
                        .build())
                .toList();
    }

    private List<UserRoadmapProgress> buildInitialProgress(RoadmapSession session, List<RoadmapTemplateNode> nodes) {
        return nodes.stream()
                .map(node -> UserRoadmapProgress.builder()
                        .roadmapSession(session)
                        .questId(resolveNodeId(node))
                        .status(UserRoadmapProgress.ProgressStatus.NOT_STARTED)
                        .progress(0)
                        .build())
                .toList();
    }

    private List<RoadmapNodeAssignment> buildRuntimeAssignmentsFromRuntime(Journey journey, RoadmapSession session, List<RuntimeRoadmapNode> nodes) {
        return nodes.stream()
                .map(node -> RoadmapNodeAssignment.builder()
                        .journeyId(journey.getId())
                        .roadmapSessionId(session.getId())
                        .nodeId(node.id())
                        .nodeSkillId(node.skillId())
                        .roadmapTemplateNodeId(null)
                        .assignmentSource(RoadmapNodeAssignment.AssignmentSource.TEMPLATE)
                        .title(node.title())
                        .description(formatExercisesToMarkdown(node.practicalExercises(), node.description()))
                        .expectedOutput(node.expectedOutput())
                        .rubric(node.rubric())
                        .createdBy(journey.getUser().getId())
                        .build())
                .toList();
    }

    private String formatExercisesToMarkdown(List<String> exercises, String fallbackDescription) {
        if (exercises == null || exercises.isEmpty()) {
            return fallbackDescription != null ? fallbackDescription : "";
        }
        return exercises.stream()
                .map(ex -> "- " + ex.trim())
                .collect(Collectors.joining("\n"));
    }

    private List<UserRoadmapProgress> buildInitialProgressFromRuntime(RoadmapSession session, List<RuntimeRoadmapNode> nodes) {
        return nodes.stream()
                .map(node -> UserRoadmapProgress.builder()
                        .roadmapSession(session)
                        .questId(node.id())
                        .status(UserRoadmapProgress.ProgressStatus.NOT_STARTED)
                        .progress(0)
                        .build())
                .toList();
    }

    @Transactional
    public Long saveRoadmapSessionAndData(
            RoadmapSession session,
            Journey journey,
            List<RoadmapNodeAssignment> assignments,
            List<UserRoadmapProgress> progresses,
            Long templateId,
            Long trackId,
            String focusSkillIdsJson) {
        
        RoadmapSession saved = roadmapSessionRepository.save(session);
        
        for (RoadmapNodeAssignment a : assignments) {
            a.setRoadmapSessionId(saved.getId());
        }
        assignmentRepository.saveAll(assignments);
        
        for (UserRoadmapProgress p : progresses) {
            p.setRoadmapSession(saved);
        }
        progressRepository.saveAll(progresses);
        
        journey.setRoadmapTemplateId(templateId);
        journey.setJobPositionTrackId(trackId);
        journey.setFocusSkillIdsJson(focusSkillIdsJson);
        journeyRepository.save(journey);
        
        return saved.getId();
    }


    private String buildRoadmapJson(RoadmapTemplate template, List<RoadmapTemplateNode> nodes) {
        return buildRoadmapJson(template, nodes, null, null, List.of(), List.of());
    }

    private String buildRoadmapJson(RoadmapTemplate template, List<RoadmapTemplateNode> nodes,
                                    Journey journey, TestResult testResult,
                                    List<Map<String, Object>> skillGaps,
                                    List<Map<String, Object>> strengths) {
        String studentLevel = resolveStudentLevel(journey, testResult);
        Set<String> gapNames = extractProfileSkillNames(skillGaps);
        Set<String> strengthNames = extractProfileSkillNames(strengths);
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode metadata = root.putObject("roadmap_metadata");
        metadata.put("title", template.getTitle());
        metadata.put("original_goal", template.getTitle());
        metadata.put("validated_goal", firstNonBlank(template.getDescription(), template.getTitle()));
        metadata.put("duration", "Admin template guided");
        metadata.put("experience_level", studentLevel);
        metadata.put("learning_style", "Assessment-aware admin template");
        metadata.put("difficulty_level", resolveDominantDifficulty(nodes));
        metadata.put("roadmap_type", "career");
        metadata.put("target", resolveRuntimeTarget(template, journey));
        metadata.put("final_objective", "Complete admin-defined job-position roadmap with verified evidence");
        metadata.put("roadmap_mode", GENERATION_MODE_TEMPLATE_GUIDED);
        metadata.put("current_level", studentLevel);
        metadata.put("desired_duration", "Admin template guided");
        if (testResult != null) {
            metadata.put("assessment_score", testResult.getScorePercentage());
        }

        Map<Long, String> nodeIdsByTemplateId = nodes.stream()
                .collect(Collectors.toMap(RoadmapTemplateNode::getId, this::resolveNodeId, (a, b) -> a, LinkedHashMap::new));
        ArrayNode roadmap = root.putArray("roadmap");
        for (RoadmapTemplateNode node : nodes) {
            ObjectNode n = roadmap.addObject();
            String nodeId = resolveNodeId(node);
            String parentId = node.getParentNodeId() != null ? nodeIdsByTemplateId.get(node.getParentNodeId()) : null;
            boolean gapMatched = profileMatchesNode(gapNames, node);
            boolean strengthMatched = profileMatchesNode(strengthNames, node);
            n.put("id", nodeId);
            n.put("title", node.getTitle());
            n.put("description", personalizeDescription(node, studentLevel, gapMatched, strengthMatched));
            n.put("estimated_time_minutes", personalizeMinutes(node, studentLevel, gapMatched));
            n.put("type", "MAIN");
            n.put("difficulty", personalizeDifficulty(node, studentLevel, gapMatched, strengthMatched));
            n.put("order_index", node.getOrderIndex());
            n.put("main_path_index", node.getOrderIndex());
            n.put("is_core", true);
            if (parentId != null) {
                n.put("parent_id", parentId);
            } else {
                n.putNull("parent_id");
            }
            n.put("node_status", node.getOrderIndex() != null && node.getOrderIndex() == 1 ? "AVAILABLE" : "LOCKED");
            putArray(n, "learning_objectives", personalizedObjectives(node, studentLevel, gapMatched));
            putArray(n, "practical_exercises", personalizedExercises(node, studentLevel, gapMatched));
            putArray(n, "success_criteria", personalizedSuccessCriteria(node));
            putArray(n, "suggested_resources", List.of());
            putArray(n, "key_concepts", personalizedKeyConcepts(node, studentLevel));
            putArray(n, "prerequisites", parentId != null ? List.of(parentId) : List.of());
            putArray(n, "children", childrenOf(node, nodes, nodeIdsByTemplateId));
            n.put("importance_score", importanceScore(node));
            n.put("confidence_score", 0.9);
            n.put("reason", personalizationReason(node, studentLevel, gapMatched, strengthMatched));
            putArray(n, "evidence", List.of("admin_template", "assessment_profile", "job_position_track"));
            n.put("importance_validation_status", "ACCEPTED");
        }

        ObjectNode stats = root.putObject("roadmap_statistics");
        stats.put("total_nodes", nodes.size());
        stats.put("main_nodes", nodes.size());
        stats.put("side_nodes", 0);
        stats.put("total_estimated_hours", nodes.stream().map(RoadmapTemplateNode::getEstimatedHours).filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue).sum());
        ObjectNode distribution = stats.putObject("difficulty_distribution");
        nodes.stream().map(n -> firstNonBlank(n.getDifficulty(), "medium"))
                .collect(Collectors.groupingBy(d -> d, LinkedHashMap::new, Collectors.counting()))
                .forEach((difficulty, count) -> distribution.put(difficulty, count.intValue()));

        putArray(root, "learning_tips", List.of("Follow the admin-defined sequence and submit evidence for each node."));
        putArray(root, "warnings", List.of());
        root.putObject("overview")
                .put("purpose", "Admin template with AI-personalized learning guidance")
                .put("audience", "Student targeting a specific job position track")
                .put("post_roadmap_state", "Ready for job-position competency verification");
        return root.toString();
    }

    private String buildEnrichedRoadmapJson(
            RoadmapTemplate template,
            List<EnrichedRuntimeNodeV1> enrichedNodes,
            Journey journey,
            TestResult testResult,
            List<Map<String, Object>> skillGaps,
            List<Map<String, Object>> strengths) {
        String studentLevel = resolveStudentLevel(journey, testResult);
        Set<String> gapNames = extractProfileSkillNames(skillGaps);
        Set<String> strengthNames = extractProfileSkillNames(strengths);
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode metadata = root.putObject("roadmap_metadata");
        metadata.put("title", template.getTitle());
        metadata.put("original_goal", template.getTitle());
        metadata.put("validated_goal", firstNonBlank(template.getDescription(), template.getTitle()));
        metadata.put("duration", "Admin template guided");
        metadata.put("experience_level", studentLevel);
        metadata.put("learning_style", "Assessment-aware admin template");
        metadata.put("difficulty_level", resolveDominantDifficulty(enrichedNodes.stream().map(EnrichedRuntimeNodeV1::node).toList()));
        metadata.put("roadmap_type", "career");
        metadata.put("target", resolveRuntimeTarget(template, journey));
        metadata.put("final_objective", "Complete admin-defined job-position roadmap with verified evidence");
        metadata.put("roadmap_mode", GENERATION_MODE_TEMPLATE_GUIDED);
        metadata.put("current_level", studentLevel);
        metadata.put("desired_duration", "Admin template guided");
        if (testResult != null) {
            metadata.put("assessment_score", testResult.getScorePercentage());
        }

        List<RoadmapTemplateNode> nodes = enrichedNodes.stream().map(EnrichedRuntimeNodeV1::node).toList();
        Map<Long, String> nodeIdsByTemplateId = nodes.stream()
                .collect(Collectors.toMap(RoadmapTemplateNode::getId, this::resolveNodeId, (a, b) -> a, LinkedHashMap::new));
        ArrayNode roadmap = root.putArray("roadmap");
        for (EnrichedRuntimeNodeV1 ern : enrichedNodes) {
            RoadmapTemplateNode node = ern.node();
            RoadmapNodeAiEnrichmentService.EnrichedNode enriched = ern.enriched();
            ObjectNode n = roadmap.addObject();
            String nodeId = resolveNodeId(node);
            String parentId = node.getParentNodeId() != null ? nodeIdsByTemplateId.get(node.getParentNodeId()) : null;
            boolean gapMatched = profileMatchesNode(gapNames, node);
            boolean strengthMatched = profileMatchesNode(strengthNames, node);
            n.put("id", nodeId);
            n.put("title", node.getTitle());
            n.put("description", enriched.getDescription());
            n.put("estimated_time_minutes", ern.estimatedMinutes());
            n.put("type", "MAIN");
            n.put("difficulty", ern.difficulty());
            n.put("order_index", node.getOrderIndex());
            n.put("main_path_index", node.getOrderIndex());
            n.put("is_core", true);
            if (parentId != null) {
                n.put("parent_id", parentId);
            } else {
                n.putNull("parent_id");
            }
            n.put("node_status", node.getOrderIndex() != null && node.getOrderIndex() == 1 ? "AVAILABLE" : "LOCKED");
            putArray(n, "learning_objectives", enriched.getLearningObjectives());
            putArray(n, "practical_exercises", enriched.getPracticalExercises());
            putArray(n, "success_criteria", enriched.getSuccessCriteria());

            ArrayNode lessonsArr = n.putArray("lessons");
            if (enriched.getLessons() != null) {
                for (RoadmapNodeAiEnrichmentService.EnrichedLesson lesson : enriched.getLessons()) {
                    ObjectNode les = lessonsArr.addObject();
                    les.put("title", lesson.getTitle());
                    les.put("description", lesson.getDescription());
                    les.put("learningObjective", lesson.getLearningObjective());
                    if (lesson.getEstimatedMinutes() != null) {
                        les.put("estimatedMinutes", lesson.getEstimatedMinutes());
                    } else {
                        les.putNull("estimatedMinutes");
                    }
                }
            }

            putArray(n, "suggested_resources", List.of());
            putArray(n, "key_concepts", personalizedKeyConcepts(node, studentLevel));
            putArray(n, "prerequisites", parentId != null ? List.of(parentId) : List.of());
            putArray(n, "children", childrenOf(node, nodes, nodeIdsByTemplateId));
            n.put("importance_score", importanceScore(node));
            n.put("confidence_score", 0.9);
            n.put("reason", personalizationReason(node, studentLevel, gapMatched, strengthMatched));
            putArray(n, "evidence", List.of("admin_template", "assessment_profile", "job_position_track"));
            n.put("importance_validation_status", "ACCEPTED");
        }

        ObjectNode stats = root.putObject("roadmap_statistics");
        stats.put("total_nodes", nodes.size());
        stats.put("main_nodes", nodes.size());
        stats.put("side_nodes", 0);
        stats.put("total_estimated_hours", nodes.stream().map(RoadmapTemplateNode::getEstimatedHours).filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue).sum());
        ObjectNode distribution = stats.putObject("difficulty_distribution");
        nodes.stream().map(n -> firstNonBlank(n.getDifficulty(), "medium"))
                .collect(Collectors.groupingBy(d -> d, LinkedHashMap::new, Collectors.counting()))
                .forEach((difficulty, count) -> distribution.put(difficulty, count.intValue()));

        putArray(root, "learning_tips", List.of("Follow the admin-defined sequence and submit evidence for each node."));
        putArray(root, "warnings", List.of());
        root.putObject("overview")
                .put("purpose", "Admin template with AI-personalized learning guidance")
                .put("audience", "Student targeting a specific job position track")
                .put("post_roadmap_state", "Ready for job-position competency verification");
        return root.toString();
    }

    private List<RuntimeRoadmapNode> buildInitialRuntimeNodesFromSkillBlocks(
            RoadmapTemplate template,
            List<RoadmapTemplateSkillBlock> blocks,
            Journey journey,
            TestResult testResult) {
        Map<Long, List<Long>> manualCoursesBySkill = courseRepository.findByTemplateIdOrderByDisplayOrderAscIdAsc(template.getId()).stream()
                .filter(course -> course.getSkillId() != null)
                .collect(Collectors.groupingBy(
                        RoadmapTemplateCourse::getSkillId,
                        LinkedHashMap::new,
                        Collectors.mapping(RoadmapTemplateCourse::getCourseId, Collectors.toList())));

        String studentLevel = resolveStudentLevel(journey, testResult);
        Journey.SkillLevel studentSkillLevel = resolveStudentSkillLevel(journey, testResult);
        List<RuntimeRoadmapNode> runtimeNodes = new ArrayList<>();
        for (RoadmapTemplateSkillBlock block : blocks) {
            List<RoadmapTemplateActivity> activities = activityRepository.findBySkillBlockIdOrderByOrderIndexAscIdAsc(block.getId());
            List<Long> suggestedCourseIds = resolveSuggestedCourseIds(block, manualCoursesBySkill.get(block.getSkillId()));
            for (RoadmapTemplateActivity activity : activities) {
                boolean targetLevelMatch = activity == null || activityMatchesLevel(activity, studentSkillLevel);
                String title = activity != null
                        ? activity.getTitle()
                        : firstNonBlank(block.getSkillNameSnapshot(), "Skill") + " module";
                int moduleIndex = runtimeNodes.size() + 1;
                String nodeId = "module-" + moduleIndex;
                runtimeNodes.add(new RuntimeRoadmapNode(
                        nodeId,
                        block.getId(),
                        block.getSkillId(),
                        block.getSkillNameSnapshot(),
                        title,
                        buildRuntimeDescription(block, activity, studentLevel),
                        firstNonBlank(activity != null ? activity.getExpectedOutput() : null, block.getSuccessCriteria(), block.getLearningGoals()),
                        firstNonBlank(activity != null ? activity.getRubric() : null, block.getSuccessCriteria()),
                        firstNonBlank(activity != null ? activity.getDifficulty() : null, "medium"),
                        activity != null ? activity.getMinLevel() : null,
                        activity != null ? activity.getMaxLevel() : null,
                        targetLevelMatch,
                        activity != null ? "activity_blueprint" : "skill_block_fallback",
                        buildRuntimeReason(block, activity, studentSkillLevel, targetLevelMatch),
                        activity != null ? activity.getEstimatedHours() : null,
                        suggestedCourseIds,
                        activity != null ? activity.getSkillRequirementsJson() : null,
                        null, null, null, null,
                        "MAIN", null, null
                ));
            }
        }
        return runtimeNodes;
    }

    private String formatRubricToMarkdown(String rubricJson) {
        if (rubricJson == null || rubricJson.isBlank()) {
            return "";
        }
        String trimmed = rubricJson.trim();
        if (!trimmed.startsWith("[") && !trimmed.startsWith("{")) {
            return rubricJson;
        }
        try {
            JsonNode root = objectMapper.readTree(trimmed);
            if (root.isArray() && root.size() > 0) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode item : root) {
                    String name = item.path("name").asText("Tiêu chí");
                    String desc = item.path("description").asText("");
                    int points = item.path("maxPoints").asInt(10);
                    sb.append("- **").append(name).append(" (").append(points).append("đ)**: ").append(desc).append("\n");
                }
                return sb.toString().trim();
            }
        } catch (Exception e) {
            log.warn("Failed to parse rubric JSON: {}", rubricJson, e);
        }
        return rubricJson;
    }

    private List<RuntimeRoadmapNode> buildInitialRuntimeNodesFromNodeGroups(
            RoadmapTemplate template,
            List<RoadmapTemplateNodeGroup> groups) {
        Map<Long, List<Long>> manualCoursesBySkill = courseRepository.findByTemplateIdOrderByDisplayOrderAscIdAsc(template.getId()).stream()
                .filter(course -> course.getSkillId() != null)
                .collect(Collectors.groupingBy(
                        RoadmapTemplateCourse::getSkillId,
                        LinkedHashMap::new,
                        Collectors.mapping(RoadmapTemplateCourse::getCourseId, Collectors.toList())));
        List<RuntimeRoadmapNode> runtimeNodes = new ArrayList<>();
        for (RoadmapTemplateNodeGroup group : defaultList(groups)) {
            List<RoadmapTemplateNodeGroupSkill> skills =
                    nodeGroupSkillRepository.findByNodeGroupIdOrderByOrderIndexAscIdAsc(group.getId());
            RoadmapTemplateNodeGroupSkill primarySkill = skills.isEmpty() ? null : skills.get(0);
            List<Long> suggestedCourseIds = skills.stream()
                    .map(RoadmapTemplateNodeGroupSkill::getSkillId)
                    .filter(Objects::nonNull)
                    .flatMap(skillId -> defaultList(manualCoursesBySkill.get(skillId)).stream())
                    .distinct()
                    .toList();

            String exInstruction = group.getDescription();
            String exExpectedOutput = group.getExpectedOutput();
            String exRubric = group.getRubric();
            List<String> listPracticalExercises = new ArrayList<>();
            List<String> listSuccessCriteria = new ArrayList<>();

            if (group.getExercisesJson() != null && !group.getExercisesJson().isBlank()) {
                try {
                    JsonNode exercisesArr = objectMapper.readTree(group.getExercisesJson());
                    if (exercisesArr.isArray() && exercisesArr.size() > 0) {
                        JsonNode ex = exercisesArr.get(0);
                        if (ex.has("instruction") && !ex.path("instruction").asText().isBlank()) {
                            exInstruction = ex.path("instruction").asText();
                            listPracticalExercises.add(exInstruction);
                        }
                        if (ex.has("expectedOutput") && !ex.path("expectedOutput").asText().isBlank()) {
                            exExpectedOutput = ex.path("expectedOutput").asText();
                        }
                        if (ex.has("rubric") && !ex.path("rubric").asText().isBlank()) {
                            String rawRubric = ex.path("rubric").asText();
                            exRubric = formatRubricToMarkdown(rawRubric);
                            
                            String trimmedRubric = rawRubric.trim();
                            if (!trimmedRubric.startsWith("[") && !trimmedRubric.startsWith("{")) {
                                listSuccessCriteria.add(rawRubric);
                            } else {
                                try {
                                    JsonNode rubricArr = objectMapper.readTree(trimmedRubric);
                                    if (rubricArr.isArray()) {
                                        for (JsonNode crit : rubricArr) {
                                            String critName = crit.path("name").asText("");
                                            String critDesc = crit.path("description").asText("");
                                            int maxPts = crit.path("maxPoints").asInt(10);
                                            if (!critDesc.isBlank()) {
                                                listSuccessCriteria.add(critName + " (" + maxPts + "đ): " + critDesc);
                                            } else if (!critName.isBlank()) {
                                                listSuccessCriteria.add(critName + " (" + maxPts + "đ)");
                                            }
                                        }
                                    }
                                } catch (Exception ignored) {
                                    listSuccessCriteria.add(rawRubric);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse exercisesJson for node group template ID: {}", group.getId(), e);
                }
            }

            if (listSuccessCriteria.isEmpty() && group.getCompletionCriteria() != null && !group.getCompletionCriteria().isBlank()) {
                listSuccessCriteria.addAll(splitTemplateText(group.getCompletionCriteria()));
            }

            List<RoadmapNodeAiEnrichmentService.EnrichedLesson> initialLessons = new ArrayList<>();
            if (group.getLessonsJson() != null && !group.getLessonsJson().isBlank()) {
                try {
                    JsonNode originalArr = objectMapper.readTree(group.getLessonsJson());
                    if (originalArr.isArray()) {
                        for (JsonNode item : originalArr) {
                            initialLessons.add(new RoadmapNodeAiEnrichmentService.EnrichedLesson(
                                    item.path("title").asText(""),
                                    item.path("description").asText(""),
                                    item.path("learningObjective").asText(""),
                                    item.has("estimatedMinutes") ? item.path("estimatedMinutes").asInt(60) : null
                            ));
                        }
                    }
                } catch (Exception ignored) {}
            }

            int moduleIndex = runtimeNodes.size() + 1;
            runtimeNodes.add(new RuntimeRoadmapNode(
                    firstNonBlank(group.getNodeKey(), "module-" + moduleIndex),
                    null,
                    primarySkill != null ? primarySkill.getSkillId() : null,
                    primarySkill != null ? primarySkill.getSkillNameSnapshot() : null,
                    group.getTitle(),
                    firstNonBlank(exInstruction, group.getDescription(), "Module combines related skills into a practical learning topic."),
                    firstNonBlank(exExpectedOutput, group.getExpectedOutput(), group.getLearningObjectives(), group.getTitle()),
                    firstNonBlank(exRubric, group.getRubric(), group.getCompletionCriteria()),
                    firstNonBlank(group.getDifficulty(), "medium"),
                    null,
                    null,
                    true,
                    "node_group",
                    "Admin module template groups related skills into one learning node.",
                    group.getEstimatedHours(),
                    suggestedCourseIds,
                    buildNodeGroupSkillRequirementsJson(skills),
                    group.getLearningObjectives() != null && !group.getLearningObjectives().isBlank()
                            ? List.of(group.getLearningObjectives().split("\n"))
                            : null,
                    listPracticalExercises.isEmpty() ? null : listPracticalExercises,
                    listSuccessCriteria.isEmpty() ? null : listSuccessCriteria,
                    group.getPinnedDocumentIds(),
                    group.getNodeType() != null ? group.getNodeType() : "MAIN",
                    group.getParentNodeKey(),
                    initialLessons
            ));
        }
        return runtimeNodes;
    }

    private RuntimeRoadmap buildRuntimeRoadmapFromSkillBlocks(
            RoadmapTemplate template,
            List<RoadmapTemplateSkillBlock> blocks,
            Journey journey,
            TestResult testResult,
            List<Map<String, Object>> skillGaps,
            List<Map<String, Object>> strengths) {

        List<RuntimeRoadmapNode> initialNodes = buildInitialRuntimeNodesFromSkillBlocks(template, blocks, journey, testResult);
        String studentLevel = resolveStudentLevel(journey, testResult);
        Set<String> gapNames = extractProfileSkillNames(skillGaps);
        Set<String> strengthNames = extractProfileSkillNames(strengths);
        
        List<CompletableFuture<RuntimeRoadmapNode>> futures = new ArrayList<>();
        for (int i = 0; i < initialNodes.size(); i++) {
            RuntimeRoadmapNode node = initialNodes.get(i);
            boolean gapMatched = false;
            boolean strengthMatched = false;
            if (node.skillName() != null) {
                String normSkill = node.skillName().toLowerCase();
                boolean finalGapMatched = gapNames.stream().anyMatch(val -> normSkill.contains(val) || val.contains(normSkill));
                boolean finalStrengthMatched = strengthNames.stream().anyMatch(val -> normSkill.contains(val) || val.contains(normSkill));
                gapMatched = finalGapMatched;
                strengthMatched = finalStrengthMatched;
            }

            final boolean isGap = gapMatched;
            final boolean isStrength = strengthMatched;

            CompletableFuture<RuntimeRoadmapNode> future = CompletableFuture.supplyAsync(() -> {
                RoadmapNodeAiEnrichmentService.EnrichedNode enriched = nodeAiEnrichmentService.enrichNode(
                        node.title(),
                        node.description(),
                        node.expectedOutput(),
                        node.rubric(),
                        node.skillName(),
                        studentLevel,
                        journey.getGoal(),
                        isGap,
                        isStrength,
                        node.pinnedDocumentIds()
                );

                String expectedOutputVal = (enriched.getExpectedOutput() != null && !enriched.getExpectedOutput().isBlank())
                        ? enriched.getExpectedOutput()
                        : node.expectedOutput();

                String rubricVal = (enriched.getRubric() != null && !enriched.getRubric().isBlank())
                        ? enriched.getRubric()
                        : node.rubric();

                List<String> practicalExercisesVal = (enriched.getPracticalExercises() != null && !enriched.getPracticalExercises().isEmpty())
                        ? enriched.getPracticalExercises()
                        : node.practicalExercises();

                List<String> successCriteriaVal = (enriched.getSuccessCriteria() != null && !enriched.getSuccessCriteria().isEmpty())
                        ? enriched.getSuccessCriteria()
                        : node.successCriteria();

                List<RoadmapNodeAiEnrichmentService.EnrichedLesson> lessonsVal = (enriched.getLessons() != null && !enriched.getLessons().isEmpty())
                        ? enriched.getLessons()
                        : node.lessons();

                return new RuntimeRoadmapNode(
                        node.id(),
                        node.templateSkillBlockId(),
                        node.skillId(),
                        node.skillName(),
                        node.title(),
                        enriched.getDescription(),
                        expectedOutputVal,
                        rubricVal,
                        node.difficulty(),
                        node.minLevel(),
                        node.maxLevel(),
                        node.targetLevelMatch(),
                        node.activitySource(),
                        node.reason(),
                        node.estimatedHours(),
                        node.suggestedCourseIds(),
                        node.skillRequirementsJson(),
                        enriched.getLearningObjectives(),
                        practicalExercisesVal,
                        successCriteriaVal,
                        node.pinnedDocumentIds(),
                        node.nodeType(),
                        node.parentNodeKey(),
                        lessonsVal
                );
            }, roadmapEnrichmentTaskExecutor);

            futures.add(future);
        }

        // Chờ tất cả hoàn thành song song (ThreadPoolTaskExecutor khống chế 3 luồng đồng thời)
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<RuntimeRoadmapNode> enrichedNodes = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

        return buildRuntimeRoadmapJson(template, enrichedNodes, studentLevel, "Admin V3 template guided",
                "Assessment-aware admin template allocation");
    }

    private RuntimeRoadmap buildRuntimeRoadmapFromNodeGroups(
            RoadmapTemplate template,
            List<RoadmapTemplateNodeGroup> groups,
            Journey journey,
            TestResult testResult,
            List<Map<String, Object>> skillGaps,
            List<Map<String, Object>> strengths) {

        List<RuntimeRoadmapNode> initialNodes = buildInitialRuntimeNodesFromNodeGroups(template, groups);
        String studentLevel = resolveStudentLevel(journey, testResult);
        Set<String> gapNames = extractProfileSkillNames(skillGaps);
        Set<String> strengthNames = extractProfileSkillNames(strengths);
        
        List<CompletableFuture<RuntimeRoadmapNode>> futures = new ArrayList<>();
        for (int i = 0; i < initialNodes.size(); i++) {
            RuntimeRoadmapNode node = initialNodes.get(i);
            boolean gapMatched = false;
            boolean strengthMatched = false;
            if (node.skillName() != null) {
                String normSkill = node.skillName().toLowerCase();
                boolean finalGapMatched = gapNames.stream().anyMatch(val -> normSkill.contains(val) || val.contains(normSkill));
                boolean finalStrengthMatched = strengthNames.stream().anyMatch(val -> normSkill.contains(val) || val.contains(normSkill));
                gapMatched = finalGapMatched;
                strengthMatched = finalStrengthMatched;
            }

            final boolean isGap = gapMatched;
            final boolean isStrength = strengthMatched;

            CompletableFuture<RuntimeRoadmapNode> future = CompletableFuture.supplyAsync(() -> {
                String lessonsJson = null;
                if (node.lessons() != null && !node.lessons().isEmpty()) {
                    try {
                        lessonsJson = objectMapper.writeValueAsString(node.lessons());
                    } catch (Exception ignored) {}
                }

                RoadmapNodeAiEnrichmentService.EnrichedNode enriched = nodeAiEnrichmentService.enrichNode(
                        node.title(),
                        node.description(),
                        node.expectedOutput(),
                        node.rubric(),
                        node.skillName(),
                        studentLevel,
                        journey.getGoal(),
                        isGap,
                        isStrength,
                        node.pinnedDocumentIds(),
                        lessonsJson
                );

                String expectedOutputVal = (enriched.getExpectedOutput() != null && !enriched.getExpectedOutput().isBlank())
                        ? enriched.getExpectedOutput()
                        : node.expectedOutput();

                String rubricVal = (enriched.getRubric() != null && !enriched.getRubric().isBlank())
                        ? enriched.getRubric()
                        : node.rubric();

                List<String> practicalExercisesVal = (enriched.getPracticalExercises() != null && !enriched.getPracticalExercises().isEmpty())
                        ? enriched.getPracticalExercises()
                        : node.practicalExercises();

                List<String> successCriteriaVal = (enriched.getSuccessCriteria() != null && !enriched.getSuccessCriteria().isEmpty())
                        ? enriched.getSuccessCriteria()
                        : node.successCriteria();

                List<RoadmapNodeAiEnrichmentService.EnrichedLesson> lessonsVal = (enriched.getLessons() != null && !enriched.getLessons().isEmpty())
                        ? enriched.getLessons()
                        : node.lessons();

                return new RuntimeRoadmapNode(
                        node.id(),
                        node.templateSkillBlockId(),
                        node.skillId(),
                        node.skillName(),
                        node.title(),
                        enriched.getDescription(),
                        expectedOutputVal,
                        rubricVal,
                        node.difficulty(),
                        node.minLevel(),
                        node.maxLevel(),
                        node.targetLevelMatch(),
                        node.activitySource(),
                        node.reason(),
                        node.estimatedHours(),
                        node.suggestedCourseIds(),
                        node.skillRequirementsJson(),
                        enriched.getLearningObjectives(),
                        practicalExercisesVal,
                        successCriteriaVal,
                        node.pinnedDocumentIds(),
                        node.nodeType(),
                        node.parentNodeKey(),
                        lessonsVal
                );
            }, roadmapEnrichmentTaskExecutor);

            futures.add(future);
        }

        // Chờ tất cả hoàn thành song song (ThreadPoolTaskExecutor khống chế 3 luồng đồng thời)
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<RuntimeRoadmapNode> enrichedNodes = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

        return buildRuntimeRoadmapJson(template, enrichedNodes, studentLevel, "Admin module template guided",
                "Assessment-aware admin module template");
    }

    private String estimateFriendlyDuration(List<RuntimeRoadmapNode> nodes) {
        double totalHours = 0;
        for (RuntimeRoadmapNode node : nodes) {
            if (node.estimatedHours() != null) {
                totalHours += node.estimatedHours();
            }
        }
        if (totalHours <= 0) {
            totalHours = nodes.size() * 4.0;
        }
        double weeks = totalHours / 7.0;
        long approxWeeks = Math.round(weeks);
        if (approxWeeks <= 4) {
            return Math.max(1, approxWeeks) + " tuần";
        }
        long approxMonths = Math.round(weeks / 4.3);
        return Math.max(1, approxMonths) + " tháng";
    }

    private RuntimeRoadmap buildRuntimeRoadmapJson(
            RoadmapTemplate template,
            List<RuntimeRoadmapNode> runtimeNodes,
            String studentLevel,
            String duration,
            String learningStyle) {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode metadata = root.putObject("roadmap_metadata");
        metadata.put("title", template.getTitle());
        metadata.put("original_goal", template.getTitle());
        metadata.put("validated_goal", firstNonBlank(template.getGlobalLearningGoal(), template.getDescription(), template.getTitle()));
        metadata.put("duration", estimateFriendlyDuration(runtimeNodes));
        metadata.put("experience_level", studentLevel);
        metadata.put("learning_style", learningStyle);
        metadata.put("difficulty_level", resolveDominantDifficultyRuntime(runtimeNodes));
        metadata.put("roadmap_type", "career");
        metadata.put("target", firstNonBlank(template.getGlobalLearningGoal(), template.getTitle()));
        metadata.put("final_objective", firstNonBlank(template.getOutputStandard(), "Complete admin-defined roadmap evidence"));
        metadata.put("roadmap_mode", GENERATION_MODE_TEMPLATE_GUIDED);
        metadata.put("generation_mode", String.valueOf(template.getGenerationMode()));
        metadata.put("knowledge_policy", String.valueOf(template.getKnowledgePolicy()));
        metadata.put("current_level", studentLevel);

        ArrayNode roadmap = root.putArray("roadmap");
        for (int i = 0; i < runtimeNodes.size(); i++) {
            RuntimeRoadmapNode node = runtimeNodes.get(i);
            ObjectNode n = roadmap.addObject();
            n.put("id", node.id());
            n.put("title", node.title());
            n.put("description", node.description());
            n.put("estimated_time_minutes", toMinutes(node.estimatedHours()));
            
            String nodeType = node.nodeType() != null ? node.nodeType() : "MAIN";
            n.put("type", nodeType);
            n.put("difficulty", node.difficulty());
            n.put("order_index", i + 1);
            n.put("main_path_index", i + 1);
            n.put("is_core", "MAIN".equals(nodeType));

            String parentId = null;
            if (node.parentNodeKey() != null && !node.parentNodeKey().isBlank()) {
                for (RuntimeRoadmapNode other : runtimeNodes) {
                    if (node.parentNodeKey().equals(other.id())) {
                        parentId = other.id();
                        break;
                    }
                }
            }
            if (parentId == null && i > 0) {
                for (int j = i - 1; j >= 0; j--) {
                    String otherType = runtimeNodes.get(j).nodeType();
                    if (otherType == null || "MAIN".equals(otherType)) {
                        parentId = runtimeNodes.get(j).id();
                        break;
                    }
                }
            }
            if (parentId != null) {
                n.put("parent_id", parentId);
            } else {
                n.putNull("parent_id");
            }

            n.put("node_status", i == 0 ? "AVAILABLE" : "LOCKED");
            if (node.skillId() != null) {
                n.put("skill_id", node.skillId());
            } else {
                n.putNull("skill_id");
            }
            n.put("skill_name", node.skillName());
            if (node.templateSkillBlockId() != null) {
                n.put("template_skill_block_id", node.templateSkillBlockId());
            } else {
                n.putNull("template_skill_block_id");
            }
            putSkillRequirements(n, node);
            if (node.minLevel() != null) {
                n.put("min_level", node.minLevel().name());
            } else {
                n.putNull("min_level");
            }
            if (node.maxLevel() != null) {
                n.put("max_level", node.maxLevel().name());
            } else {
                n.putNull("max_level");
            }
            n.put("target_level_match", node.targetLevelMatch());
            n.put("activity_source", node.activitySource());

            if (node.learningObjectives() != null) {
                putArray(n, "learning_objectives", node.learningObjectives());
            } else {
                putArray(n, "learning_objectives", splitTemplateText(firstNonBlank(node.expectedOutput(), node.title())));
            }

            if (node.practicalExercises() != null) {
                putArray(n, "practical_exercises", node.practicalExercises());
            } else {
                putArray(n, "practical_exercises", splitTemplateText(node.expectedOutput()));
            }

            if (node.successCriteria() != null) {
                putArray(n, "success_criteria", node.successCriteria());
            } else {
                putArray(n, "success_criteria", splitTemplateText(node.rubric()));
            }

            ArrayNode lessonsArr = n.putArray("lessons");
            if (node.lessons() != null) {
                for (RoadmapNodeAiEnrichmentService.EnrichedLesson lesson : node.lessons()) {
                    ObjectNode les = lessonsArr.addObject();
                    les.put("title", lesson.getTitle());
                    les.put("description", lesson.getDescription());
                    les.put("learningObjective", lesson.getLearningObjective());
                    if (lesson.getEstimatedMinutes() != null) {
                        les.put("estimatedMinutes", lesson.getEstimatedMinutes());
                    } else {
                        les.putNull("estimatedMinutes");
                    }
                }
            }

            putArray(n, "suggested_resources", List.of());
            putArray(n, "key_concepts", resolveRuntimeSkillNames(node));
            putArray(n, "prerequisites", parentId != null ? List.of(parentId) : List.of());
            
            List<String> children = new ArrayList<>();
            for (RuntimeRoadmapNode other : runtimeNodes) {
                if (node.id().equals(other.parentNodeKey())) {
                    children.add(other.id());
                }
            }
            if (children.isEmpty() && i + 1 < runtimeNodes.size()) {
                String nextType = runtimeNodes.get(i + 1).nodeType();
                String nextParent = runtimeNodes.get(i + 1).parentNodeKey();
                if ((nextType == null || "MAIN".equals(nextType)) && (nextParent == null || nextParent.isBlank())) {
                    children.add(runtimeNodes.get(i + 1).id());
                }
            }
            putArray(n, "children", children);

            putLongArray(n, "suggested_course_ids", node.suggestedCourseIds());
            n.put("importance_score", 0.85);
            n.put("confidence_score", 0.95);
            n.put("reason", node.reason());
            putArray(n, "evidence", List.of("admin_template_v3", "skill_weight", node.activitySource(), "assessment_level"));
            n.put("importance_validation_status", "ACCEPTED");
        }

        ObjectNode stats = root.putObject("roadmap_statistics");
        stats.put("total_nodes", runtimeNodes.size());
        stats.put("main_nodes", runtimeNodes.size());
        stats.put("side_nodes", 0);
        stats.put("total_estimated_hours", runtimeNodes.stream()
                .map(RuntimeRoadmapNode::estimatedHours)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum());
        putArray(root, "learning_tips", List.of("Follow each module as one practical topic that combines related skills."));
        putArray(root, "warnings", List.of());
        root.putObject("overview")
                .put("purpose", "Admin V3 template with skill weights, level-banded activities, courses, and skill document permissions")
                .put("audience", "Runtime learner profile from journey assessment")
                .put("post_roadmap_state", firstNonBlank(template.getOutputStandard(), "Ready for job-position competency verification"));
        return new RuntimeRoadmap(root.toString(), runtimeNodes);
    }

    private String resolveRuntimeTarget(RoadmapTemplate template, Journey journey) {
        return firstNonBlank(
                journey != null ? journey.getGoal() : null,
                journey != null ? journey.getJobRole() : null,
                template.getGlobalLearningGoal(),
                template.getTitle());
    }

    private Journey.SkillLevel resolveStudentSkillLevel(Journey journey, TestResult testResult) {
        if (testResult != null && testResult.getEvaluatedLevel() != null) {
            return testResult.getEvaluatedLevel();
        }
        if (journey != null && journey.getCurrentLevel() != null) {
            return journey.getCurrentLevel();
        }
        return Journey.SkillLevel.BEGINNER;
    }

    private String resolveStudentLevel(Journey journey, TestResult testResult) {
        return resolveStudentSkillLevel(journey, testResult).name().toLowerCase();
    }

    private RoadmapTemplateActivity selectActivityForLevel(
            List<RoadmapTemplateActivity> activities, Journey.SkillLevel studentLevel, int offset) {
        List<RoadmapTemplateActivity> safeActivities = defaultList(activities);
        if (safeActivities.isEmpty()) {
            return null;
        }
        List<RoadmapTemplateActivity> matching = safeActivities.stream()
                .filter(activity -> activityMatchesLevel(activity, studentLevel))
                .toList();
        if (!matching.isEmpty()) {
            return matching.get(Math.floorMod(offset, matching.size()));
        }
        return safeActivities.stream()
                .min(Comparator.comparingInt(activity -> activityLevelDistance(activity, studentLevel)))
                .orElse(safeActivities.get(Math.floorMod(offset, safeActivities.size())));
    }

    private boolean activityMatchesLevel(RoadmapTemplateActivity activity, Journey.SkillLevel studentLevel) {
        if (activity == null || studentLevel == null || activity.getMinLevel() == null) {
            return false;
        }
        int studentRank = levelRank(studentLevel);
        int minRank = levelRank(activity.getMinLevel());
        int maxRank = activity.getMaxLevel() != null ? levelRank(activity.getMaxLevel()) : minRank;
        return studentRank >= minRank && studentRank <= maxRank;
    }

    private int activityLevelDistance(RoadmapTemplateActivity activity, Journey.SkillLevel studentLevel) {
        if (activity == null || studentLevel == null || activity.getMinLevel() == null) {
            return Integer.MAX_VALUE;
        }
        int studentRank = levelRank(studentLevel);
        int minRank = levelRank(activity.getMinLevel());
        int maxRank = activity.getMaxLevel() != null ? levelRank(activity.getMaxLevel()) : minRank;
        if (studentRank < minRank) {
            return minRank - studentRank;
        }
        if (studentRank > maxRank) {
            return studentRank - maxRank;
        }
        return 0;
    }

    private int levelRank(Journey.SkillLevel level) {
        if (level == null) {
            return 0;
        }
        return switch (level) {
            case BEGINNER -> 0;
            case ELEMENTARY -> 1;
            case INTERMEDIATE -> 2;
            case ADVANCED -> 3;
            case EXPERT -> 4;
        };
    }

    private String buildRuntimeReason(
            RoadmapTemplateSkillBlock block,
            RoadmapTemplateActivity activity,
            Journey.SkillLevel studentLevel,
            boolean targetLevelMatch) {
        String skillName = firstNonBlank(block.getSkillNameSnapshot(), "this skill");
        String level = studentLevel != null ? studentLevel.name().toLowerCase() : "beginner";
        if (activity == null) {
            return "Admin V2 template allocated this node from skill weight; no activity blueprint was available for " + skillName + ".";
        }
        if (targetLevelMatch) {
            return "Activity blueprint matches the learner assessment level " + level + " for " + skillName + ".";
        }
        return "No exact activity level band matched " + level + "; backend selected the nearest available activity for " + skillName + ".";
    }

    private Set<String> extractProfileSkillNames(List<Map<String, Object>> profileItems) {
        if (profileItems == null) {
            return Set.of();
        }
        return profileItems.stream()
                .filter(Objects::nonNull)
                .flatMap(item -> item.values().stream())
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .map(value -> value.toLowerCase().trim())
                .filter(value -> !value.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean profileMatchesNode(Set<String> profileNames, RoadmapTemplateNode node) {
        if (profileNames == null || profileNames.isEmpty()) {
            return false;
        }
        String skill = firstNonBlank(node.getSkillNameSnapshot(), node.getSkillCanonicalKeySnapshot(), node.getTitle());
        if (skill == null) {
            return false;
        }
        String normalizedSkill = skill.toLowerCase();
        return profileNames.stream().anyMatch(value -> normalizedSkill.contains(value) || value.contains(normalizedSkill));
    }

    private String personalizeDescription(RoadmapTemplateNode node, String studentLevel, boolean gapMatched, boolean strengthMatched) {
        String base = firstNonBlank(node.getDescription(), node.getExpectedOutput(), node.getTitle(), "");
        if (gapMatched) {
            return base + " Focus more practice here because assessment identified this as a gap for your " + studentLevel + " level.";
        }
        if (strengthMatched) {
            return base + " Use this node to turn an existing strength into job-ready evidence.";
        }
        return base + " Adjust examples and pacing for " + studentLevel + " level.";
    }

    private int personalizeMinutes(RoadmapTemplateNode node, String studentLevel, boolean gapMatched) {
        int baseMinutes = toMinutes(node.getEstimatedHours());
        if (gapMatched) {
            return Math.max(baseMinutes, (int) Math.round(baseMinutes * 1.25));
        }
        if ("advanced".equals(studentLevel) || "expert".equals(studentLevel)) {
            return Math.max(15, (int) Math.round(baseMinutes * 0.85));
        }
        return baseMinutes;
    }

    private String personalizeDifficulty(RoadmapTemplateNode node, String studentLevel, boolean gapMatched, boolean strengthMatched) {
        String base = firstNonBlank(node.getDifficulty(), "medium");
        if (gapMatched && ("beginner".equals(studentLevel) || "elementary".equals(studentLevel))) {
            return "easy";
        }
        if (strengthMatched && ("advanced".equals(studentLevel) || "expert".equals(studentLevel))) {
            return "hard";
        }
        return base;
    }

    private List<String> personalizedObjectives(RoadmapTemplateNode node, String studentLevel, boolean gapMatched) {
        List<String> objectives = new ArrayList<>();
        objectives.add(node.getTitle());
        if (node.getSkillNameSnapshot() != null) {
            objectives.add("Apply " + node.getSkillNameSnapshot() + " at " + studentLevel + " level");
        }
        if (gapMatched) {
            objectives.add("Close the assessment gap with guided practice and evidence");
        }
        return objectives;
    }

    private List<String> personalizedExercises(RoadmapTemplateNode node, String studentLevel, boolean gapMatched) {
        List<String> exercises = new ArrayList<>();
        if (node.getExpectedOutput() != null && !node.getExpectedOutput().isBlank()) {
            exercises.add(node.getExpectedOutput());
        }
        exercises.add("Produce a short evidence artifact that proves " + node.getTitle() + " for " + studentLevel + " level");
        if (gapMatched) {
            exercises.add("Repeat the task with one extra constraint based on assessment feedback");
        }
        return exercises;
    }

    private List<String> personalizedSuccessCriteria(RoadmapTemplateNode node) {
        List<String> criteria = new ArrayList<>();
        if (node.getRubric() != null && !node.getRubric().isBlank()) {
            criteria.add(node.getRubric());
        }
        criteria.add("Submission clearly maps to the node expected output");
        criteria.add("Work can be reviewed against the job position skill requirement");
        return criteria;
    }

    private List<String> personalizedKeyConcepts(RoadmapTemplateNode node, String studentLevel) {
        List<String> concepts = new ArrayList<>();
        if (node.getSkillNameSnapshot() != null) {
            concepts.add(node.getSkillNameSnapshot());
        }
        concepts.add("Level: " + studentLevel);
        if (node.getRequirementType() != null) {
            concepts.add("Requirement: " + node.getRequirementType().name());
        }
        return concepts;
    }

    private String personalizationReason(RoadmapTemplateNode node, String studentLevel, boolean gapMatched, boolean strengthMatched) {
        if (gapMatched) {
            return "Admin template node prioritized because assessment shows a gap in this skill for " + studentLevel + " level.";
        }
        if (strengthMatched) {
            return "Admin template node adapted to convert an assessed strength into portfolio-ready output.";
        }
        return "Admin template node adapted to the student's assessed " + studentLevel + " level within the selected job position track.";
    }

    private List<String> childrenOf(RoadmapTemplateNode parent, List<RoadmapTemplateNode> nodes, Map<Long, String> ids) {
        return nodes.stream()
                .filter(n -> Objects.equals(n.getParentNodeId(), parent.getId()))
                .map(n -> ids.get(n.getId()))
                .filter(Objects::nonNull)
                .toList();
    }

    private void putArray(ObjectNode node, String field, List<String> values) {
        ArrayNode array = node.putArray(field);
        defaultList(values).forEach(array::add);
    }

    private void putLongArray(ObjectNode node, String field, List<Long> values) {
        ArrayNode array = node.putArray(field);
        defaultList(values).forEach(array::add);
    }

    private void putSkillRequirements(ObjectNode node, RuntimeRoadmapNode runtimeNode) {
        ArrayNode array = node.putArray("skills");
        String raw = runtimeNode.skillRequirementsJson();
        if (raw != null && !raw.isBlank()) {
            try {
                objectMapper.readTree(raw).forEach(array::add);
                if (!array.isEmpty()) {
                    return;
                }
            } catch (Exception ignored) {
                log.warn("Invalid activity skillRequirementsJson for runtime node {}", runtimeNode.id());
                array.removeAll();
            }
        }

        ObjectNode fallback = array.addObject();
        fallback.put("skill_id", runtimeNode.skillId());
        fallback.put("skill_name", runtimeNode.skillName());
        fallback.put("requirement_type", RequirementType.REQUIRED.name());
    }

    private String buildNodeGroupSkillRequirementsJson(List<RoadmapTemplateNodeGroupSkill> skills) {
        ArrayNode array = objectMapper.createArrayNode();
        for (RoadmapTemplateNodeGroupSkill skill : defaultList(skills)) {
            ObjectNode item = array.addObject();
            item.put("skill_id", skill.getSkillId());
            item.put("skill_name", skill.getSkillNameSnapshot());
            item.put("canonical_key", skill.getSkillCanonicalKeySnapshot());
            item.put("requirement_type", skill.getRequirementType() != null
                    ? skill.getRequirementType().normalized().name()
                    : RequirementType.REQUIRED.name());
        }
        return array.toString();
    }

    private List<String> resolveRuntimeSkillNames(RuntimeRoadmapNode runtimeNode) {
        List<NodeSkillRef> skills = parseActivitySkillRequirements(
                runtimeNode.skillRequirementsJson(),
                runtimeNode.skillId(),
                runtimeNode.skillName(),
                null);
        List<String> names = skills.stream()
                .map(skill -> firstNonBlank(skill.skillName(), skill.canonicalKey(),
                        skill.skillId() != null ? "Skill " + skill.skillId() : null))
                .filter(Objects::nonNull)
                .distinct()
                .limit(7)
                .toList();
        return names.isEmpty() ? splitTemplateText(firstNonBlank(runtimeNode.skillName(), runtimeNode.title())) : names;
    }

    private List<String> splitTemplateText(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value.split("\\r?\\n|;"))
                .stream()
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .limit(6)
                .toList();
    }

    private String buildRuntimeDescription(RoadmapTemplateSkillBlock block, RoadmapTemplateActivity activity, String studentLevel) {
        String base = firstNonBlank(
                activity != null ? activity.getDescription() : null,
                block.getActivityInstructions(),
                block.getLearningGoals(),
                block.getRequiredTopics(),
                firstNonBlank(block.getSkillNameSnapshot(), "Skill") + " practice");
        return base + " Level: " + studentLevel + ".";
    }

    private List<Long> resolveSuggestedCourseIds(RoadmapTemplateSkillBlock block, List<Long> manualCourseIds) {
        RoadmapTemplateCourseLinkPolicy policy = block.getCourseLinkPolicy() != null
                ? block.getCourseLinkPolicy()
                : RoadmapTemplateCourseLinkPolicy.AUTO_HYBRID;
        int limit = normalizeAutoCourseLimit(block.getAutoCourseLimit());
        LinkedHashSet<Long> ids = new LinkedHashSet<>(defaultList(manualCourseIds));
        if (policy == RoadmapTemplateCourseLinkPolicy.MANUAL_ONLY) {
            return ids.stream().limit(limit).toList();
        }
        if (policy == RoadmapTemplateCourseLinkPolicy.AUTO_NEWEST || policy == RoadmapTemplateCourseLinkPolicy.AUTO_HYBRID) {
            systemCourseRepository.findNewestPublicCourseCandidatesBySkill(block.getSkillId(), limit)
                    .forEach(row -> ids.add(asLong(row[0])));
        }
        if (policy == RoadmapTemplateCourseLinkPolicy.AUTO_POPULAR || policy == RoadmapTemplateCourseLinkPolicy.AUTO_HYBRID) {
            systemCourseRepository.findPopularPublicCourseCandidatesBySkill(block.getSkillId(), limit)
                    .forEach(row -> ids.add(asLong(row[0])));
        }
        return ids.stream().filter(Objects::nonNull).limit(limit).toList();
    }

    private int toMinutes(Double hours) {
        if (hours == null || hours <= 0) {
            return 60;
        }
        return Math.max(15, (int) Math.round(hours * 60));
    }

    private double importanceScore(RoadmapTemplateNode node) {
        if (node.getImportanceLevel() == null) {
            return 0.7;
        }
        return switch (node.getImportanceLevel()) {
            case CRITICAL -> 1.0;
            case HIGH -> 0.85;
            case MEDIUM -> 0.65;
            case LOW -> 0.45;
        };
    }

    private String resolveNodeId(RoadmapTemplateNode node) {
        if (node.getNodeKey() != null && !node.getNodeKey().isBlank()) {
            return node.getNodeKey();
        }
        return "template-node-" + node.getId();
    }

    private String resolveDomainCode(Long domainId) {
        if (domainId == null) {
            return "ROADMAP_TEMPLATE";
        }
        return domainRepository.findById(domainId)
                .map(Domain::getCode)
                .filter(code -> !code.isBlank())
                .orElse("ROADMAP_TEMPLATE");
    }

    private String resolvePrimarySkillName(List<RoadmapTemplateNode> nodes) {
        return nodes.stream()
                .sorted(Comparator.comparing(RoadmapTemplateNode::getOrderIndex, Comparator.nullsLast(Integer::compareTo)))
                .map(n -> firstNonBlank(n.getSkillNameSnapshot(), n.getTitle()))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("ROADMAP_TEMPLATE");
    }

    private List<Long> extractSkillIds(List<RoadmapTemplateNode> nodes) {
        Set<Long> ids = nodes.stream()
                .map(RoadmapTemplateNode::getSkillId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new ArrayList<>(ids);
    }

    private List<Long> extractSkillIdsFromBlocks(List<RoadmapTemplateSkillBlock> blocks) {
        Set<Long> ids = defaultList(blocks).stream()
                .map(RoadmapTemplateSkillBlock::getSkillId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new ArrayList<>(ids);
    }

    private List<Long> extractSkillIdsFromRuntime(List<RuntimeRoadmapNode> nodes) {
        Set<Long> ids = new LinkedHashSet<>();
        for (RuntimeRoadmapNode node : defaultList(nodes)) {
            parseActivitySkillRequirements(node.skillRequirementsJson(), node.skillId(), node.skillName(), null).stream()
                    .map(NodeSkillRef::skillId)
                    .filter(Objects::nonNull)
                    .forEach(ids::add);
        }
        return new ArrayList<>(ids);
    }

    private String resolveDominantDifficulty(List<RoadmapTemplateNode> nodes) {
        return nodes.stream()
                .map(n -> firstNonBlank(n.getDifficulty(), "medium"))
                .collect(Collectors.groupingBy(d -> d, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("medium");
    }

    private String resolveDominantDifficultyRuntime(List<RuntimeRoadmapNode> nodes) {
        return defaultList(nodes).stream()
                .map(n -> firstNonBlank(n.difficulty(), "medium"))
                .collect(Collectors.groupingBy(d -> d, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("medium");
    }

    private RoadmapTemplate requireTemplate(Long id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Roadmap template not found: " + id));
    }



    private User requireUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found: " + id));
    }

    private void requireOwnerOrAdmin(Long actorId, Long ownerId) {
        User actor = requireUser(actorId);
        if (!isAdmin(actor) && !Objects.equals(actorId, ownerId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "You do not own this roadmap template resource");
        }
    }

    private void requireAdmin(Long actorId) {
        if (!isAdmin(requireUser(actorId))) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Admin role required");
        }
    }



    private boolean isAdmin(User user) {
        PrimaryRole role = user.getPrimaryRole();
        return role == PrimaryRole.ADMIN || (role != null && role.isSubAdminRole());
    }



    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private <T> List<T> defaultList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private double safeWeight(AllocationInput input) {
        return input.weightPercent() != null && input.weightPercent() > 0 ? input.weightPercent() : 0D;
    }

    private int normalizeAutoCourseLimit(Integer value) {
        return Math.max(1, Math.min(value != null ? value : 2, 10));
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Instant asInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        return null;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Failed to serialize roadmap template payload");
        }
    }

    private RoadmapTemplateResponse toTemplateResponse(RoadmapTemplate template) {
        List<RoadmapTemplateNodeResponse> nodes = nodeRepository.findByTemplateIdOrderByOrderIndexAscIdAsc(template.getId()).stream()
                .map(this::toNodeResponse)
                .toList();
        List<RoadmapTemplateCourseResponse> courses = courseRepository.findByTemplateIdOrderByDisplayOrderAscIdAsc(template.getId()).stream()
                .map(this::toCourseResponse)
                .toList();
        List<RoadmapTemplateSkillBlock> persistedBlocks = skillBlockRepository.findByTemplateIdOrderByIdAsc(template.getId());
        AllocationResult allocation = persistedBlocks.isEmpty()
                ? new AllocationResult(true, List.of(), List.of())
                : allocateFromBlocks(template.getJobPositionTrackId(), template.getTotalNodeCount(), persistedBlocks);
        Map<Long, Integer> allocatedBySkill = allocation.items().stream()
                .collect(Collectors.toMap(
                        RoadmapTemplateAllocationPreviewResponse.Item::getSkillId,
                        RoadmapTemplateAllocationPreviewResponse.Item::getAllocatedNodes,
                        (a, b) -> a,
                        LinkedHashMap::new));
        List<RoadmapTemplateSkillBlockResponse> skillBlocks = persistedBlocks.stream()
                .map(block -> toSkillBlockResponse(block, allocatedBySkill.get(block.getSkillId())))
                .toList();
        List<RoadmapTemplateNodeGroupResponse> nodeGroups = nodeGroupRepository
                .findByTemplateIdOrderByOrderIndexAscIdAsc(template.getId())
                .stream()
                .map(this::toNodeGroupResponse)
                .toList();
        return RoadmapTemplateResponse.builder()
                .id(template.getId())
                .createdByAdminId(template.getCreatedByAdminId())
                .updatedByAdminId(template.getUpdatedByAdminId())
                .domainId(template.getDomainId())
                .jobPositionId(template.getJobPositionId())
                .jobPositionTrackId(template.getJobPositionTrackId())
                .title(template.getTitle())
                .description(template.getDescription())
                .targetRole(template.getTargetRole())
                .targetLevel(template.getTargetLevel())
                .targetRoleSnapshot(template.getTargetRoleSnapshot())
                .targetLevelSnapshot(template.getTargetLevelSnapshot())
                .totalNodeCount(template.getTotalNodeCount())
                .generationMode(template.getGenerationMode())
                .knowledgePolicy(template.getKnowledgePolicy())
                .globalLearningGoal(template.getGlobalLearningGoal())
                .audienceLevel(template.getAudienceLevel())
                .outputStandard(template.getOutputStandard())
                .assessmentPolicy(template.getAssessmentPolicy())
                .templateInstructions(template.getTemplateInstructions())
                .constraintsJson(template.getConstraintsJson())
                .aiEvidenceReviewEnabled(template.getAiEvidenceReviewEnabled())
                .aiAutoPassEnabled(template.getAiAutoPassEnabled())
                .aiAutoPassMinScorePercent(template.getAiAutoPassMinScorePercent())
                .aiAutoPassMinConfidence(template.getAiAutoPassMinConfidence())
                .aiManualReviewBelowConfidence(template.getAiManualReviewBelowConfidence())
                .aiEvidencePrompt(template.getAiEvidencePrompt())
                .finalAssignmentInstructions(template.getFinalAssignmentInstructions())
                .finalAssignmentRubric(template.getFinalAssignmentRubric())
                .status(template.getStatus())
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .nodes(nodes)
                .courses(courses)
                .skillBlocks(skillBlocks)
                .nodeGroups(nodeGroups)
                .allocationPreview(toAllocationPreview(template.getTotalNodeCount(), allocation))
                .build();
    }

    private RoadmapTemplateNodeResponse toNodeResponse(RoadmapTemplateNode node) {
        return RoadmapTemplateNodeResponse.builder()
                .id(node.getId())
                .templateId(node.getTemplateId())
                .parentNodeId(node.getParentNodeId())
                .nodeKey(node.getNodeKey())
                .title(node.getTitle())
                .description(node.getDescription())
                .orderIndex(node.getOrderIndex())
                .skillId(node.getSkillId())
                .skillNameSnapshot(node.getSkillNameSnapshot())
                .skillCanonicalKeySnapshot(node.getSkillCanonicalKeySnapshot())
                .requirementType(node.getRequirementType())
                .importanceLevel(node.getImportanceLevel())
                .difficulty(node.getDifficulty())
                .estimatedHours(node.getEstimatedHours())
                .expectedOutput(node.getExpectedOutput())
                .rubric(node.getRubric())
                .build();
    }

    private RoadmapTemplateCourseResponse toCourseResponse(RoadmapTemplateCourse course) {
        return RoadmapTemplateCourseResponse.builder()
                .id(course.getId())
                .templateId(course.getTemplateId())
                .templateNodeId(course.getTemplateNodeId())
                .courseId(course.getCourseId())
                .skillId(course.getSkillId())
                .displayOrder(course.getDisplayOrder())
                .required(course.getRequired())
                .build();
    }

    private RoadmapTemplateSkillBlockResponse toSkillBlockResponse(RoadmapTemplateSkillBlock block, Integer allocatedNodes) {
        List<RoadmapTemplateActivityResponse> activities = activityRepository
                .findBySkillBlockIdOrderByOrderIndexAscIdAsc(block.getId())
                .stream()
                .map(this::toActivityResponse)
                .toList();
        return RoadmapTemplateSkillBlockResponse.builder()
                .id(block.getId())
                .templateId(block.getTemplateId())
                .skillId(block.getSkillId())
                .skillNameSnapshot(block.getSkillNameSnapshot())
                .skillCanonicalKeySnapshot(block.getSkillCanonicalKeySnapshot())
                .weightPercent(block.getWeightPercent())
                .minNodes(block.getMinNodes())
                .maxNodes(block.getMaxNodes())
                .nodeCountOverride(block.getNodeCountOverride())
                .learningGoals(block.getLearningGoals())
                .requiredTopics(block.getRequiredTopics())
                .activityInstructions(block.getActivityInstructions())
                .exerciseTypes(block.getExerciseTypes())
                .successCriteria(block.getSuccessCriteria())
                .ragQueryHint(block.getRagQueryHint())
                .courseLinkPolicy(block.getCourseLinkPolicy())
                .autoCourseLimit(block.getAutoCourseLimit())
                .ragEnabled(block.getRagEnabled())
                .allocatedNodes(allocatedNodes)
                .activities(activities)
                .build();
    }

    private RoadmapTemplateNodeGroupResponse toNodeGroupResponse(RoadmapTemplateNodeGroup group) {
        List<RoadmapTemplateNodeGroupResponse.SkillItem> skills = nodeGroupSkillRepository
                .findByNodeGroupIdOrderByOrderIndexAscIdAsc(group.getId())
                .stream()
                .map(skill -> RoadmapTemplateNodeGroupResponse.SkillItem.builder()
                        .skillId(skill.getSkillId())
                        .skillName(skill.getSkillNameSnapshot())
                        .canonicalKey(skill.getSkillCanonicalKeySnapshot())
                        .requirementType(skill.getRequirementType())
                        .build())
                .toList();
        return RoadmapTemplateNodeGroupResponse.builder()
                .id(group.getId())
                .templateId(group.getTemplateId())
                .nodeKey(group.getNodeKey())
                .title(group.getTitle())
                .description(group.getDescription())
                .learningObjectives(group.getLearningObjectives())
                .lessonsJson(group.getLessonsJson())
                .exercisesJson(group.getExercisesJson())
                .completionCriteria(group.getCompletionCriteria())
                .difficulty(group.getDifficulty())
                .estimatedHours(group.getEstimatedHours())
                .expectedOutput(group.getExpectedOutput())
                .rubric(group.getRubric())
                .aiPromptHint(group.getAiPromptHint())
                .nodeType(group.getNodeType())
                .parentNodeKey(group.getParentNodeKey())
                .orderIndex(group.getOrderIndex())
                .skills(skills)
                .build();
    }

    private RoadmapTemplateActivityResponse toActivityResponse(RoadmapTemplateActivity activity) {
        return RoadmapTemplateActivityResponse.builder()
                .id(activity.getId())
                .templateId(activity.getTemplateId())
                .skillBlockId(activity.getSkillBlockId())
                .title(activity.getTitle())
                .description(activity.getDescription())
                .exerciseType(activity.getExerciseType())
                .expectedOutput(activity.getExpectedOutput())
                .rubric(activity.getRubric())
                .difficulty(activity.getDifficulty())
                .minLevel(activity.getMinLevel())
                .maxLevel(activity.getMaxLevel())
                .estimatedHours(activity.getEstimatedHours())
                .prerequisiteHint(activity.getPrerequisiteHint())
                .aiPromptHint(activity.getAiPromptHint())
                .skillRequirementsJson(activity.getSkillRequirementsJson())
                .orderIndex(activity.getOrderIndex())
                .build();
    }

    private RoadmapTemplateCourseCandidateResponse toCourseCandidateResponse(Object[] row) {
        return RoadmapTemplateCourseCandidateResponse.builder()
                .courseId(asLong(row[0]))
                .title(row[1] != null ? String.valueOf(row[1]) : null)
                .level(row[2] != null ? String.valueOf(row[2]) : null)
                .category(row[3] != null ? String.valueOf(row[3]) : null)
                .createdAt(asInstant(row[4]))
                .enrollmentCount(asLong(row[5]))
                .thumbnailUrl(row.length > 6 && row[6] != null ? String.valueOf(row[6]) : null)
                .build();
    }


}
