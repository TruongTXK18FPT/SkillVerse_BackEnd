package com.exe.skillverse_backend.course_service.service.impl;

import com.exe.skillverse_backend.course_service.dto.coursedto.CourseRevisionDTO;
import com.exe.skillverse_backend.course_service.dto.coursedto.CourseRevisionUpdateDTO;
import com.exe.skillverse_backend.course_service.entity.Assignment;
import com.exe.skillverse_backend.course_service.entity.AssignmentCriteria;
import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.course_service.entity.CourseRevision;
import com.exe.skillverse_backend.course_service.entity.Lesson;
import com.exe.skillverse_backend.course_service.entity.CourseSkill;
import com.exe.skillverse_backend.course_service.entity.CourseSkillId;
import com.exe.skillverse_backend.course_service.entity.LessonAttachment;
import com.exe.skillverse_backend.course_service.entity.Module;
import com.exe.skillverse_backend.course_service.event.CourseRevisionApprovedEvent;
import com.exe.skillverse_backend.course_service.repository.CourseSkillRepository;
import com.exe.skillverse_backend.shared.repository.SkillRepository;
import com.exe.skillverse_backend.shared.entity.Skill;
import com.exe.skillverse_backend.course_service.entity.Quiz;
import com.exe.skillverse_backend.course_service.entity.QuizOption;
import com.exe.skillverse_backend.course_service.entity.QuizQuestion;
import com.exe.skillverse_backend.course_service.entity.enums.AttachmentType;
import com.exe.skillverse_backend.course_service.entity.enums.CourseRevisionStatus;
import com.exe.skillverse_backend.course_service.entity.enums.CourseStatus;
import com.exe.skillverse_backend.course_service.entity.enums.LessonType;
import com.exe.skillverse_backend.course_service.entity.enums.QuestionType;
import com.exe.skillverse_backend.course_service.entity.enums.QuizGradingMethod;
import com.exe.skillverse_backend.course_service.entity.enums.SubmissionType;
import com.exe.skillverse_backend.course_service.policy.CourseRevisionFeatureProperties;
import com.exe.skillverse_backend.course_service.repository.AssignmentRepository;
import com.exe.skillverse_backend.course_service.repository.CourseRepository;
import com.exe.skillverse_backend.course_service.repository.CourseRevisionRepository;
import com.exe.skillverse_backend.course_service.repository.LessonRepository;
import com.exe.skillverse_backend.course_service.repository.ModuleRepository;
import com.exe.skillverse_backend.course_service.repository.QuizRepository;
import com.exe.skillverse_backend.course_service.service.CourseRevisionService;
import com.exe.skillverse_backend.course_service.util.CourseRevisionSnapshotAssembler;
import com.exe.skillverse_backend.shared.dto.PageResponse;
import com.exe.skillverse_backend.shared.entity.Media;
import com.exe.skillverse_backend.shared.enums.SkillStatus;
import com.exe.skillverse_backend.shared.exception.AccessDeniedException;
import com.exe.skillverse_backend.shared.exception.BadRequestException;
import com.exe.skillverse_backend.shared.exception.ConflictException;
import com.exe.skillverse_backend.shared.exception.MediaOperationException;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import com.exe.skillverse_backend.shared.repository.MediaRepository;
import com.exe.skillverse_backend.shared.service.CloudinaryService;
import com.exe.skillverse_backend.shared.util.SkillNameUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class CourseRevisionServiceImpl implements CourseRevisionService {

    private static final String SUBMIT_GUARD_METRIC = "course_revision_submit_guard_total";
    private static final String BASELINE_FALLBACK_METRIC = "course_revision_baseline_fallback_total";
    private static final String SNAPSHOT_HASH_DURATION_METRIC = "course_revision_snapshot_hash_duration";
    private static final int CONTENT_SNAPSHOT_VERSION_V1 = 1;

    private final CourseRepository courseRepository;
    private final CourseRevisionRepository courseRevisionRepository;
    private final ModuleRepository moduleRepository;
    private final LessonRepository lessonRepository;
    private final QuizRepository quizRepository;
    private final AssignmentRepository assignmentRepository;
    private final MediaRepository mediaRepository;
    private final CourseRevisionFeatureProperties courseRevisionFeatureProperties;
    private final SkillRepository skillRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final CourseSkillRepository courseSkillRepository;
    private final CloudinaryService cloudinaryService;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    @Override
    @Transactional
    public CourseRevisionDTO createRevision(Long courseId, Long actorId) {
        ensureRevisionWriteEnabled();

        Course course = getCourseOrThrow(courseId);
        ensureAuthorOrAdmin(actorId, course.getAuthor().getId());

        if (course.getStatus() != CourseStatus.PUBLIC) {
            throw new ConflictException("COURSE_REVISION_CREATE_ALLOWED_ONLY_FOR_PUBLIC");
        }

        ensureLegacyInitialApprovedRevision(course, actorId);

        boolean hasOpenRevision = courseRevisionRepository.existsByCourseIdAndStatusIn(
                courseId,
                EnumSet.of(CourseRevisionStatus.DRAFT, CourseRevisionStatus.PENDING)
        );
        if (hasOpenRevision) {
            throw new ConflictException("COURSE_HAS_OPEN_REVISION");
        }

        int nextRevisionNumber = courseRevisionRepository.findTopByCourseIdOrderByRevisionNumberDesc(courseId)
                .map(r -> r.getRevisionNumber() + 1)
                .orElse(1);

        CourseRevision source = resolveSourceRevision(course);
        CourseRevision revision = buildRevisionFromSource(course, source, nextRevisionNumber, actorId);
        revision.setCreatedAt(now());
        revision.setUpdatedAt(now());
        revision.setSnapshotVersion(1);
        revision.setBaselineSnapshotHash(resolveBaselineHashForNewRevision(course, source));
        revision.setSnapshotHash(computeRevisionSnapshotHash(revision));

        CourseRevision saved;
        try {
            saved = courseRevisionRepository.save(revision);
        } catch (DataIntegrityViolationException ex) {
            // DB unique index is the final guard against concurrent "double-open revision" requests.
            throw new ConflictException("COURSE_HAS_OPEN_REVISION");
        }

        // Keep pointer to latest draft/revision for review, active remains unchanged until approval.
        course.setLatestRevisionId(saved.getId());
        courseRepository.save(course);

        logRevisionEvent(
                "create",
                courseId,
                saved.getId(),
                saved.getSourceRevisionId(),
                actorId,
                "CREATED"
        );

        return toRevisionDto(saved);
    }

    private void ensureLegacyInitialApprovedRevision(Course course, Long actorId) {
        if (course == null || course.getId() == null) {
            return;
        }
        if (course.getActiveRevisionId() != null) {
            return;
        }
        if (courseRevisionRepository.findTopByCourseIdOrderByRevisionNumberDesc(course.getId()).isPresent()) {
            return;
        }

        Instant now = now();
        CourseRevision initialRevision = CourseRevision.builder()
                .course(course)
                .revisionNumber(1)
                .status(CourseRevisionStatus.APPROVED)
                .title(course.getTitle())
                .description(course.getDescription())
                .level(course.getLevel())
                .category(course.getCategory())
                .shortDescription(course.getShortDescription())
                .estimatedDurationHours(course.getEstimatedDurationHours())
                .language(course.getLanguage())
                .price(course.getPrice())
                .currency(course.getCurrency())
                .learningObjectivesJson(writeJsonSafely(course.getLearningObjectives(), "[]"))
                .requirementsJson(writeJsonSafely(course.getRequirements(), "[]"))
                .courseSkillTagsJson(writeJsonSafely(course.getCourseSkillTags(), "[]"))
                .thumbnail(course.getThumbnail())
                .contentSnapshotJson(CourseRevisionSnapshotAssembler.buildCourseContentSnapshot(
                        objectMapper,
                        course,
                        CONTENT_SNAPSHOT_VERSION_V1
                ))
                .sourceRevisionId(null)
                .sourceCourseStatus(course.getStatus().name())
                .snapshotVersion(CONTENT_SNAPSHOT_VERSION_V1)
                .createdBy(actorId)
                .createdAt(now)
                .submittedAt(course.getSubmittedAt() != null ? course.getSubmittedAt() : now)
                .approvedAt(course.getPublishedAt() != null ? course.getPublishedAt() : now)
                .updatedAt(now)
                .build();
        initialRevision.setBaselineSnapshotHash(computeCourseSnapshotHash(course));
        initialRevision.setSnapshotHash(computeRevisionSnapshotHash(initialRevision));

        CourseRevision savedInitialRevision = courseRevisionRepository.save(initialRevision);
        course.setActiveRevisionId(savedInitialRevision.getId());
        course.setLatestRevisionId(savedInitialRevision.getId());
        course.setRevisioningEnabled(Boolean.TRUE);
        course.setUpdatedAt(now);
        courseRepository.save(course);

        logRevisionEvent(
                "bootstrap_initial",
                course.getId(),
                savedInitialRevision.getId(),
                null,
                actorId,
                "LEGACY_PUBLIC_WITHOUT_INITIAL_REVISION"
        );
    }

    @Override
    @Transactional(readOnly = true)
    public CourseRevisionDTO getRevision(Long revisionId, Long actorId) {
        CourseRevision revision = getRevisionOrThrow(revisionId);
        ensureAuthorOrAdmin(actorId, revision.getCourse().getAuthor().getId());
        return toRevisionDto(revision);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CourseRevisionDTO> listCourseRevisions(
            Long courseId,
            Long actorId,
            CourseRevisionStatus status,
            Pageable pageable
    ) {
        Course course = getCourseOrThrow(courseId);
        ensureAuthorOrAdmin(actorId, course.getAuthor().getId());

        Page<CourseRevision> page = status == null
                ? courseRevisionRepository.findByCourseId(courseId, pageable)
                : courseRevisionRepository.findByCourseIdAndStatus(courseId, status, pageable);

        return toPageResponse(page);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CourseRevisionDTO> listAdminRevisions(CourseRevisionStatus status, Pageable pageable) {
        CourseRevisionStatus effectiveStatus = status == null ? CourseRevisionStatus.PENDING : status;
        Page<CourseRevision> page = courseRevisionRepository.findByStatus(effectiveStatus, pageable);
        return toPageResponse(page);
    }

    @Override
    @Transactional
    public CourseRevisionDTO submitRevision(Long revisionId, Long actorId) {
        ensureRevisionWriteEnabled();

        CourseRevision revision = getRevisionOrThrow(revisionId);
        Course course = revision.getCourse();

        ensureAuthorOrAdmin(actorId, course.getAuthor().getId());

        if (revision.getStatus() != CourseRevisionStatus.DRAFT
                && revision.getStatus() != CourseRevisionStatus.REJECTED) {
            throw new ConflictException("COURSE_REVISION_CANNOT_BE_SUBMITTED_IN_STATUS_" + revision.getStatus());
        }
        if (isSourceBaselineRequired(revision) && revision.getSourceRevisionId() == null) {
            recordSubmitGuardMetric("blocked", "baseline_not_found");
            throw new ConflictException("COURSE_REVISION_BASELINE_NOT_FOUND");
        }

        String preMaterializeSnapshotHash = computeRevisionSnapshotHash(revision);
        boolean hasMeaningfulChanges = hasMeaningfulChangesComparedToBaseline(revision, course);
        boolean noChangesSinceLastReject = revision.getRejectedSnapshotHash() != null
                && !revision.getRejectedSnapshotHash().isBlank()
                && Objects.equals(preMaterializeSnapshotHash, revision.getRejectedSnapshotHash());

        if (noChangesSinceLastReject) {
            if (isSubmitChangeCheckEnforced()) {
                recordSubmitGuardMetric("blocked", "no_changes_since_rejection");
                throw new ConflictException("COURSE_REVISION_NO_CHANGES_SINCE_REJECTION");
            }
            if (isSubmitChangeCheckWarnOnly()) {
                recordSubmitGuardMetric("allowed_warn", "no_changes_since_rejection");
                log.warn("Revision {} submitted without changes since rejection (mode=WARN, actor={})",
                        revisionId, actorId);
            }
            if (!isSubmitChangeCheckWarnOnly() && !isSubmitChangeCheckEnforced()) {
                recordSubmitGuardMetric("allowed_off", "no_changes_since_rejection");
            }
        } else if (!hasMeaningfulChanges) {
            if (isSubmitChangeCheckEnforced()) {
                recordSubmitGuardMetric("blocked", "no_changes_to_submit");
                throw new ConflictException("COURSE_REVISION_NO_CHANGES_TO_SUBMIT");
            }
            if (isSubmitChangeCheckWarnOnly()) {
                recordSubmitGuardMetric("allowed_warn", "no_changes_to_submit");
                log.warn("Revision {} submitted without meaningful changes (mode=WARN, actor={})",
                        revisionId, actorId);
            }
            if (!isSubmitChangeCheckWarnOnly() && !isSubmitChangeCheckEnforced()) {
                recordSubmitGuardMetric("allowed_off", "no_changes_to_submit");
            }
        }

        JsonNode materializedSnapshot = materializeSnapshotIdentityForSubmit(
                revision.getContentSnapshotJson(),
                course,
                revision.getId()
        );
        revision.setContentSnapshotJson(materializedSnapshot);
        ensureSnapshotIdentityContract(revision.getContentSnapshotJson(), course.getId(), revision.getId());
        String currentSnapshotHash = computeRevisionSnapshotHash(revision);

        boolean wasRejected = revision.getStatus() == CourseRevisionStatus.REJECTED;
        revision.setStatus(CourseRevisionStatus.PENDING);
        revision.setSubmittedAt(now());
        revision.setUpdatedAt(now());
        revision.setRejectedAt(null);
        revision.setRejectionReason(null);
        revision.setSnapshotHash(currentSnapshotHash);
        if (revision.getBaselineSnapshotHash() == null || revision.getBaselineSnapshotHash().isBlank()) {
            revision.setBaselineSnapshotHash(resolveBaselineHashForRevision(revision, course));
        }
        if (revision.getSnapshotVersion() == null) {
            revision.setSnapshotVersion(1);
        }
        if (wasRejected) {
            revision.setRejectedSnapshotHash(null);
        }

        CourseRevision saved = courseRevisionRepository.save(revision);
        logRevisionEvent(
                "submit",
                course.getId(),
                saved.getId(),
                saved.getSourceRevisionId(),
                actorId,
                "SUBMITTED"
        );
        return toRevisionDto(saved);
    }

    @Override
    @Transactional
    public CourseRevisionDTO updateRevision(Long revisionId, CourseRevisionUpdateDTO dto, Long actorId, MultipartFile thumbnailFile) {
        ensureRevisionWriteEnabled();

        CourseRevision revision = getRevisionOrThrow(revisionId);
        Course course = revision.getCourse();

        ensureAuthorOrAdmin(actorId, course.getAuthor().getId());

        if (revision.getStatus() != CourseRevisionStatus.DRAFT
                && revision.getStatus() != CourseRevisionStatus.REJECTED) {
            throw new ConflictException("COURSE_REVISION_NOT_EDITABLE_IN_STATUS_" + revision.getStatus());
        }

        // Handle thumbnail: upload new file if provided, otherwise use thumbnailMediaId from DTO
        if (thumbnailFile != null && !thumbnailFile.isEmpty()) {
            Media uploaded = uploadRevisionThumbnail(thumbnailFile, actorId);
            revision.setThumbnail(uploaded);
        } else if (dto.getThumbnailMediaId() != null) {
            Media existing = mediaRepository.findById(dto.getThumbnailMediaId())
                    .orElseThrow(() -> new NotFoundException("COURSE_REVISION_THUMBNAIL_MEDIA_NOT_FOUND"));
            revision.setThumbnail(existing);
        }

        if (dto.getTitle() != null) {
            if (dto.getTitle().isBlank()) {
                throw new IllegalArgumentException("Course revision title cannot be blank");
            }
            revision.setTitle(dto.getTitle());
        }
        if (dto.getDescription() != null) revision.setDescription(dto.getDescription());
        if (dto.getLevel() != null) revision.setLevel(dto.getLevel());
        if (dto.getCategory() != null) revision.setCategory(dto.getCategory());
        if (dto.getShortDescription() != null) revision.setShortDescription(dto.getShortDescription());
        if (dto.getEstimatedDurationHours() != null) revision.setEstimatedDurationHours(dto.getEstimatedDurationHours());
        if (dto.getLanguage() != null) revision.setLanguage(dto.getLanguage());
        if (dto.getPrice() != null) revision.setPrice(dto.getPrice());
        if (dto.getCurrency() != null) revision.setCurrency(dto.getCurrency());
        if (dto.getLearningObjectives() != null) {
            revision.setLearningObjectivesJson(writeJsonSafely(normalizeDtoTextList(dto.getLearningObjectives()), "[]"));
        }
        if (dto.getRequirements() != null) {
            revision.setRequirementsJson(writeJsonSafely(normalizeDtoTextList(dto.getRequirements()), "[]"));
        }
        if (dto.getCourseSkills() != null) {
            revision.setCourseSkillTagsJson(writeJsonSafely(normalizeDtoStringList(dto.getCourseSkills()), "[]"));
        }
        if (dto.getContentSnapshotJson() != null) {
            JsonNode canonicalizedSnapshot = normalizeContentSnapshot(parseJsonSafely(dto.getContentSnapshotJson()));
            validateContentSnapshotSize(canonicalizedSnapshot, course.getId(), revisionId, actorId);
            revision.setContentSnapshotJson(canonicalizedSnapshot);
        }

        // Editing a rejected revision moves it back to draft lifecycle.
        if (revision.getStatus() == CourseRevisionStatus.REJECTED) {
            revision.setStatus(CourseRevisionStatus.DRAFT);
            revision.setRejectedAt(null);
            revision.setRejectionReason(null);
        }

        revision.setSnapshotHash(computeRevisionSnapshotHash(revision));
        if (revision.getBaselineSnapshotHash() == null || revision.getBaselineSnapshotHash().isBlank()) {
            revision.setBaselineSnapshotHash(resolveBaselineHashForRevision(revision, course));
        }
        if (revision.getSnapshotVersion() == null) {
            revision.setSnapshotVersion(1);
        }
        revision.setUpdatedAt(now());
        CourseRevision saved = courseRevisionRepository.save(revision);
        logRevisionEvent(
                "update",
                course.getId(),
                saved.getId(),
                saved.getSourceRevisionId(),
                actorId,
                "UPDATED"
        );
        return toRevisionDto(saved);
    }

    @Override
    @Transactional
    public CourseRevisionDTO approveRevision(Long revisionId, Long adminId) {
        ensureRevisionApprovalEnabled();

        CourseRevision revision = courseRevisionRepository.findByIdForApproval(revisionId)
                .orElseThrow(() -> new NotFoundException("COURSE_REVISION_NOT_FOUND"));
        if (revision.getStatus() != CourseRevisionStatus.PENDING) {
            throw new ConflictException("COURSE_REVISION_CANNOT_BE_APPROVED_IN_STATUS_" + revision.getStatus());
        }

        ensureSnapshotIdentityContract(
            revision.getContentSnapshotJson(),
            revision.getCourse() != null ? revision.getCourse().getId() : null,
            revision.getId()
        );

        Long courseId = revision.getCourse().getId();
        Course course = courseRepository.findByIdForRevisionApproval(courseId)
                .orElseThrow(() -> new NotFoundException("COURSE_NOT_FOUND"));
        Media effectiveThumbnail = revision.getThumbnail() != null
                ? revision.getThumbnail()
                : course.getThumbnail();
        if (revision.getThumbnail() == null && effectiveThumbnail != null) {
            revision.setThumbnail(effectiveThumbnail);
        }

        revision.setStatus(CourseRevisionStatus.APPROVED);
        revision.setApprovedAt(now());
        revision.setUpdatedAt(now());
        revision.setRejectedAt(null);
        revision.setRejectionReason(null);

        CourseRevision saved = courseRevisionRepository.save(revision);

        course.setActiveRevisionId(saved.getId());
        course.setLatestRevisionId(saved.getId());
        course.setRevisioningEnabled(Boolean.TRUE);
        course.setUpdatedAt(now());
        // Sync price and currency from approved revision to course entity.
        // These fields on the course table are used by enrollment and purchase logic,
        // so keeping them in sync prevents 400 errors and incorrect wallet charges.
        course.setPrice(saved.getPrice());
        course.setCurrency(saved.getCurrency());
        course.setThumbnail(effectiveThumbnail);
        courseRepository.save(course);

        // Sync AI grading fields from approved revision snapshot → live assignments table
        // This ensures SubmissionCreatedEventListener reads correct ai_grading_enabled
        syncAssignmentAiGradingFieldsFromSnapshot(saved.getContentSnapshotJson());

        // Sync course_skill (N:N) links from the approved revision's skill tags.
        // Keeps the N:N entity table in sync with the active revision so taxonomy-based
        // course recommendations work correctly after revision approval.
        syncCourseSkillLinksFromRevision(course.getId(), saved.getCourseSkillTagsJson());

        // Publish event to trigger immediate BM25 catalog refresh for this course.
        // Replaces 5-minute scheduled refresh for real-time index update.
        eventPublisher.publishEvent(new CourseRevisionApprovedEvent(this, course.getId(), saved.getId()));

        String autoUpgradeReasonCode = "POLICY_MANUAL_ONLY";

        logRevisionEvent(
                "approve",
                course.getId(),
                saved.getId(),
                saved.getSourceRevisionId(),
                adminId,
            autoUpgradeReasonCode
        );
        CourseRevisionDTO dto = toRevisionDto(saved);
        dto.setAutoUpgradeOutcome("SKIPPED");
        dto.setAutoUpgradeAffectedEnrollments(0);
        dto.setAutoUpgradeReasonCode(autoUpgradeReasonCode);
        dto.setAutoUpgradeReasonDetail("Manual-only policy: no auto-upgrade execution on approval.");
        return dto;
    }

    /**
     * Syncs course_skill (N:N) links from the approved revision's skill tags.
     *
     * <p>When a revision is approved, its {@code courseSkillTagsJson} becomes the active
     * source of truth for course skills. This method mirrors the same upsert logic as
     * {@code CourseServiceImpl.syncCourseSkillLinks()} so the N:N entity table stays in
     * sync with the approved revision — enabling taxonomy-based course recommendations.
     *
     * <p>Flow on approval:
     * <ol>
     *   <li>Extract skill names from {@code revision.getCourseSkillTagsJson()}</li>
     *   <li>Delete all existing {@code course_skill} links for this course</li>
    *   <li>Resolve existing ACTIVE {@code Skill} entities by canonical key, fallback to legacy name</li>
     *   <li>Create new {@code CourseSkill} links</li>
     * </ol>
     *
     * @param courseId the course being revised
     * @param courseSkillTagsJson JSON array of skill tag names (e.g. ["JAVA","SPRING"])
     */
    private void syncCourseSkillLinksFromRevision(Long courseId, JsonNode courseSkillTagsJson) {
        if (courseId == null) return;

        List<String> skillNames = toStringList(courseSkillTagsJson);
        if (skillNames.isEmpty()) {
            courseSkillRepository.deleteByCourseId(courseId);
            log.info("[RevisionSkillSync] Course {} approved with no skill tags, cleared all links", courseId);
            return;
        }

        // Delete all existing links first — revision is the source of truth on approval
        courseSkillRepository.deleteByCourseId(courseId);

        // Fetch course once outside loop to satisfy @MapsId FK requirement
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalStateException("Course " + courseId + " not found"));

        for (String name : skillNames) {
            Skill skill = resolveExistingActiveSkillByTag(name);

            CourseSkill link = CourseSkill.builder()
                    .id(new CourseSkillId(courseId, skill.getId()))
                    .course(course)
                    .skill(skill)
                    .build();
            courseSkillRepository.save(link);
            log.debug("[RevisionSkillSync] Linked course {} to skill '{}' (id={})",
                    courseId, name, skill.getId());
        }

        log.info("[RevisionSkillSync] Synced {} skill links for course {} on revision approval",
                skillNames.size(), courseId);
    }

        private Skill resolveExistingActiveSkillByTag(String rawSkillName) {
        String normalizedName = rawSkillName == null ? null : rawSkillName.trim();
        String canonicalKey = SkillNameUtils.normalizeRequired(normalizedName);

        Skill skill = skillRepository.findByCanonicalKey(canonicalKey)
            .orElseGet(() -> skillRepository.findByNameIgnoreCase(normalizedName)
                .orElseThrow(() -> new BadRequestException("SKILL_NOT_FOUND: " + normalizedName)));
        if (skill.getStatus() != SkillStatus.ACTIVE) {
            throw new BadRequestException("SKILL_NOT_ACTIVE: " + normalizedName);
        }
        return skill;
        }

    /**
     * Convert a JSON array node to a deduplicated list of UPPERCASE, trimmed strings.
     *
     * <p>Normalization ensures consistency:
     * <ul>
     *   <li>All names are trimmed and converted to UPPERCASE</li>
     *   <li>Duplicates are removed — ["java", "JAVA", "java"] becomes ["JAVA"]</li>
     * </ul>
     *
     * <p>This keeps the N:N entity table in sync with the ElementCollection
     * {@code course_skill_tags} and prevents duplicate Skill entities from forming.
     */
    private List<String> toStringList(JsonNode jsonNode) {
        if (jsonNode == null || !jsonNode.isArray()) {
            return Collections.emptyList();
        }
        // Sentinel: "__EMPTY__" is sent by FE when user intentionally clears all items.
        // Normalize first, then check — sentinel survives canonicalization as "__EMPTY__".
        List<String> normalized = StreamSupport.stream(jsonNode.spliterator(), false)
                .map(node -> node == null || node.isNull() ? null :
                    node.asText().trim()
                        .replaceAll("[^a-zA-Z0-9]+", "_")
                        .replaceAll("_+", "_")
                        .replaceAll("^_|_$", "")
                        .toUpperCase(Locale.ROOT))
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
        if (normalized.contains("__EMPTY__")) {
            // Sentinel only means "clear" when it's the sole item.
            // If mixed with real items → treat as normal items (sentinel was likely
            // a user-entered skill name that coincidentally matched the sentinel).
            if (normalized.size() == 1) {
                return Collections.emptyList();
            }
            normalized = normalized.stream().filter(s -> !"__EMPTY__".equals(s)).toList();
        }
        return normalized;
    }

    /**
     * Normalizes a List&lt;String&gt; received from DTO multipart binding.
     * FE sends "__EMPTY__" as a sentinel when the user intentionally clears an array field,
     * because Spring @ModelAttribute cannot distinguish missing field from empty array.
     * This method detects the sentinel and returns an empty list to signal "clear".
     */
    private List<String> normalizeDtoStringList(List<String> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> normalized = items.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> s.trim()
                        .replaceAll("[^a-zA-Z0-9]+", "_")
                        .replaceAll("_+", "_")
                        .replaceAll("^_|_$", "")
                        .toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
        if (normalized.contains("__EMPTY__")) {
            // Sentinel only means "clear" when it's the sole item.
            if (normalized.size() == 1) {
                return Collections.emptyList();
            }
            // Mixed with real items → ignore sentinel, keep the real items only.
            normalized = normalized.stream().filter(s -> !"__EMPTY__".equals(s)).toList();
        }
        return normalized;
    }

    /**
     * Normalizes display text lists from multipart binding without slugifying user-facing content.
     * Learning objectives and requirements may contain Vietnamese or punctuation and must round-trip
     * exactly enough for editing; only trim, drop blanks, dedupe, and honor the clear sentinel.
     */
    private List<String> normalizeDtoTextList(List<String> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> normalized = items.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        if (normalized.contains("__EMPTY__")) {
            if (normalized.size() == 1) {
                return Collections.emptyList();
            }
            normalized = normalized.stream().filter(s -> !"__EMPTY__".equals(s)).toList();
        }
        return normalized;
    }

    /**
     * Syncs AI grading fields (aiGradingEnabled, gradingStyle, aiGradingPrompt,
     * trustAiEnabled) from the approved revision's snapshot JSON into the live
     * assignments table. This ensures SubmissionCreatedEventListener reads correct
     * AI config when students submit assignments.
     */
    private void syncAssignmentAiGradingFieldsFromSnapshot(JsonNode snapshot) {
        if (snapshot == null) { return; }

        JsonNode modules = snapshot.path("modules");
        if (!modules.isArray()) { return; }

        // Bước 1: Collect tất cả assignment IDs từ snapshot (1 pass)
        List<Long> assignmentIds = new ArrayList<>();
        for (JsonNode module : modules) {
            JsonNode lessons = module.path("lessons");
            if (!lessons.isArray()) { continue; }
            for (JsonNode lesson : lessons) {
                if (!"assignment".equals(lesson.path("type").asText(null))) { continue; }
                Long id = lesson.path("id").asLong(0L);
                if (id != null && id != 0L) assignmentIds.add(id);
            }
        }

        if (assignmentIds.isEmpty()) {
            log.debug("[RevisionApproval] AI grading sync: no assignment lessons found in snapshot");
            return;
        }

        // Bước 2: Batch query — 1 query duy nhất thay vì N+1
        Map<Long, Assignment> dbMap = assignmentRepository.findAllById(assignmentIds)
                .stream()
                .collect(Collectors.toMap(Assignment::getId, Function.identity()));

        // Bước 3: Sync với in-memory lookup (0 query)
        int synced = 0;
        for (JsonNode module : modules) {
            JsonNode lessons = module.path("lessons");
            if (!lessons.isArray()) { continue; }

            for (JsonNode lesson : lessons) {
                if (!"assignment".equals(lesson.path("type").asText(null))) { continue; }

                Long assignmentId = lesson.path("id").asLong(0L);
                if (assignmentId == null || assignmentId == 0L) { continue; }

                Assignment assignment = dbMap.get(assignmentId);
                if (assignment == null) { continue; }

                boolean changed = false;

                boolean newAiEnabled = lesson.path("aiGradingEnabled").asBoolean(false);
                if (newAiEnabled != Boolean.TRUE.equals(assignment.getAiGradingEnabled())) {
                    assignment.setAiGradingEnabled(newAiEnabled);
                    changed = true;
                }

                String newGradingStyle = lesson.path("gradingStyle").asText(null);
                if (!equalsNullSafe(newGradingStyle, assignment.getGradingStyle())) {
                    assignment.setGradingStyle(newGradingStyle);
                    changed = true;
                }

                String newAiPrompt = lesson.path("aiGradingPrompt").asText(null);
                if (!equalsNullSafe(newAiPrompt, assignment.getAiGradingPrompt())) {
                    assignment.setAiGradingPrompt(newAiPrompt);
                    changed = true;
                }

                boolean newTrustEnabled = lesson.path("trustAiEnabled").asBoolean(false);
                if (newTrustEnabled != Boolean.TRUE.equals(assignment.getTrustAiEnabled())) {
                    assignment.setTrustAiEnabled(newTrustEnabled);
                    changed = true;
                }

                if (changed) {
                    assignment.setUpdatedAt(now());
                    assignmentRepository.save(assignment);
                    log.info("[RevisionApproval] Synced AI grading fields to assignment {}: aiEnabled={}, trustAi={}",
                            assignmentId, newAiEnabled, newTrustEnabled);
                }
                synced++;
            }
        }
        log.info("[RevisionApproval] AI grading sync complete: {} assignments scanned", synced);
    }

    private static boolean equalsNullSafe(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    @Override
    @Transactional
    public CourseRevisionDTO rejectRevision(Long revisionId, Long adminId, String reason) {
        ensureRevisionApprovalEnabled();

        CourseRevision revision = getRevisionOrThrow(revisionId);
        if (revision.getStatus() != CourseRevisionStatus.PENDING) {
            throw new ConflictException("COURSE_REVISION_CANNOT_BE_REJECTED_IN_STATUS_" + revision.getStatus());
        }

        revision.setStatus(CourseRevisionStatus.REJECTED);
        revision.setRejectedAt(now());
        revision.setRejectionReason(reason);
        revision.setUpdatedAt(now());
        revision.setRejectedSnapshotHash(computeRevisionSnapshotHash(revision));

        CourseRevision saved = courseRevisionRepository.save(revision);
        logRevisionEvent(
                "reject",
                revision.getCourse().getId(),
                saved.getId(),
                saved.getSourceRevisionId(),
                adminId,
                "REJECTED"
        );
        return toRevisionDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CourseRevision> getLatestApprovedRevision(Long courseId) {
        return courseRevisionRepository
                .findTopByCourseIdAndStatusOrderByRevisionNumberDesc(courseId, CourseRevisionStatus.APPROVED);
    }

    private CourseRevision buildRevisionFromSource(
            Course course,
            CourseRevision source,
            int revisionNumber,
            Long actorId
    ) {
        if (source != null) {
            return CourseRevision.builder()
                    .course(course)
                    .revisionNumber(revisionNumber)
                    .status(CourseRevisionStatus.DRAFT)
                    .title(source.getTitle())
                    .description(source.getDescription())
                    .level(source.getLevel())
                    .category(source.getCategory())
                    .shortDescription(source.getShortDescription())
                    .estimatedDurationHours(source.getEstimatedDurationHours())
                    .language(source.getLanguage())
                    .price(source.getPrice())
                    .currency(source.getCurrency())
                    .learningObjectivesJson(defaultJsonArray(source.getLearningObjectivesJson()))
                    .requirementsJson(defaultJsonArray(source.getRequirementsJson()))
                    .courseSkillTagsJson(defaultJsonArray(source.getCourseSkillTagsJson()))
                    .contentSnapshotJson(defaultContentSnapshot(source.getContentSnapshotJson()))
                    .thumbnail(source.getThumbnail())
                    .sourceRevisionId(source.getId())
                    .sourceCourseStatus(course.getStatus().name())
                    .createdBy(actorId)
                    .build();
        }

        return CourseRevision.builder()
                .course(course)
                .revisionNumber(revisionNumber)
                .status(CourseRevisionStatus.DRAFT)
                .title(course.getTitle())
                .description(course.getDescription())
                .level(course.getLevel())
                .category(course.getCategory())
                .shortDescription(course.getShortDescription())
                .estimatedDurationHours(course.getEstimatedDurationHours())
                .language(course.getLanguage())
                .price(course.getPrice())
                .currency(course.getCurrency())
                .learningObjectivesJson(writeJsonSafely(course.getLearningObjectives(), "[]"))
                .requirementsJson(writeJsonSafely(course.getRequirements(), "[]"))
                .courseSkillTagsJson(writeJsonSafely(course.getCourseSkillTags(), "[]"))
                .contentSnapshotJson(defaultContentSnapshot(
                        CourseRevisionSnapshotAssembler.buildCourseContentSnapshot(
                                objectMapper,
                                course,
                                CONTENT_SNAPSHOT_VERSION_V1
                        )
                ))
                .thumbnail(course.getThumbnail())
                .sourceRevisionId(null)
                .sourceCourseStatus(course.getStatus().name())
                .createdBy(actorId)
                .build();
    }

    private CourseRevision resolveSourceRevision(Course course) {
        if (course.getActiveRevisionId() != null) {
            return courseRevisionRepository.findById(course.getActiveRevisionId()).orElse(null);
        }

        return courseRevisionRepository.findTopByCourseIdOrderByRevisionNumberDesc(course.getId()).orElse(null);
    }

    private CourseRevision getRevisionOrThrow(Long revisionId) {
        return courseRevisionRepository.findById(revisionId)
                .orElseThrow(() -> new NotFoundException("COURSE_REVISION_NOT_FOUND"));
    }

    private Course getCourseOrThrow(Long id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("COURSE_NOT_FOUND"));
    }

    private boolean hasMeaningfulChangesComparedToBaseline(CourseRevision revision, Course course) {
        String currentHash = computeRevisionSnapshotHash(revision);
        revision.setSnapshotHash(currentHash);
        if (revision.getSnapshotVersion() == null) {
            revision.setSnapshotVersion(1);
        }

        String baselineHash = resolveBaselineHashForComparison(revision, course);
        if (baselineHash == null || baselineHash.isBlank()) {
            if (isSourceBaselineRequired(revision)) {
                recordSubmitGuardMetric("blocked", "baseline_not_found");
                throw new ConflictException("COURSE_REVISION_BASELINE_NOT_FOUND");
            }
            recordBaselineFallbackMetric("baseline_unresolved_legacy_allow");
            log.warn("Cannot resolve baseline hash for revision {}. Submit remains allowed for backward compatibility.",
                    revision.getId());
            return true;
        }
        revision.setBaselineSnapshotHash(baselineHash);
        return !Objects.equals(currentHash, baselineHash);
    }

    private String resolveBaselineHashForComparison(CourseRevision revision, Course course) {
        if (revision.getBaselineSnapshotHash() != null && !revision.getBaselineSnapshotHash().isBlank()) {
            return revision.getBaselineSnapshotHash();
        }
        return resolveBaselineHashForRevision(revision, course);
    }

    private String resolveBaselineHashForRevision(CourseRevision revision, Course course) {
        Long sourceRevisionId = revision.getSourceRevisionId();
        if (sourceRevisionId != null && !sourceRevisionId.equals(revision.getId())) {
            Optional<CourseRevision> sourceOpt = courseRevisionRepository.findById(sourceRevisionId);
            if (sourceOpt.isPresent()) {
                return getOrComputeRevisionSnapshotHash(sourceOpt.get());
            }
            if (isSourceBaselineRequired(revision)) {
                throw new ConflictException("COURSE_REVISION_BASELINE_NOT_FOUND");
            }
            recordBaselineFallbackMetric("source_revision_missing");
            log.warn("Source revision {} not found for revision {}, fallback to active/course baseline",
                    sourceRevisionId, revision.getId());
        }

        Long activeRevisionId = course.getActiveRevisionId();
        if (activeRevisionId != null && !activeRevisionId.equals(revision.getId())) {
            Optional<CourseRevision> activeOpt = courseRevisionRepository.findById(activeRevisionId);
            if (activeOpt.isPresent()) {
                return getOrComputeRevisionSnapshotHash(activeOpt.get());
            }
            recordBaselineFallbackMetric("active_revision_missing");
            log.warn("Active revision {} not found for course {}, fallback to course baseline",
                    activeRevisionId, course.getId());
        }

        return computeCourseSnapshotHash(course);
    }

    private String resolveBaselineHashForNewRevision(Course course, CourseRevision source) {
        if (source != null) {
            return getOrComputeRevisionSnapshotHash(source);
        }
        return computeCourseSnapshotHash(course);
    }

    private String getOrComputeRevisionSnapshotHash(CourseRevision revision) {
        if (revision.getSnapshotHash() != null && !revision.getSnapshotHash().isBlank()) {
            return revision.getSnapshotHash();
        }
        String computed = computeRevisionSnapshotHash(revision);
        revision.setSnapshotHash(computed);
        if (revision.getSnapshotVersion() == null) {
            revision.setSnapshotVersion(1);
        }
        try {
            courseRevisionRepository.save(revision);
        } catch (Exception ex) {
            log.warn("Failed to persist backfilled snapshot hash for revision {}: {}",
                    revision.getId(), ex.getMessage());
        }
        return computed;
    }

    private String computeRevisionSnapshotHash(CourseRevision revision) {
        long startedNanos = System.nanoTime();
        try {
            JsonNode snapshot = buildCanonicalSnapshot(
                    revision.getTitle(),
                    revision.getDescription(),
                    revision.getLevel(),
                    revision.getCategory(),
                    revision.getShortDescription(),
                    revision.getEstimatedDurationHours(),
                    revision.getLanguage(),
                    revision.getPrice(),
                    revision.getCurrency(),
                    defaultJsonArray(revision.getLearningObjectivesJson()),
                    defaultJsonArray(revision.getRequirementsJson()),
                    defaultJsonArray(revision.getCourseSkillTagsJson()),
                    defaultContentSnapshot(revision.getContentSnapshotJson())
            );
            return sha256Hex(snapshot.toString());
        } finally {
            recordSnapshotHashLatency("revision", revision != null ? revision.getCourse() : null, startedNanos);
        }
    }

    private String computeCourseSnapshotHash(Course course) {
        long startedNanos = System.nanoTime();
        try {
            JsonNode snapshot = buildCanonicalSnapshot(
                    course.getTitle(),
                    course.getDescription(),
                    course.getLevel(),
                    course.getCategory(),
                    course.getShortDescription(),
                    course.getEstimatedDurationHours(),
                    course.getLanguage(),
                    course.getPrice(),
                    course.getCurrency(),
                    writeJsonSafely(course.getLearningObjectives(), "[]"),
                    writeJsonSafely(course.getRequirements(), "[]"),
                    writeJsonSafely(course.getCourseSkillTags(), "[]"),
                    CourseRevisionSnapshotAssembler.buildCourseContentSnapshot(
                            objectMapper,
                            course,
                            CONTENT_SNAPSHOT_VERSION_V1
                    )
            );
            return sha256Hex(snapshot.toString());
        } finally {
            recordSnapshotHashLatency("course", course, startedNanos);
        }
    }

    private JsonNode buildCanonicalSnapshot(
            String title,
            String description,
            String level,
            String category,
            String shortDescription,
            Integer estimatedDurationHours,
            String language,
            BigDecimal price,
            String currency,
            JsonNode learningObjectives,
            JsonNode requirements,
            JsonNode courseSkillTags,
            JsonNode contentSnapshot
    ) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("snapshotVersion", 1);
        putNullableText(root, "title", title);
        putNullableText(root, "description", description);
        putNullableText(root, "level", level);
        putNullableText(root, "category", category);
        putNullableText(root, "shortDescription", shortDescription);
        if (estimatedDurationHours == null) root.putNull("estimatedDurationHours");
        else root.put("estimatedDurationHours", estimatedDurationHours);
        putNullableText(root, "language", language);
        putNullableText(root, "currency", currency);
        putNullableText(root, "price", normalizeMoney(price));
        root.set("learningObjectives", canonicalizeStringArray(learningObjectives));
        root.set("requirements", canonicalizeStringArray(requirements));
        root.set("courseSkillTags", canonicalizeStringArray(courseSkillTags));
        root.set("contentSnapshot", canonicalizeContentSnapshotForHash(contentSnapshot));
        return root;
    }

    /**
     * Compatibility metadata controls auto-upgrade policy evaluation and must not alter
     * no-meaningful-change hashing for revision content identity.
     */
    private JsonNode canonicalizeContentSnapshotForHash(JsonNode contentSnapshot) {
        JsonNode normalized = defaultContentSnapshot(contentSnapshot);
        if (!(normalized instanceof ObjectNode normalizedObject)) {
            return canonicalizeJsonNode(normalized);
        }
        ObjectNode hashSafeSnapshot = normalizedObject.deepCopy();
        hashSafeSnapshot.remove("compatibility");
        return canonicalizeJsonNode(hashSafeSnapshot);
    }

    private String normalizeText(String value) {
        if (value == null) return null;
        String collapsed = value.replaceAll("\\s+", " ").trim();
        return collapsed.isEmpty() ? null : collapsed;
    }

    private String normalizeMoney(BigDecimal money) {
        if (money == null) return null;
        return money.stripTrailingZeros().toPlainString();
    }

    private void putNullableText(ObjectNode node, String field, String value) {
        String normalized = normalizeText(value);
        if (normalized == null) {
            node.putNull(field);
        } else {
            node.put(field, normalized);
        }
    }

    private ArrayNode canonicalizeStringArray(JsonNode rawArray) {
        JsonNode source = defaultJsonArray(rawArray);
        ArrayNode canonical = JsonNodeFactory.instance.arrayNode();
        for (JsonNode item : source) {
            if (item == null || item.isNull()) {
                canonical.addNull();
                continue;
            }
            if (item.isTextual()) {
                String normalized = normalizeText(item.asText());
                if (normalized == null) {
                    canonical.addNull();
                } else {
                    canonical.add(normalized);
                }
                continue;
            }
            canonical.add(canonicalizeJsonNode(item));
        }
        return canonical;
    }

    private JsonNode canonicalizeJsonNode(JsonNode source) {
        if (source == null || source.isNull()) {
            return NullNode.getInstance();
        }

        if (source.isObject()) {
            ObjectNode canonical = JsonNodeFactory.instance.objectNode();
            List<String> fieldNames = new ArrayList<>();
            source.fieldNames().forEachRemaining(fieldNames::add);
            Collections.sort(fieldNames);
            for (String fieldName : fieldNames) {
                canonical.set(fieldName, canonicalizeJsonNode(source.get(fieldName)));
            }
            return canonical;
        }

        if (source.isArray()) {
            ArrayNode canonical = JsonNodeFactory.instance.arrayNode();
            for (JsonNode child : source) {
                canonical.add(canonicalizeJsonNode(child));
            }
            return canonical;
        }

        return source;
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm not available", ex);
        }
    }

    private void validateContentSnapshotSize(
            JsonNode canonicalContentSnapshot,
            Long courseId,
            Long revisionId,
            Long actorId
    ) {
        int maxBytes = courseRevisionFeatureProperties.getMaxContentSnapshotBytes();
        if (maxBytes <= 0) {
            return;
        }
        int snapshotBytes = canonicalContentSnapshot.toString().getBytes(StandardCharsets.UTF_8).length;
        if (snapshotBytes > maxBytes) {
            log.warn(
                    "course_revision_event action=update_snapshot_rejected courseId={} revisionId={} sourceRevisionId={} actorId={} reasonCode={} snapshotBytes={} maxBytes={}",
                    courseId,
                    revisionId,
                    null,
                    actorId,
                    "COURSE_REVISION_CONTENT_SNAPSHOT_TOO_LARGE",
                    snapshotBytes,
                    maxBytes
            );
            throw new BadRequestException("COURSE_REVISION_CONTENT_SNAPSHOT_TOO_LARGE");
        }
    }

    private void recordSnapshotHashLatency(String scope, Course course, long startedNanos) {
        long durationNanos = Math.max(0L, System.nanoTime() - startedNanos);
        try {
            Timer timer = meterRegistry.timer(
                    SNAPSHOT_HASH_DURATION_METRIC,
                    "scope", scope,
                    "course_id", String.valueOf(course != null ? course.getId() : null)
            );
            timer.record(Duration.ofNanos(durationNanos));
        } catch (Exception ex) {
            log.debug("Unable to record metric {}: {}", SNAPSHOT_HASH_DURATION_METRIC, ex.getMessage());
        }

        long warnMs = courseRevisionFeatureProperties.getSnapshotHashLatencyWarnMs();
        if (warnMs > 0) {
            long durationMs = Duration.ofNanos(durationNanos).toMillis();
            if (durationMs > warnMs) {
                log.warn(
                        "course_revision_event action=snapshot_hash_slow courseId={} revisionId={} sourceRevisionId={} actorId={} reasonCode={} hashScope={} latencyMs={} thresholdMs={}",
                        course != null ? course.getId() : null,
                        null,
                        null,
                        null,
                        "SNAPSHOT_HASH_SLOW",
                        scope,
                        durationMs,
                        warnMs
                );
            }
        }
    }

    private boolean isSourceBaselineRequired(CourseRevision revision) {
        Integer revisionNumber = revision.getRevisionNumber();
        if (!courseRevisionFeatureProperties.isRequireSourceBaseline()) {
            return false;
        }
        if (revisionNumber == null || revisionNumber <= 1) {
            return false;
        }

        int rolloutPercent = normalizeRolloutPercent(
                courseRevisionFeatureProperties.getRequireSourceBaselineRolloutPercent()
        );
        if (rolloutPercent <= 0) {
            return false;
        }
        if (rolloutPercent >= 100) {
            return true;
        }

        Long courseId = revision.getCourse() != null ? revision.getCourse().getId() : null;
        if (courseId == null) {
            // Defensive default: enforce when we cannot evaluate canary bucket.
            return true;
        }
        int bucket = Math.floorMod(Long.hashCode(courseId), 100);
        return bucket < rolloutPercent;
    }

    private int normalizeRolloutPercent(int percent) {
        if (percent < 0) {
            return 0;
        }
        return Math.min(percent, 100);
    }

    private boolean isSubmitChangeCheckEnforced() {
        CourseRevisionFeatureProperties.SubmitChangeCheckMode mode =
                courseRevisionFeatureProperties.getSubmitChangeCheckMode();
        if (mode == null) {
            mode = CourseRevisionFeatureProperties.SubmitChangeCheckMode.ENFORCE;
        }
        return mode == CourseRevisionFeatureProperties.SubmitChangeCheckMode.ENFORCE;
    }

    private boolean isSubmitChangeCheckWarnOnly() {
        CourseRevisionFeatureProperties.SubmitChangeCheckMode mode =
                courseRevisionFeatureProperties.getSubmitChangeCheckMode();
        return mode == CourseRevisionFeatureProperties.SubmitChangeCheckMode.WARN;
    }

    private String resolveSubmitCheckModeTag() {
        CourseRevisionFeatureProperties.SubmitChangeCheckMode mode =
                courseRevisionFeatureProperties.getSubmitChangeCheckMode();
        if (mode == null) {
            mode = CourseRevisionFeatureProperties.SubmitChangeCheckMode.ENFORCE;
        }
        return mode.name().toLowerCase(Locale.ROOT);
    }

    private void recordSubmitGuardMetric(String result, String reason) {
        incrementCounter(
                SUBMIT_GUARD_METRIC,
                "result", result,
                "reason", reason,
                "mode", resolveSubmitCheckModeTag()
        );
    }

    private void recordBaselineFallbackMetric(String reason) {
        incrementCounter(
                BASELINE_FALLBACK_METRIC,
                "reason", reason
        );
    }

    private void incrementCounter(String metricName, String... tags) {
        try {
            Counter counter = meterRegistry.counter(metricName, tags);
            if (counter == null) {
                return;
            }
            counter.increment();
        } catch (Exception ex) {
            log.warn("Unable to increment metric {}: {}", metricName, ex.getMessage());
        }
    }

    private void logRevisionEvent(
            String action,
            Long courseId,
            Long revisionId,
            Long sourceRevisionId,
            Long actorId,
            String reasonCode
    ) {
        log.info(
                "course_revision_event action={} courseId={} revisionId={} sourceRevisionId={} actorId={} reasonCode={}",
                action,
                courseId,
                revisionId,
                sourceRevisionId,
                actorId,
                reasonCode
        );
    }

    private CourseRevisionDTO toRevisionDto(CourseRevision revision) {
        return CourseRevisionDTO.builder()
                .id(revision.getId())
                .courseId(revision.getCourse().getId())
                .revisionNumber(revision.getRevisionNumber())
                .status(revision.getStatus())
                .title(revision.getTitle())
                .description(revision.getDescription())
                .level(revision.getLevel())
                .category(revision.getCategory())
                .shortDescription(revision.getShortDescription())
                .estimatedDurationHours(revision.getEstimatedDurationHours())
                .language(revision.getLanguage())
                .price(revision.getPrice())
                .currency(revision.getCurrency())
                .learningObjectivesJson(toJsonText(revision.getLearningObjectivesJson(), "[]"))
                .requirementsJson(toJsonText(revision.getRequirementsJson(), "[]"))
                .courseSkillTagsJson(toJsonText(revision.getCourseSkillTagsJson(), "[]"))
                .contentSnapshotJson(toJsonText(revision.getContentSnapshotJson(), "{}"))
                .thumbnailMediaId(revision.getThumbnail() != null ? revision.getThumbnail().getId() : null)
                .thumbnailUrl(revision.getThumbnail() != null ? revision.getThumbnail().getUrl() : null)
                .sourceRevisionId(revision.getSourceRevisionId())
                .sourceCourseStatus(revision.getSourceCourseStatus())
                .createdBy(revision.getCreatedBy())
                .createdAt(revision.getCreatedAt())
                .updatedAt(revision.getUpdatedAt())
                .submittedAt(revision.getSubmittedAt())
                .approvedAt(revision.getApprovedAt())
                .rejectedAt(revision.getRejectedAt())
                .rejectionReason(revision.getRejectionReason())
                .archivedAt(revision.getArchivedAt())
                .build();
    }

    private PageResponse<CourseRevisionDTO> toPageResponse(Page<CourseRevision> page) {
        return PageResponse.<CourseRevisionDTO>builder()
                .items(page.map(this::toRevisionDto).getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .total(page.getTotalElements())
                .build();
    }

    /**
     * Upload thumbnail for a course revision to Cloudinary.
     * Mirrors {@link com.exe.skillverse_backend.course_service.service.impl.CourseServiceImpl#uploadThumbnail}.
     */
    private Media uploadRevisionThumbnail(MultipartFile thumbnailFile, Long uploaderId) {
        try {
            log.info("Uploading thumbnail for revision: {}", thumbnailFile.getOriginalFilename());
            String folder = "skillverse/revisions/" + uploaderId;
            Map<String, Object> uploadResult = cloudinaryService.uploadImage(thumbnailFile, folder);
            String publicUrl = (String) uploadResult.get("url");
            String publicId = (String) uploadResult.get("public_id");
            String resourceType = (String) uploadResult.get("resource_type");
            Media thumbnail = new Media();
            thumbnail.setUrl(publicUrl);
            thumbnail.setType(thumbnailFile.getContentType());
            thumbnail.setFileName(thumbnailFile.getOriginalFilename());
            thumbnail.setFileSize(thumbnailFile.getSize());
            thumbnail.setUploadedBy(uploaderId);
            thumbnail.setUploadedAt(java.time.LocalDateTime.now());
            thumbnail.setCloudinaryPublicId(publicId);
            thumbnail.setCloudinaryResourceType(resourceType);
            Media saved = mediaRepository.save(thumbnail);
            log.info("Revision thumbnail uploaded: {} - {}", saved.getId(), saved.getUrl());
            return saved;
        } catch (Exception e) {
            log.error("Failed to upload revision thumbnail: {}", e.getMessage());
            throw new MediaOperationException("Thumbnail upload failed: " + e.getMessage(), e);
        }
    }

    private JsonNode writeJsonSafely(Object value, String fallbackJson) {
        JsonNode node = objectMapper.valueToTree(value);
        if (node == null || node.isNull()) {
            return parseJsonSafely(fallbackJson);
        }
        return node;
    }

    private JsonNode defaultJsonArray(JsonNode value) {
        if (value == null || value.isNull()) {
            return parseJsonSafely("[]");
        }
        return value;
    }

    private JsonNode defaultJsonObject(JsonNode value) {
        if (value == null || value.isNull()) {
            return parseJsonSafely("{}");
        }
        return value;
    }

    private JsonNode defaultContentSnapshot(JsonNode value) {
        return normalizeContentSnapshot(defaultJsonObject(value));
    }

    private JsonNode normalizeContentSnapshot(JsonNode value) {
        JsonNode canonical = canonicalizeJsonNode(defaultJsonObject(value));
        if (!canonical.isObject()) {
            throw new BadRequestException("COURSE_REVISION_INVALID_CONTENT_SNAPSHOT");
        }
        ObjectNode objectNode = (ObjectNode) canonical;
        JsonNode versionNode = objectNode.get("snapshotVersion");
        if (versionNode == null || versionNode.isNull()) {
            objectNode.put("snapshotVersion", CONTENT_SNAPSHOT_VERSION_V1);
        } else if (!versionNode.canConvertToInt()) {
            throw new BadRequestException("COURSE_REVISION_UNSUPPORTED_SNAPSHOT_VERSION");
        } else {
            int snapshotVersion = versionNode.asInt();
            if (snapshotVersion != CONTENT_SNAPSHOT_VERSION_V1) {
                throw new BadRequestException("COURSE_REVISION_UNSUPPORTED_SNAPSHOT_VERSION");
            }
        }
        ensureSnapshotCompatibility(objectNode);
        return objectNode;
    }

    private void ensureSnapshotCompatibility(ObjectNode snapshot) {
        if (snapshot == null) {
            return;
        }
        ObjectNode compatibilityNode;
        JsonNode rawCompatibility = snapshot.get("compatibility");
        if (rawCompatibility instanceof ObjectNode objectNode) {
            compatibilityNode = objectNode;
        } else {
            compatibilityNode = snapshot.putObject("compatibility");
        }

        JsonNode autoCompatibleNode = compatibilityNode.get("autoCompatibleOnly");
        Boolean normalizedAutoCompatible = parseCompatibilityBoolean(autoCompatibleNode);
        if (normalizedAutoCompatible == null) {
            compatibilityNode.put("autoCompatibleOnly", true);
        } else {
            compatibilityNode.put("autoCompatibleOnly", normalizedAutoCompatible);
        }

        JsonNode levelNode = compatibilityNode.get("level");
        compatibilityNode.put("level", normalizeCompatibilityLevel(levelNode));
    }

    private Boolean parseCompatibilityBoolean(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isNumber()) {
            return node.intValue() != 0;
        }
        if (!node.isTextual()) {
            return null;
        }
        String normalized = node.asText("").trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }
        if ("true".equals(normalized)
                || "1".equals(normalized)
                || "yes".equals(normalized)
                || "y".equals(normalized)) {
            return Boolean.TRUE;
        }
        if ("false".equals(normalized)
                || "0".equals(normalized)
                || "no".equals(normalized)
                || "n".equals(normalized)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private String normalizeCompatibilityLevel(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return "NON_BREAKING";
        }
        if (!node.isTextual()) {
            return "NON_BREAKING";
        }
        String raw = node.asText("").trim();
        if (raw.isBlank()) {
            return "NON_BREAKING";
        }
        String normalized = raw.toUpperCase(Locale.ROOT);
        if ("AUTO_COMPATIBLE_ONLY".equals(normalized)) {
            return "NON_BREAKING";
        }
        return normalized;
    }

    private JsonNode materializeSnapshotIdentityForSubmit(JsonNode contentSnapshot, Course course, Long revisionId) {
        JsonNode normalized = defaultContentSnapshot(contentSnapshot);
        if (!(normalized instanceof ObjectNode snapshotObject)) {
            return normalized;
        }

        Set<Long> claimedModuleIds = new LinkedHashSet<>();
        Set<Long> claimedLessonIds = new LinkedHashSet<>();
        Set<Long> claimedQuizIds = new LinkedHashSet<>();
        Set<Long> claimedAssignmentIds = new LinkedHashSet<>();

        JsonNode modulesNode = snapshotObject.path("modules");
        if (!modulesNode.isArray()) {
            return snapshotObject;
        }

        for (int moduleIndex = 0; moduleIndex < modulesNode.size(); moduleIndex++) {
            JsonNode rawModuleNode = modulesNode.get(moduleIndex);
            if (!(rawModuleNode instanceof ObjectNode moduleNode)) {
                continue;
            }
            String moduleIdPath = "modules[" + moduleIndex + "].id";
            Module module = resolveOrCreateModuleIdentity(
                    moduleNode,
                    moduleIdPath,
                    course,
                    moduleIndex,
                    revisionId,
                    claimedModuleIds
            );
            moduleNode.put("id", module.getId());

            JsonNode lessonLikeItems = moduleNode.path("lessons");
            if (!lessonLikeItems.isArray()) {
                continue;
            }

            for (int itemIndex = 0; itemIndex < lessonLikeItems.size(); itemIndex++) {
                JsonNode rawItemNode = lessonLikeItems.get(itemIndex);
                if (!(rawItemNode instanceof ObjectNode itemNode)) {
                    continue;
                }
                String itemIdPath = "modules[" + moduleIndex + "].lessons[" + itemIndex + "].id";
                String itemType = normalizeSnapshotItemType(itemNode);
                Long itemId = resolveOrCreateItemIdentity(
                        itemNode,
                        itemType,
                        itemIdPath,
                        module,
                        itemIndex,
                    revisionId,
                    claimedLessonIds,
                    claimedQuizIds,
                    claimedAssignmentIds
                );
                itemNode.put("id", itemId);
            }
        }

        return snapshotObject;
    }

    private Module resolveOrCreateModuleIdentity(
            ObjectNode moduleNode,
            String path,
            Course course,
            int fallbackOrderIndex,
            Long revisionId,
            Set<Long> claimedModuleIds
    ) {
        Long explicitId = parseExplicitPositiveId(moduleNode);
        if (explicitId != null) {
            Optional<Module> existingOpt = moduleRepository.findById(explicitId);
            if (existingOpt.isPresent()) {
                Module existing = existingOpt.get();
                Long moduleCourseId = existing.getCourse() != null ? existing.getCourse().getId() : null;
                if (moduleCourseId != null
                        && moduleCourseId.equals(course.getId())
                        && (claimedModuleIds == null || !claimedModuleIds.contains(existing.getId()))) {
                    if (claimedModuleIds != null) {
                        claimedModuleIds.add(existing.getId());
                    }
                    return existing;
                }
                log.warn(
                        "course_revision_event action=materialize_module_identity_fallback_new courseId={} revisionId={} path={} reasonCode={}",
                        course.getId(),
                        revisionId,
                        path,
                        "MODULE_ID_SCOPE_MISMATCH"
                );
            } else {
                log.warn(
                        "course_revision_event action=materialize_module_identity_fallback_new courseId={} revisionId={} path={} reasonCode={}",
                        course.getId(),
                        revisionId,
                        path,
                        "MODULE_ID_NOT_FOUND"
                );
            }
        }

        Module equivalentModule = findEquivalentModuleInCourse(course, moduleNode, claimedModuleIds);
        if (equivalentModule != null) {
            log.info(
                    "course_revision_event action=materialize_module_identity_reused courseId={} revisionId={} path={} reasonCode={} moduleId={}",
                    course.getId(),
                    revisionId,
                    path,
                    "MODULE_EQUIVALENT_REUSE_NO_ID",
                    equivalentModule.getId()
            );
            if (claimedModuleIds != null) {
                claimedModuleIds.add(equivalentModule.getId());
            }
            return equivalentModule;
        }

        Module created = Module.builder()
                .course(course)
                .title(textOrDefault(moduleNode.path("title"), "Module " + (fallbackOrderIndex + 1)))
                .description(textOrNull(moduleNode.path("description")))
                .orderIndex(parseInteger(moduleNode.path("orderIndex"), fallbackOrderIndex))
                .createdAt(now())
                .updatedAt(now())
                .build();
        Module saved = moduleRepository.save(created);
        if (claimedModuleIds != null && saved.getId() != null) {
            claimedModuleIds.add(saved.getId());
        }
        return saved;
    }

    private Long resolveOrCreateItemIdentity(
            ObjectNode itemNode,
            String itemType,
            String path,
            Module module,
            int fallbackOrderIndex,
            Long revisionId,
            Set<Long> claimedLessonIds,
            Set<Long> claimedQuizIds,
            Set<Long> claimedAssignmentIds
    ) {
        return switch (itemType) {
            case "quiz" -> resolveQuizIdentity(itemNode, path, module, fallbackOrderIndex, revisionId, claimedQuizIds);
            case "assignment" -> resolveAssignmentIdentity(
                    itemNode,
                    path,
                    module,
                    fallbackOrderIndex,
                    revisionId,
                    claimedAssignmentIds
            );
            default -> resolveLessonIdentity(itemNode, path, module, fallbackOrderIndex, revisionId, claimedLessonIds);
        };
    }

    private Long resolveLessonIdentity(
            ObjectNode itemNode,
            String path,
            Module module,
            int fallbackOrderIndex,
            Long revisionId,
            Set<Long> claimedLessonIds
    ) {
        Long explicitId = parseExplicitPositiveId(itemNode);
        if (explicitId == null) {
            Long equivalentId = findEquivalentLessonIdInModule(itemNode, module, claimedLessonIds);
            if (equivalentId != null) {
                log.info(
                        "course_revision_event action=materialize_lesson_identity_reused courseId={} revisionId={} path={} reasonCode={} lessonId={}",
                        module.getCourse().getId(),
                        revisionId,
                        path,
                        "LESSON_EQUIVALENT_REUSE_NO_ID",
                        equivalentId
                );
                if (claimedLessonIds != null) {
                    claimedLessonIds.add(equivalentId);
                }
                return equivalentId;
            }
            Lesson created = createLessonFromSnapshot(itemNode, module, fallbackOrderIndex);
            if (claimedLessonIds != null && created.getId() != null) {
                claimedLessonIds.add(created.getId());
            }
            return created.getId();
        }

        Optional<Lesson> existingOpt = lessonRepository.findById(explicitId);
        if (existingOpt.isEmpty()) {
            Long equivalentId = findEquivalentLessonIdInModule(itemNode, module, claimedLessonIds);
            if (equivalentId != null) {
                if (claimedLessonIds != null) {
                    claimedLessonIds.add(equivalentId);
                }
                return equivalentId;
            }
            Lesson created = createLessonFromSnapshot(itemNode, module, fallbackOrderIndex);
            if (claimedLessonIds != null && created.getId() != null) {
                claimedLessonIds.add(created.getId());
            }
            return created.getId();
        }

        Lesson existing = existingOpt.get();
        Long lessonCourseId = extractCourseId(existing.getModule());
        if (lessonCourseId == null || !lessonCourseId.equals(module.getCourse().getId())) {
            log.warn(
                    "course_revision_event action=materialize_lesson_identity_fallback_new courseId={} revisionId={} path={} reasonCode={}",
                    module.getCourse().getId(),
                    revisionId,
                    path,
                    "LESSON_ID_SCOPE_MISMATCH"
            );
            Long equivalentId = findEquivalentLessonIdInModule(itemNode, module, claimedLessonIds);
            if (equivalentId != null) {
                if (claimedLessonIds != null) {
                    claimedLessonIds.add(equivalentId);
                }
                return equivalentId;
            }
            Lesson created = createLessonFromSnapshot(itemNode, module, fallbackOrderIndex);
            if (claimedLessonIds != null && created.getId() != null) {
                claimedLessonIds.add(created.getId());
            }
            return created.getId();
        }

        boolean equivalentSnapshot = isLessonEquivalentSnapshot(existing, itemNode);
        if (!equivalentSnapshot) {
            Lesson created = createLessonFromSnapshot(itemNode, module, fallbackOrderIndex);
            if (claimedLessonIds != null && created.getId() != null) {
                claimedLessonIds.add(created.getId());
            }
            return created.getId();
        }

        if (claimedLessonIds != null && claimedLessonIds.contains(explicitId)) {
            Lesson created = createLessonFromSnapshot(itemNode, module, fallbackOrderIndex);
            if (claimedLessonIds != null && created.getId() != null) {
                claimedLessonIds.add(created.getId());
            }
            return created.getId();
        }

        if (!Objects.equals(existing.getModule().getId(), module.getId())) {
            log.info(
                    "course_revision_event action=materialize_lesson_identity_reused courseId={} revisionId={} path={} reasonCode={}",
                    module.getCourse().getId(),
                    revisionId,
                    path,
                    "LESSON_MODULE_CHANGED_REUSE_ID"
            );
        }
        if (claimedLessonIds != null) {
            claimedLessonIds.add(explicitId);
        }
        return explicitId;
    }

    private Long resolveQuizIdentity(
            ObjectNode itemNode,
            String path,
            Module module,
            int fallbackOrderIndex,
            Long revisionId,
            Set<Long> claimedQuizIds
    ) {
        Long explicitId = parseExplicitPositiveId(itemNode);
        if (explicitId == null) {
            Quiz created = createQuizFromSnapshot(itemNode, module, fallbackOrderIndex);
            if (claimedQuizIds != null && created.getId() != null) {
                claimedQuizIds.add(created.getId());
            }
            return created.getId();
        }

        Optional<Quiz> existingOpt = quizRepository.findById(explicitId);
        if (existingOpt.isEmpty()) {
            Quiz created = createQuizFromSnapshot(itemNode, module, fallbackOrderIndex);
            if (claimedQuizIds != null && created.getId() != null) {
                claimedQuizIds.add(created.getId());
            }
            return created.getId();
        }

        Quiz existing = existingOpt.get();
        Long quizCourseId = extractCourseId(existing.getModule());
        if (quizCourseId == null || !quizCourseId.equals(module.getCourse().getId())) {
            log.warn(
                    "course_revision_event action=materialize_quiz_identity_fallback_new courseId={} revisionId={} path={} reasonCode={}",
                    module.getCourse().getId(),
                    revisionId,
                    path,
                    "QUIZ_ID_SCOPE_MISMATCH"
            );
            Quiz created = createQuizFromSnapshot(itemNode, module, fallbackOrderIndex);
            if (claimedQuizIds != null && created.getId() != null) {
                claimedQuizIds.add(created.getId());
            }
            return created.getId();
        }

        boolean equivalentSnapshot = isQuizEquivalentSnapshot(existing, itemNode);
        if (!equivalentSnapshot) {
            Quiz created = createQuizFromSnapshot(itemNode, module, fallbackOrderIndex);
            if (claimedQuizIds != null && created.getId() != null) {
                claimedQuizIds.add(created.getId());
            }
            return created.getId();
        }

        if (claimedQuizIds != null && claimedQuizIds.contains(explicitId)) {
            Quiz created = createQuizFromSnapshot(itemNode, module, fallbackOrderIndex);
            if (claimedQuizIds != null && created.getId() != null) {
                claimedQuizIds.add(created.getId());
            }
            return created.getId();
        }

        if (!Objects.equals(existing.getModule().getId(), module.getId())) {
            log.info(
                    "course_revision_event action=materialize_quiz_identity_reused courseId={} revisionId={} path={} reasonCode={}",
                    module.getCourse().getId(),
                    revisionId,
                    path,
                    "QUIZ_MODULE_CHANGED_REUSE_ID"
            );
        }
        if (claimedQuizIds != null) {
            claimedQuizIds.add(explicitId);
        }
        return explicitId;
    }

    private Long resolveAssignmentIdentity(
            ObjectNode itemNode,
            String path,
            Module module,
            int fallbackOrderIndex,
            Long revisionId,
            Set<Long> claimedAssignmentIds
    ) {
        Long explicitId = parseExplicitPositiveId(itemNode);
        if (explicitId == null) {
            Long equivalentId = findEquivalentAssignmentIdInModule(itemNode, module, claimedAssignmentIds);
            if (equivalentId != null) {
                if (claimedAssignmentIds != null) {
                    claimedAssignmentIds.add(equivalentId);
                }
                return equivalentId;
            }
            Assignment created = createAssignmentFromSnapshot(itemNode, module, fallbackOrderIndex);
            if (claimedAssignmentIds != null && created.getId() != null) {
                claimedAssignmentIds.add(created.getId());
            }
            return created.getId();
        }

        Optional<Assignment> existingOpt = assignmentRepository.findById(explicitId);
        if (existingOpt.isEmpty()) {
            Long equivalentId = findEquivalentAssignmentIdInModule(itemNode, module, claimedAssignmentIds);
            if (equivalentId != null) {
                if (claimedAssignmentIds != null) {
                    claimedAssignmentIds.add(equivalentId);
                }
                return equivalentId;
            }
            Assignment created = createAssignmentFromSnapshot(itemNode, module, fallbackOrderIndex);
            if (claimedAssignmentIds != null && created.getId() != null) {
                claimedAssignmentIds.add(created.getId());
            }
            return created.getId();
        }

        Assignment existing = existingOpt.get();
        Long assignmentCourseId = extractCourseId(existing.getModule());
        if (assignmentCourseId == null || !assignmentCourseId.equals(module.getCourse().getId())) {
            log.warn(
                    "course_revision_event action=materialize_assignment_identity_fallback_new courseId={} revisionId={} path={} reasonCode={}",
                    module.getCourse().getId(),
                    revisionId,
                    path,
                    "ASSIGNMENT_ID_SCOPE_MISMATCH"
            );
            Long equivalentId = findEquivalentAssignmentIdInModule(itemNode, module, claimedAssignmentIds);
            if (equivalentId != null) {
                if (claimedAssignmentIds != null) {
                    claimedAssignmentIds.add(equivalentId);
                }
                return equivalentId;
            }
            Assignment created = createAssignmentFromSnapshot(itemNode, module, fallbackOrderIndex);
            if (claimedAssignmentIds != null && created.getId() != null) {
                claimedAssignmentIds.add(created.getId());
            }
            return created.getId();
        }

        boolean equivalentSnapshot = isAssignmentEquivalentSnapshot(existing, itemNode);
        if (!equivalentSnapshot) {
            Assignment created = createAssignmentFromSnapshot(itemNode, module, fallbackOrderIndex);
            if (claimedAssignmentIds != null && created.getId() != null) {
                claimedAssignmentIds.add(created.getId());
            }
            return created.getId();
        }

        if (claimedAssignmentIds != null && claimedAssignmentIds.contains(explicitId)) {
            Assignment created = createAssignmentFromSnapshot(itemNode, module, fallbackOrderIndex);
            if (claimedAssignmentIds != null && created.getId() != null) {
                claimedAssignmentIds.add(created.getId());
            }
            return created.getId();
        }

        if (!Objects.equals(existing.getModule().getId(), module.getId())) {
            log.info(
                    "course_revision_event action=materialize_assignment_identity_reused courseId={} revisionId={} path={} reasonCode={}",
                    module.getCourse().getId(),
                    revisionId,
                    path,
                    "ASSIGNMENT_MODULE_CHANGED_REUSE_ID"
            );
        }
        if (claimedAssignmentIds != null) {
            claimedAssignmentIds.add(explicitId);
        }
        return explicitId;
    }

    private Module findEquivalentModuleInCourse(
            Course course,
            ObjectNode moduleNode,
            Set<Long> claimedModuleIds
    ) {
        if (course == null || course.getId() == null) {
            return null;
        }
        String snapshotTitle = normalizeText(textOrNull(moduleNode.path("title")));
        if (snapshotTitle == null) {
            return null;
        }
        String snapshotDescription = normalizeText(textOrNull(moduleNode.path("description")));

        List<Module> candidates = moduleRepository.findByCourseIdOrderByOrderIndexAsc(course.getId());
        for (Module candidate : candidates) {
            if (candidate == null || candidate.getId() == null) {
                continue;
            }
            if (claimedModuleIds != null && claimedModuleIds.contains(candidate.getId())) {
                continue;
            }
            if (!Objects.equals(normalizeText(candidate.getTitle()), snapshotTitle)) {
                continue;
            }
            if (snapshotDescription != null
                    && !Objects.equals(normalizeText(candidate.getDescription()), snapshotDescription)) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    private Long findEquivalentLessonIdInModule(
            ObjectNode itemNode,
            Module module,
            Set<Long> claimedLessonIds
    ) {
        if (module == null || module.getId() == null) {
            return null;
        }
        List<Lesson> candidates = lessonRepository.findByModuleIdOrderByOrderIndexAsc(module.getId());
        for (Lesson candidate : candidates) {
            if (candidate == null || candidate.getId() == null) {
                continue;
            }
            if (claimedLessonIds != null && claimedLessonIds.contains(candidate.getId())) {
                continue;
            }
            if (isLessonEquivalentSnapshot(candidate, itemNode)) {
                return candidate.getId();
            }
        }
        return null;
    }

    private Long findEquivalentAssignmentIdInModule(
            ObjectNode itemNode,
            Module module,
            Set<Long> claimedAssignmentIds
    ) {
        if (module == null || module.getId() == null) {
            return null;
        }
        List<Assignment> candidates = assignmentRepository.findByModuleIdOrderByOrderIndexAsc(module.getId());
        for (Assignment candidate : candidates) {
            if (candidate == null || candidate.getId() == null) {
                continue;
            }
            if (claimedAssignmentIds != null && claimedAssignmentIds.contains(candidate.getId())) {
                continue;
            }
            if (isAssignmentEquivalentSnapshot(candidate, itemNode)) {
                return candidate.getId();
            }
        }
        return null;
    }

    private Lesson createLessonFromSnapshot(ObjectNode itemNode, Module module, int fallbackOrderIndex) {
        Lesson lesson = Lesson.builder()
                .module(module)
                .title(textOrDefault(itemNode.path("title"), "Bài học"))
                .type(resolveLessonType(itemNode))
                .orderIndex(parseInteger(itemNode.path("orderIndex"), fallbackOrderIndex))
                .contentText(textOrNull(itemNode.path("contentText")))
                .resourceUrl(textOrNull(itemNode.path("resourceUrl")))
                .videoUrl(firstNonBlank(
                        textOrNull(itemNode.path("youtubeUrl")),
                        textOrNull(itemNode.path("videoUrl"))
                ))
                .videoMedia(resolveMedia(itemNode.path("videoMediaId")))
                .durationSec(resolveDurationSec(itemNode))
                .createdAt(now())
                .updatedAt(now())
                .attachments(new ArrayList<>())
                .build();

        List<LessonAttachment> attachments = buildLessonAttachments(itemNode.path("attachments"));
        for (LessonAttachment attachment : attachments) {
            attachment.setLesson(lesson);
        }
        lesson.setAttachments(attachments);
        return lessonRepository.save(lesson);
    }

    private Quiz createQuizFromSnapshot(ObjectNode itemNode, Module module, int fallbackOrderIndex) {
        Quiz quiz = Quiz.builder()
                .module(module)
                .title(textOrDefault(itemNode.path("title"), "Quiz"))
                .description(firstNonBlank(
                        textOrNull(itemNode.path("quizDescription")),
                        textOrNull(itemNode.path("description")),
                        textOrNull(itemNode.path("contentText"))
                ))
                .passScore(parseInteger(itemNode.path("passScore"), 80))
                .maxAttempts(parseInteger(itemNode.path("quizMaxAttempts"), null))
                .timeLimitMinutes(parseInteger(itemNode.path("quizTimeLimitMinutes"), null))
                .roundingIncrement(parseInteger(itemNode.path("roundingIncrement"), null))
                .gradingMethod(parseQuizGradingMethod(itemNode.path("gradingMethod")))
                .cooldownHours(parseInteger(itemNode.path("cooldownHours"), null))
                .orderIndex(parseInteger(itemNode.path("orderIndex"), fallbackOrderIndex))
                .createdAt(now())
                .updatedAt(now())
                .questions(buildQuizQuestions(itemNode.path("questions")))
                .build();

        if (quiz.getQuestions() != null) {
            for (QuizQuestion question : quiz.getQuestions()) {
                question.setQuiz(quiz);
                if (question.getOptions() != null) {
                    for (QuizOption option : question.getOptions()) {
                        option.setQuestion(question);
                    }
                }
            }
        }

        return quizRepository.save(quiz);
    }

    private List<QuizQuestion> buildQuizQuestions(JsonNode questionsNode) {
        if (questionsNode == null || !questionsNode.isArray()) {
            return List.of();
        }

        List<QuizQuestion> questions = new ArrayList<>();
        for (int questionIndex = 0; questionIndex < questionsNode.size(); questionIndex++) {
            JsonNode questionNode = questionsNode.get(questionIndex);
            if (!(questionNode instanceof ObjectNode questionObject)) {
                continue;
            }

            QuizQuestion question = QuizQuestion.builder()
                    .questionText(textOrDefault(questionObject.path("text"), "Question " + (questionIndex + 1)))
                    .questionType(parseQuestionType(questionObject.path("type")))
                    .score(parseInteger(questionObject.path("score"), 1))
                    .orderIndex(parseInteger(questionObject.path("orderIndex"), questionIndex))
                    .options(buildQuizOptions(questionObject.path("options")))
                    .build();
            questions.add(question);
        }
        return questions;
    }

    private List<QuizOption> buildQuizOptions(JsonNode optionsNode) {
        if (optionsNode == null || !optionsNode.isArray()) {
            return List.of();
        }

        List<QuizOption> options = new ArrayList<>();
        for (int optionIndex = 0; optionIndex < optionsNode.size(); optionIndex++) {
            JsonNode optionNode = optionsNode.get(optionIndex);
            if (!(optionNode instanceof ObjectNode optionObject)) {
                continue;
            }

            QuizOption option = QuizOption.builder()
                    .optionText(textOrDefault(optionObject.path("text"), "Option " + (optionIndex + 1)))
                    .isCorrect(parseBoolean(optionObject.path("correct"), false))
                    .orderIndex(parseInteger(optionObject.path("orderIndex"), optionIndex))
                    .build();
            options.add(option);
        }
        return options;
    }

    private List<LessonAttachment> buildLessonAttachments(JsonNode attachmentsNode) {
        if (attachmentsNode == null || !attachmentsNode.isArray()) {
            return List.of();
        }
        List<LessonAttachment> attachments = new ArrayList<>();
        for (int attachmentIndex = 0; attachmentIndex < attachmentsNode.size(); attachmentIndex++) {
            JsonNode attachmentNode = attachmentsNode.get(attachmentIndex);
            if (!(attachmentNode instanceof ObjectNode attachmentObject)) {
                continue;
            }
            Media media = resolveMedia(attachmentObject.path("mediaId"));
            String externalUrl = firstNonBlank(
                    textOrNull(attachmentObject.path("url")),
                    textOrNull(attachmentObject.path("externalUrl"))
            );
            LessonAttachment attachment = LessonAttachment.builder()
                    .title(firstNonBlank(
                            textOrNull(attachmentObject.path("name")),
                            textOrNull(attachmentObject.path("title")),
                            "Tài liệu " + (attachmentIndex + 1)
                    ))
                    .description(textOrNull(attachmentObject.path("description")))
                    .media(media)
                    .externalUrl(externalUrl)
                    .type(parseAttachmentType(attachmentObject.path("type"), media != null))
                    .fileSize(parseLongValue(attachmentObject.path("fileSize")))
                    .orderIndex(parseInteger(attachmentObject.path("orderIndex"), attachmentIndex))
                    .createdAt(now())
                    .updatedAt(now())
                    .build();
            attachments.add(attachment);
        }
        attachments.sort(Comparator
                .comparing(LessonAttachment::getOrderIndex, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(LessonAttachment::getTitle, Comparator.nullsLast(String::compareTo)));
        return attachments;
    }

    private Assignment createAssignmentFromSnapshot(ObjectNode itemNode, Module module, int fallbackOrderIndex) {
        Assignment assignment = Assignment.builder()
                .module(module)
                .title(textOrDefault(itemNode.path("title"), "Bài tập"))
                .description(firstNonBlank(
                        textOrNull(itemNode.path("assignmentDescription")),
                        textOrNull(itemNode.path("description")),
                        textOrNull(itemNode.path("contentText"))
                ))
                .submissionType(parseSubmissionType(itemNode.path("assignmentSubmissionType")))
                .maxScore(parseDecimal(itemNode.path("assignmentMaxScore"), new BigDecimal("100")))
                .passingScore(parseDecimal(itemNode.path("assignmentPassingScore"), null))
                .orderIndex(parseInteger(itemNode.path("orderIndex"), fallbackOrderIndex))
                .isRequired(parseBoolean(itemNode.path("isRequired"), true))
                .createdAt(now())
                .updatedAt(now())
                .criteria(buildAssignmentCriteria(resolveAssignmentCriteriaNode(itemNode)))
                .build();

        if (assignment.getCriteria() != null) {
            for (AssignmentCriteria criteria : assignment.getCriteria()) {
                criteria.setAssignment(assignment);
            }
        }

        return assignmentRepository.save(assignment);
    }

    private List<AssignmentCriteria> buildAssignmentCriteria(JsonNode criteriaNode) {
        if (criteriaNode == null || !criteriaNode.isArray()) {
            return List.of();
        }

        List<AssignmentCriteria> criteria = new ArrayList<>();
        for (int criteriaIndex = 0; criteriaIndex < criteriaNode.size(); criteriaIndex++) {
            JsonNode criteriaItem = criteriaNode.get(criteriaIndex);
            if (!(criteriaItem instanceof ObjectNode criteriaObject)) {
                continue;
            }

                BigDecimal snapshotMaxPoints = parseDecimal(criteriaObject.path("maxPoints"), BigDecimal.ZERO);
                BigDecimal snapshotPassingPoints = resolveCriteriaPassingPointsFromSnapshot(
                    criteriaObject,
                    snapshotMaxPoints,
                    BigDecimal.ZERO
                );

            AssignmentCriteria assignmentCriteria = AssignmentCriteria.builder()
                    .name(textOrDefault(criteriaObject.path("name"), "Tiêu chí " + (criteriaIndex + 1)))
                    .description(textOrNull(criteriaObject.path("description")))
                    .maxPoints(snapshotMaxPoints)
                    .passingPoints(snapshotPassingPoints)
                    .orderIndex(parseInteger(criteriaObject.path("orderIndex"), criteriaIndex))
                    .isRequired(parseBoolean(criteriaObject.path("isRequired"), false))
                    .build();
            criteria.add(assignmentCriteria);
        }
        return criteria;
    }

    private JsonNode resolveAssignmentCriteriaNode(ObjectNode itemNode) {
        if (itemNode == null) {
            return MissingNode.getInstance();
        }
        JsonNode assignmentCriteriaNode = itemNode.path("assignmentCriteria");
        if (assignmentCriteriaNode.isArray()) {
            return assignmentCriteriaNode;
        }
        JsonNode legacyCriteriaNode = itemNode.path("criteria");
        if (legacyCriteriaNode.isArray()) {
            return legacyCriteriaNode;
        }
        return MissingNode.getInstance();
    }

    private boolean isLessonEquivalentSnapshot(Lesson existing, ObjectNode itemNode) {
        if (existing == null) {
            return false;
        }
        if (existing.getType() != resolveLessonType(itemNode)) {
            return false;
        }
        if (!Objects.equals(normalizeText(existing.getTitle()), normalizeText(textOrDefault(itemNode.path("title"), "Bài học")))) {
            return false;
        }
        if (!Objects.equals(normalizeText(existing.getContentText()), normalizeText(textOrNull(itemNode.path("contentText"))))) {
            return false;
        }
        if (!Objects.equals(normalizeText(existing.getResourceUrl()), normalizeText(textOrNull(itemNode.path("resourceUrl"))))) {
            return false;
        }
        String snapshotVideoUrl = firstNonBlank(
                textOrNull(itemNode.path("youtubeUrl")),
                textOrNull(itemNode.path("videoUrl"))
        );
        if (!Objects.equals(normalizeText(existing.getVideoUrl()), normalizeText(snapshotVideoUrl))) {
            return false;
        }
        Long existingVideoMediaId = existing.getVideoMedia() != null ? existing.getVideoMedia().getId() : null;
        Long snapshotVideoMediaId = parsePositiveId(itemNode.path("videoMediaId"));
        if (!Objects.equals(existingVideoMediaId, snapshotVideoMediaId)) {
            return false;
        }
        if (!Objects.equals(existing.getDurationSec(), resolveDurationSec(itemNode))) {
            return false;
        }
        return areLessonAttachmentsEquivalent(existing.getAttachments(), itemNode.path("attachments"));
    }

    private boolean areLessonAttachmentsEquivalent(List<LessonAttachment> existingAttachments, JsonNode attachmentsNode) {
        List<LessonAttachment> existing = existingAttachments == null ? List.of() : new ArrayList<>(existingAttachments);
        existing.sort(Comparator
                .comparing(LessonAttachment::getOrderIndex, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(LessonAttachment::getId, Comparator.nullsLast(Long::compareTo)));

        List<ObjectNode> snapshotAttachments = collectObjectNodes(attachmentsNode);
        if (existing.size() != snapshotAttachments.size()) {
            return false;
        }

        for (int index = 0; index < snapshotAttachments.size(); index++) {
            LessonAttachment existingAttachment = existing.get(index);
            ObjectNode snapshotAttachment = snapshotAttachments.get(index);

            String snapshotTitle = firstNonBlank(
                    textOrNull(snapshotAttachment.path("name")),
                    textOrNull(snapshotAttachment.path("title")),
                    "Tài liệu " + (index + 1)
            );
            if (!Objects.equals(normalizeText(existingAttachment.getTitle()), normalizeText(snapshotTitle))) {
                return false;
            }
            if (!Objects.equals(
                    normalizeText(existingAttachment.getDescription()),
                    normalizeText(textOrNull(snapshotAttachment.path("description")))
            )) {
                return false;
            }
            String snapshotExternalUrl = firstNonBlank(
                    textOrNull(snapshotAttachment.path("url")),
                    textOrNull(snapshotAttachment.path("externalUrl"))
            );
            if (!Objects.equals(
                    normalizeText(existingAttachment.getExternalUrl()),
                    normalizeText(snapshotExternalUrl)
            )) {
                return false;
            }
            Long existingMediaId = existingAttachment.getMedia() != null ? existingAttachment.getMedia().getId() : null;
            Long snapshotMediaId = parsePositiveId(snapshotAttachment.path("mediaId"));
            if (!Objects.equals(existingMediaId, snapshotMediaId)) {
                return false;
            }
            AttachmentType snapshotType = parseAttachmentType(snapshotAttachment.path("type"), snapshotMediaId != null);
            if (existingAttachment.getType() != snapshotType) {
                return false;
            }
            Integer snapshotOrderIndex = parseInteger(snapshotAttachment.path("orderIndex"), index);
            if (!Objects.equals(existingAttachment.getOrderIndex(), snapshotOrderIndex)) {
                return false;
            }
        }
        return true;
    }

    private boolean isQuizEquivalentSnapshot(Quiz existing, ObjectNode itemNode) {
        if (existing == null) {
            return false;
        }
        if (!Objects.equals(normalizeText(existing.getTitle()), normalizeText(textOrDefault(itemNode.path("title"), "Quiz")))) {
            return false;
        }
        String snapshotDescription = hasAnyField(itemNode, "quizDescription", "description", "contentText")
                ? firstNonBlank(
                        textOrNull(itemNode.path("quizDescription")),
                        textOrNull(itemNode.path("description")),
                        textOrNull(itemNode.path("contentText"))
                )
                : existing.getDescription();
        if (!Objects.equals(normalizeText(existing.getDescription()), normalizeText(snapshotDescription))) {
            return false;
        }
        Integer snapshotPassScore = hasExplicitField(itemNode, "passScore")
            ? parseExplicitIntegerField(itemNode, "passScore", 80)
                : existing.getPassScore();
        if (!Objects.equals(existing.getPassScore(), snapshotPassScore)) {
            return false;
        }
        Integer snapshotMaxAttempts = hasExplicitField(itemNode, "quizMaxAttempts")
            ? parseInteger(itemNode.path("quizMaxAttempts"), existing.getMaxAttempts())
                : existing.getMaxAttempts();
        if (!Objects.equals(existing.getMaxAttempts(), snapshotMaxAttempts)) {
            return false;
        }
        Integer snapshotTimeLimit = hasExplicitField(itemNode, "quizTimeLimitMinutes")
            ? parseInteger(itemNode.path("quizTimeLimitMinutes"), existing.getTimeLimitMinutes())
                : existing.getTimeLimitMinutes();
        if (!Objects.equals(existing.getTimeLimitMinutes(), snapshotTimeLimit)) {
            return false;
        }
        Integer snapshotRoundingIncrement = hasExplicitField(itemNode, "roundingIncrement")
            ? parseInteger(itemNode.path("roundingIncrement"), existing.getRoundingIncrement())
                : existing.getRoundingIncrement();
        if (!Objects.equals(existing.getRoundingIncrement(), snapshotRoundingIncrement)) {
            return false;
        }
        QuizGradingMethod snapshotGradingMethod = hasExplicitField(itemNode, "gradingMethod")
            ? parseQuizGradingMethodAllowNullAsExisting(itemNode.path("gradingMethod"), existing.getGradingMethod())
                : existing.getGradingMethod();
        if (!Objects.equals(existing.getGradingMethod(), snapshotGradingMethod)) {
            return false;
        }
        Integer snapshotCooldownHours = hasExplicitField(itemNode, "cooldownHours")
            ? parseInteger(itemNode.path("cooldownHours"), existing.getCooldownHours())
                : existing.getCooldownHours();
        if (!Objects.equals(existing.getCooldownHours(), snapshotCooldownHours)) {
            return false;
        }
        if (!hasExplicitField(itemNode, "questions")) {
            return true;
        }
        return areQuizQuestionListsEquivalent(existing.getQuestions(), itemNode.path("questions"));
    }

    private boolean areQuizQuestionListsEquivalent(List<QuizQuestion> existingQuestions, JsonNode questionsNode) {
        List<QuizQuestion> existing = existingQuestions == null ? List.of() : new ArrayList<>(existingQuestions);
        existing.sort(Comparator
                .comparing(QuizQuestion::getOrderIndex, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(QuizQuestion::getId, Comparator.nullsLast(Long::compareTo)));

        List<ObjectNode> snapshotQuestions = collectObjectNodes(questionsNode);
        if (existing.size() != snapshotQuestions.size()) {
            return false;
        }

        for (int questionIndex = 0; questionIndex < snapshotQuestions.size(); questionIndex++) {
            QuizQuestion existingQuestion = existing.get(questionIndex);
            ObjectNode snapshotQuestion = snapshotQuestions.get(questionIndex);
            String snapshotText = textOrDefault(snapshotQuestion.path("text"), "Question " + (questionIndex + 1));
            if (!Objects.equals(normalizeText(existingQuestion.getQuestionText()), normalizeText(snapshotText))) {
                return false;
            }
            if (!Objects.equals(existingQuestion.getQuestionType(), parseQuestionType(snapshotQuestion.path("type")))) {
                return false;
            }
            if (!Objects.equals(existingQuestion.getScore(), parseInteger(snapshotQuestion.path("score"), 1))) {
                return false;
            }
            if (!Objects.equals(existingQuestion.getOrderIndex(), parseInteger(snapshotQuestion.path("orderIndex"), questionIndex))) {
                return false;
            }
            if (!areQuizOptionListsEquivalent(existingQuestion.getOptions(), snapshotQuestion.path("options"))) {
                return false;
            }
        }
        return true;
    }

    private boolean areQuizOptionListsEquivalent(List<QuizOption> existingOptions, JsonNode optionsNode) {
        List<QuizOption> existing = existingOptions == null ? List.of() : new ArrayList<>(existingOptions);
        existing.sort(Comparator
                .comparing(QuizOption::getOrderIndex, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(QuizOption::getId, Comparator.nullsLast(Long::compareTo)));

        List<ObjectNode> snapshotOptions = collectObjectNodes(optionsNode);
        if (existing.size() != snapshotOptions.size()) {
            return false;
        }

        for (int optionIndex = 0; optionIndex < snapshotOptions.size(); optionIndex++) {
            QuizOption existingOption = existing.get(optionIndex);
            ObjectNode snapshotOption = snapshotOptions.get(optionIndex);
            String snapshotText = textOrDefault(snapshotOption.path("text"), "Option " + (optionIndex + 1));
            if (!Objects.equals(normalizeText(existingOption.getOptionText()), normalizeText(snapshotText))) {
                return false;
            }
            if (!Objects.equals(existingOption.getIsCorrect(), parseBoolean(snapshotOption.path("correct"), false))) {
                return false;
            }
            if (!Objects.equals(existingOption.getOrderIndex(), parseInteger(snapshotOption.path("orderIndex"), optionIndex))) {
                return false;
            }
        }
        return true;
    }

    private boolean isAssignmentEquivalentSnapshot(Assignment existing, ObjectNode itemNode) {
        if (existing == null) {
            return false;
        }
        if (!Objects.equals(normalizeText(existing.getTitle()), normalizeText(textOrDefault(itemNode.path("title"), "Bài tập")))) {
            return false;
        }
        String snapshotDescription = hasAnyField(itemNode, "assignmentDescription", "description", "contentText")
                ? firstNonBlank(
                        textOrNull(itemNode.path("assignmentDescription")),
                        textOrNull(itemNode.path("description")),
                        textOrNull(itemNode.path("contentText"))
                )
                : existing.getDescription();
        if (!Objects.equals(normalizeText(existing.getDescription()), normalizeText(snapshotDescription))) {
            return false;
        }
        SubmissionType snapshotSubmissionType = hasAnyField(itemNode, "assignmentSubmissionType", "submissionType")
            ? parseSubmissionTypeAllowNullAsExisting(
                firstPresentNode(itemNode, "assignmentSubmissionType", "submissionType"),
                existing.getSubmissionType()
            )
                : existing.getSubmissionType();
        if (!Objects.equals(existing.getSubmissionType(), snapshotSubmissionType)) {
            return false;
        }
        BigDecimal snapshotMaxScore = hasAnyField(itemNode, "assignmentMaxScore", "maxScore")
            ? parseDecimal(firstPresentNode(itemNode, "assignmentMaxScore", "maxScore"), existing.getMaxScore())
                : existing.getMaxScore();
        if (!Objects.equals(normalizeMoney(existing.getMaxScore()), normalizeMoney(snapshotMaxScore))) {
            return false;
        }
        BigDecimal snapshotPassingScore = hasAnyField(itemNode, "assignmentPassingScore", "passingScore")
            ? parseDecimalAllowExplicitNull(firstPresentNode(itemNode, "assignmentPassingScore", "passingScore"), null)
                : existing.getPassingScore();
        if (!Objects.equals(normalizeMoney(existing.getPassingScore()), normalizeMoney(snapshotPassingScore))) {
            return false;
        }
        Boolean snapshotIsRequired = hasAnyField(itemNode, "isRequired", "required")
            ? parseBoolean(firstPresentNode(itemNode, "isRequired", "required"), existing.getIsRequired())
                : existing.getIsRequired();
        if (!Objects.equals(existing.getIsRequired(), snapshotIsRequired)) {
            return false;
        }
        if (!hasAnyField(itemNode, "assignmentCriteria", "criteria")) {
            return true;
        }
        return areAssignmentCriteriaEquivalent(existing.getCriteria(), resolveAssignmentCriteriaNode(itemNode));
    }

    private boolean hasExplicitField(ObjectNode node, String fieldName) {
        if (node == null || fieldName == null || fieldName.isBlank()) {
            return false;
        }
        if (!node.has(fieldName)) {
            return false;
        }
        JsonNode fieldNode = node.get(fieldName);
        return fieldNode != null && !fieldNode.isMissingNode();
    }

    private boolean hasNonNullField(ObjectNode node, String fieldName) {
        if (!hasExplicitField(node, fieldName)) {
            return false;
        }
        JsonNode fieldNode = node.get(fieldName);
        return fieldNode != null && !fieldNode.isNull();
    }

    private boolean hasAnyField(ObjectNode node, String... fieldNames) {
        if (fieldNames == null || fieldNames.length == 0) {
            return false;
        }
        for (String fieldName : fieldNames) {
            if (hasExplicitField(node, fieldName)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAnyNonNullField(ObjectNode node, String... fieldNames) {
        if (fieldNames == null || fieldNames.length == 0) {
            return false;
        }
        for (String fieldName : fieldNames) {
            if (hasNonNullField(node, fieldName)) {
                return true;
            }
        }
        return false;
    }

    private JsonNode firstPresentNonNullNode(ObjectNode node, String... fieldNames) {
        if (node == null || fieldNames == null || fieldNames.length == 0) {
            return MissingNode.getInstance();
        }
        for (String fieldName : fieldNames) {
            if (hasNonNullField(node, fieldName)) {
                return node.get(fieldName);
            }
        }
        return MissingNode.getInstance();
    }

    private JsonNode firstPresentNode(ObjectNode node, String... fieldNames) {
        if (node == null || fieldNames == null || fieldNames.length == 0) {
            return MissingNode.getInstance();
        }
        for (String fieldName : fieldNames) {
            if (hasExplicitField(node, fieldName)) {
                return node.get(fieldName);
            }
        }
        return MissingNode.getInstance();
    }

    private boolean areAssignmentCriteriaEquivalent(List<AssignmentCriteria> existingCriteria, JsonNode criteriaNode) {
        List<AssignmentCriteria> existing = existingCriteria == null ? List.of() : new ArrayList<>(existingCriteria);
        existing.sort(Comparator
                .comparing(AssignmentCriteria::getOrderIndex, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(AssignmentCriteria::getId, Comparator.nullsLast(Long::compareTo)));

        List<ObjectNode> snapshotCriteria = collectObjectNodes(criteriaNode);
        if (existing.size() != snapshotCriteria.size()) {
            return false;
        }

        for (int criteriaIndex = 0; criteriaIndex < snapshotCriteria.size(); criteriaIndex++) {
            AssignmentCriteria existingItem = existing.get(criteriaIndex);
            ObjectNode snapshotItem = snapshotCriteria.get(criteriaIndex);

            String snapshotName = hasExplicitField(snapshotItem, "name")
                    ? textOrDefaultAllowExplicitNull(snapshotItem.path("name"), "Tiêu chí " + (criteriaIndex + 1))
                    : existingItem.getName();
            if (!Objects.equals(normalizeText(existingItem.getName()), normalizeText(snapshotName))) {
                return false;
            }
            String snapshotDescription = hasExplicitField(snapshotItem, "description")
                    ? textOrNull(snapshotItem.path("description"))
                    : existingItem.getDescription();
            if (!Objects.equals(
                    normalizeText(existingItem.getDescription()),
                    normalizeText(snapshotDescription)
            )) {
                return false;
            }
            BigDecimal snapshotMaxPoints = hasExplicitField(snapshotItem, "maxPoints")
                    ? parseDecimalAllowExplicitNull(snapshotItem.path("maxPoints"), BigDecimal.ZERO)
                    : existingItem.getMaxPoints();
            if (!Objects.equals(
                    normalizeMoney(existingItem.getMaxPoints()),
                    normalizeMoney(snapshotMaxPoints)
            )) {
                return false;
            }
            Integer snapshotOrderIndex = hasExplicitField(snapshotItem, "orderIndex")
                    ? parseInteger(snapshotItem.path("orderIndex"), existingItem.getOrderIndex())
                    : existingItem.getOrderIndex();
            if (!Objects.equals(existingItem.getOrderIndex(), snapshotOrderIndex)) {
                return false;
            }
            Boolean snapshotIsRequired = hasExplicitField(snapshotItem, "isRequired")
                    ? parseBoolean(snapshotItem.path("isRequired"), existingItem.isRequired())
                    : existingItem.isRequired();
            if (!Objects.equals(Boolean.valueOf(existingItem.isRequired()), snapshotIsRequired)) {
                return false;
            }
            BigDecimal snapshotPassingPoints = hasExplicitField(snapshotItem, "passingPoints")
                    ? resolveCriteriaPassingPointsFromSnapshot(
                            snapshotItem,
                            snapshotMaxPoints,
                            existingItem.getPassingPoints()
                    )
                    : existingItem.getPassingPoints();
            if (!Objects.equals(
                    normalizeMoney(existingItem.getPassingPoints()),
                    normalizeMoney(snapshotPassingPoints)
            )) {
                return false;
            }
        }
        return true;
    }

    private BigDecimal resolveCriteriaPassingPointsFromSnapshot(
            ObjectNode criteriaNode,
            BigDecimal resolvedMaxPoints,
            BigDecimal fallback
    ) {
        if (criteriaNode == null) {
            return fallback;
        }

        if (hasExplicitField(criteriaNode, "passingPoints")) {
            BigDecimal explicitPassingPoints = parseDecimalAllowExplicitNull(
                    criteriaNode.path("passingPoints"),
                    null
            );
            if (explicitPassingPoints != null) {
                return explicitPassingPoints;
            }
        }

        if (resolvedMaxPoints != null) {
            return resolvedMaxPoints;
        }

        BigDecimal maxFromNode = parseDecimalAllowExplicitNull(criteriaNode.path("maxPoints"), null);
        if (maxFromNode != null) {
            return maxFromNode;
        }

        return fallback;
    }

    private Integer parseExplicitIntegerField(ObjectNode node, String fieldName, Integer fallback) {
        if (node == null || !hasExplicitField(node, fieldName)) {
            return fallback;
        }
        return parseExplicitIntegerNode(node.get(fieldName), fallback);
    }

    private Integer parseExplicitIntegerNode(JsonNode node, Integer fallback) {
        if (node == null || node.isMissingNode()) {
            return fallback;
        }
        if (node.isNull()) {
            return null;
        }
        return parseInteger(node, fallback);
    }

    private Boolean parseExplicitBooleanField(ObjectNode node, String fieldName, Boolean fallback) {
        if (node == null || !hasExplicitField(node, fieldName)) {
            return fallback;
        }
        return parseBooleanAllowExplicitNull(node.get(fieldName), fallback);
    }

    private Boolean parseBooleanAllowExplicitNull(JsonNode node, Boolean fallback) {
        if (node == null || node.isMissingNode()) {
            return fallback;
        }
        if (node.isNull()) {
            return null;
        }
        return parseBoolean(node, fallback);
    }

    private QuizGradingMethod parseExplicitQuizGradingMethodField(ObjectNode node, String fieldName) {
        if (node == null || !hasExplicitField(node, fieldName)) {
            return null;
        }
        JsonNode valueNode = node.get(fieldName);
        if (valueNode == null || valueNode.isNull() || valueNode.isMissingNode()) {
            return null;
        }
        return parseQuizGradingMethod(valueNode);
    }

    private QuizGradingMethod parseQuizGradingMethodAllowNullAsExisting(
            JsonNode node,
            QuizGradingMethod existingValue
    ) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return existingValue;
        }
        return parseQuizGradingMethod(node);
    }

    private BigDecimal parseDecimalAllowExplicitNull(JsonNode node, BigDecimal fallback) {
        if (node == null || node.isMissingNode()) {
            return fallback;
        }
        if (node.isNull()) {
            return null;
        }
        return parseDecimal(node, fallback);
    }

    private SubmissionType parseSubmissionTypeAllowExplicitNull(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return SubmissionType.TEXT;
        }
        if (node.isNull()) {
            return null;
        }
        return parseSubmissionType(node);
    }

    private SubmissionType parseSubmissionTypeAllowNullAsExisting(
            JsonNode node,
            SubmissionType existingValue
    ) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return existingValue;
        }
        return parseSubmissionType(node);
    }

    private String textOrDefaultAllowExplicitNull(JsonNode node, String fallback) {
        if (node != null && node.isNull()) {
            return null;
        }
        return textOrDefault(node, fallback);
    }

    private List<ObjectNode> collectObjectNodes(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<ObjectNode> objects = new ArrayList<>();
        for (int index = 0; index < node.size(); index++) {
            JsonNode child = node.get(index);
            if (child instanceof ObjectNode objectNode) {
                objects.add(objectNode);
            }
        }
        return objects;
    }

    private Long parseExplicitPositiveId(ObjectNode node) {
        if (!node.has("id") || node.get("id") == null || node.get("id").isNull()) {
            return null;
        }
        return parsePositiveId(node.get("id"));
    }

    private Long parseLongValue(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.canConvertToLong()) {
            return node.longValue();
        }
        if (!node.isTextual()) {
            return null;
        }
        String raw = node.asText().trim();
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private AttachmentType parseAttachmentType(JsonNode typeNode, boolean hasMedia) {
        String raw = textOrNull(typeNode);
        if (raw != null) {
            try {
                return AttachmentType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // Default inference below.
            }
        }
        return hasMedia ? AttachmentType.PDF : AttachmentType.EXTERNAL_LINK;
    }

    private Long extractCourseId(Module module) {
        if (module == null || module.getCourse() == null) {
            return null;
        }
        return module.getCourse().getId();
    }

    private String normalizeSnapshotItemType(JsonNode node) {
        String type = textOrNull(node.path("type"));
        if (type == null) {
            type = textOrNull(node.path("itemType"));
        }
        if (type == null) {
            type = textOrNull(node.path("lessonType"));
        }
        if (type == null) {
            return "lesson";
        }
        String normalized = type.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? "lesson" : normalized;
    }

    private LessonType resolveLessonType(JsonNode node) {
        String itemType = normalizeSnapshotItemType(node);
        return switch (itemType) {
            case "video" -> LessonType.VIDEO;
            case "codelab" -> LessonType.CODELAB;
            default -> LessonType.READING;
        };
    }

    private Integer resolveDurationSec(JsonNode node) {
        Integer durationSec = parseInteger(node.path("durationSec"), null);
        if (durationSec != null) {
            return durationSec;
        }
        Integer durationMin = parseInteger(node.path("durationMin"), null);
        return durationMin == null ? null : durationMin * 60;
    }

    private SubmissionType parseSubmissionType(JsonNode node) {
        String raw = textOrNull(node);
        if (raw == null) {
            return SubmissionType.TEXT;
        }
        try {
            return SubmissionType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return SubmissionType.TEXT;
        }
    }

    private QuizGradingMethod parseQuizGradingMethod(JsonNode node) {
        String raw = textOrNull(node);
        if (raw == null) {
            return null;
        }
        try {
            return QuizGradingMethod.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private QuestionType parseQuestionType(JsonNode node) {
        String raw = textOrNull(node);
        if (raw == null) {
            return QuestionType.MULTIPLE_CHOICE;
        }
        try {
            return QuestionType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return QuestionType.MULTIPLE_CHOICE;
        }
    }

    private Media resolveMedia(JsonNode mediaIdNode) {
        Long mediaId = parsePositiveId(mediaIdNode);
        if (mediaId == null) {
            return null;
        }
        return mediaRepository.findById(mediaId)
                .orElseThrow(() -> new BadRequestException("COURSE_REVISION_CONTENT_MEDIA_NOT_FOUND"));
    }

    private Integer parseInteger(JsonNode node, Integer fallback) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return fallback;
        }
        if (node.canConvertToInt()) {
            return node.asInt();
        }
        if (!node.isTextual()) {
            return fallback;
        }
        String raw = node.asText().trim();
        if (raw.isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private BigDecimal parseDecimal(JsonNode node, BigDecimal fallback) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return fallback;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        if (!node.isTextual()) {
            return fallback;
        }
        String raw = node.asText().trim();
        if (raw.isEmpty()) {
            return fallback;
        }
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private Boolean parseBoolean(JsonNode node, Boolean fallback) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return fallback;
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (!node.isTextual()) {
            return fallback;
        }
        String raw = node.asText().trim();
        if (raw.isEmpty()) {
            return fallback;
        }
        if ("true".equalsIgnoreCase(raw)) {
            return true;
        }
        if ("false".equalsIgnoreCase(raw)) {
            return false;
        }
        return fallback;
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        String value = node.asText();
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String textOrDefault(JsonNode node, String fallback) {
        String value = textOrNull(node);
        return value == null ? fallback : value;
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

    private void ensureSnapshotIdentityContract(JsonNode contentSnapshot, Long courseId, Long revisionId) {
        JsonNode snapshot = defaultContentSnapshot(contentSnapshot);
        JsonNode modulesNode = snapshot.path("modules");
        if (!modulesNode.isArray()) {
            return;
        }

        for (int moduleIndex = 0; moduleIndex < modulesNode.size(); moduleIndex++) {
            JsonNode moduleNode = modulesNode.get(moduleIndex);
            if (!(moduleNode instanceof ObjectNode)) {
                continue;
            }

            Long moduleId = parsePositiveId(moduleNode.path("id"));
            if (moduleId == null) {
                throwInvalidSnapshotIdentity(courseId, revisionId, "modules[" + moduleIndex + "].id");
            }

            JsonNode itemsNode = moduleNode.path("lessons");
            if (!itemsNode.isArray()) {
                continue;
            }

            for (int itemIndex = 0; itemIndex < itemsNode.size(); itemIndex++) {
                JsonNode itemNode = itemsNode.get(itemIndex);
                if (!(itemNode instanceof ObjectNode)) {
                    continue;
                }

                Long itemId = parsePositiveId(itemNode.path("id"));
                if (itemId == null) {
                    throwInvalidSnapshotIdentity(
                            courseId,
                            revisionId,
                            "modules[" + moduleIndex + "].lessons[" + itemIndex + "].id"
                    );
                }
            }
        }
    }

    private void throwInvalidSnapshotIdentity(Long courseId, Long revisionId, String path) {
        log.warn(
                "course_revision_event action=snapshot_identity_invalid courseId={} revisionId={} reasonCode={} path={}",
                courseId,
                revisionId,
                "COURSE_REVISION_CONTENT_ID_REQUIRED",
                path
        );
        throw new BadRequestException("COURSE_REVISION_CONTENT_ID_REQUIRED: " + path);
    }

    private Long parsePositiveId(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }

        if (node.canConvertToLong()) {
            long value = node.asLong();
            return value > 0 ? value : null;
        }

        if (!node.isTextual()) {
            return null;
        }

        String raw = node.asText().trim();
        if (raw.isEmpty()) {
            return null;
        }

        try {
            long parsed = Long.parseLong(raw);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private JsonNode parseJsonSafely(String json) {
        try {
            JsonNode parsed = objectMapper.readTree(json);
            return parsed != null ? parsed : JsonNodeFactory.instance.objectNode();
        } catch (JsonProcessingException ex) {
            log.warn("Failed to parse JSON '{}': {}", json, ex.getMessage());
            return JsonNodeFactory.instance.objectNode();
        }
    }

    private String toJsonText(JsonNode jsonNode, String fallback) {
        return jsonNode == null ? fallback : jsonNode.toString();
    }

    private void ensureRevisionWriteEnabled() {
        if (!courseRevisionFeatureProperties.isWriteEnabled()) {
            throw new ConflictException("COURSE_REVISION_WRITE_DISABLED");
        }
    }

    private void ensureRevisionApprovalEnabled() {
        if (!courseRevisionFeatureProperties.isApprovalEnabled()) {
            throw new ConflictException("COURSE_REVISION_APPROVAL_DISABLED");
        }
    }

    private void ensureAuthorOrAdmin(Long actorId, Long authorId) {
        if (actorId != null && actorId.equals(authorId)) {
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN") || a.equals("ROLE_CONTENT_ADMIN"))) {
            log.debug("Actor {} allowed via admin role", actorId);
            return;
        }

        if (auth == null) {
            log.warn("Course revision access denied: no authentication context (actorId={}, authorId={})",
                    actorId, authorId);
        } else {
            log.warn("Course revision access denied: actorId={}, authorId={}, authorities={}",
                    actorId,
                    authorId,
                    auth.getAuthorities().stream()
                            .map(GrantedAuthority::getAuthority)
                            .sorted()
                            .toList());
        }

        throw new AccessDeniedException("FORBIDDEN");
    }

    private Instant now() {
        return Instant.now(clock);
    }
}
