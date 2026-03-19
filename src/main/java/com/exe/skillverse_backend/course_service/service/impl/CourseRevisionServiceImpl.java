package com.exe.skillverse_backend.course_service.service.impl;

import com.exe.skillverse_backend.course_service.dto.coursedto.CourseRevisionDTO;
import com.exe.skillverse_backend.course_service.dto.coursedto.CourseRevisionUpdateDTO;
import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.course_service.entity.CourseRevision;
import com.exe.skillverse_backend.course_service.entity.enums.CourseRevisionStatus;
import com.exe.skillverse_backend.course_service.entity.enums.CourseStatus;
import com.exe.skillverse_backend.course_service.policy.CourseRevisionFeatureProperties;
import com.exe.skillverse_backend.course_service.repository.CourseRepository;
import com.exe.skillverse_backend.course_service.repository.CourseRevisionRepository;
import com.exe.skillverse_backend.course_service.service.CourseRevisionService;
import com.exe.skillverse_backend.shared.dto.PageResponse;
import com.exe.skillverse_backend.shared.exception.AccessDeniedException;
import com.exe.skillverse_backend.shared.exception.BadRequestException;
import com.exe.skillverse_backend.shared.exception.ConflictException;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

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
    private final CourseRevisionFeatureProperties courseRevisionFeatureProperties;
    private final CourseAutoCompatibleUpgradeExecutor autoCompatibleUpgradeExecutor;
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

        boolean hasMeaningfulChanges = hasMeaningfulChangesComparedToBaseline(revision, course);
        boolean noChangesSinceLastReject = revision.getRejectedSnapshotHash() != null
                && !revision.getRejectedSnapshotHash().isBlank()
                && Objects.equals(revision.getSnapshotHash(), revision.getRejectedSnapshotHash());

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

        boolean wasRejected = revision.getStatus() == CourseRevisionStatus.REJECTED;
        revision.setStatus(CourseRevisionStatus.PENDING);
        revision.setSubmittedAt(now());
        revision.setUpdatedAt(now());
        revision.setRejectedAt(null);
        revision.setRejectionReason(null);
        revision.setSnapshotHash(computeRevisionSnapshotHash(revision));
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
    public CourseRevisionDTO updateRevision(Long revisionId, CourseRevisionUpdateDTO dto, Long actorId) {
        ensureRevisionWriteEnabled();

        CourseRevision revision = getRevisionOrThrow(revisionId);
        Course course = revision.getCourse();

        ensureAuthorOrAdmin(actorId, course.getAuthor().getId());

        if (revision.getStatus() != CourseRevisionStatus.DRAFT
                && revision.getStatus() != CourseRevisionStatus.REJECTED) {
            throw new ConflictException("COURSE_REVISION_NOT_EDITABLE_IN_STATUS_" + revision.getStatus());
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
            revision.setLearningObjectivesJson(writeJsonSafely(dto.getLearningObjectives(), "[]"));
        }
        if (dto.getRequirements() != null) {
            revision.setRequirementsJson(writeJsonSafely(dto.getRequirements(), "[]"));
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

        Long courseId = revision.getCourse().getId();
        Course course = courseRepository.findByIdForRevisionApproval(courseId)
                .orElseThrow(() -> new NotFoundException("COURSE_NOT_FOUND"));
        Long previousActiveRevisionId = course.getActiveRevisionId();

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
        courseRepository.save(course);

        CourseAutoCompatibleUpgradeExecutor.AutoUpgradeExecutionResult autoUpgradeResult =
                autoCompatibleUpgradeExecutor.executeAfterRevisionApproval(course, previousActiveRevisionId, saved);
        registerPostCommitAutoUpgradeReconciliation(course.getId(), previousActiveRevisionId, saved.getId());

        logRevisionEvent(
                "approve",
                course.getId(),
                saved.getId(),
                saved.getSourceRevisionId(),
                adminId,
                autoUpgradeResult.getReasonCode()
        );
        CourseRevisionDTO dto = toRevisionDto(saved);
        dto.setAutoUpgradeOutcome(autoUpgradeResult.getOutcome());
        dto.setAutoUpgradeAffectedEnrollments(autoUpgradeResult.getUpgradedCount());
        dto.setAutoUpgradeReasonCode(autoUpgradeResult.getReasonCode());
        dto.setAutoUpgradeReasonDetail(autoUpgradeResult.getReasonDetail());
        return dto;
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
                    .contentSnapshotJson(defaultContentSnapshot(source.getContentSnapshotJson()))
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
                .contentSnapshotJson(defaultContentSnapshot(null))
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
                    defaultJsonObject(revision.getContentSnapshotJson())
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
                    parseJsonSafely("{}")
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
            java.math.BigDecimal price,
            String currency,
            JsonNode learningObjectives,
            JsonNode requirements,
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
        root.set("contentSnapshot", canonicalizeJsonNode(contentSnapshot));
        return root;
    }

    private String normalizeText(String value) {
        if (value == null) return null;
        String collapsed = value.replaceAll("\\s+", " ").trim();
        return collapsed.isEmpty() ? null : collapsed;
    }

    private String normalizeMoney(java.math.BigDecimal money) {
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
        ArrayNode canonical = objectMapper.createArrayNode();
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
            return objectMapper.nullNode();
        }

        if (source.isObject()) {
            ObjectNode canonical = objectMapper.createObjectNode();
            List<String> fieldNames = new ArrayList<>();
            source.fieldNames().forEachRemaining(fieldNames::add);
            Collections.sort(fieldNames);
            for (String fieldName : fieldNames) {
                canonical.set(fieldName, canonicalizeJsonNode(source.get(fieldName)));
            }
            return canonical;
        }

        if (source.isArray()) {
            ArrayNode canonical = objectMapper.createArrayNode();
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
                .contentSnapshotJson(toJsonText(revision.getContentSnapshotJson(), "{}"))
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
            return objectNode;
        }
        if (!versionNode.canConvertToInt()) {
            throw new BadRequestException("COURSE_REVISION_UNSUPPORTED_SNAPSHOT_VERSION");
        }
        int snapshotVersion = versionNode.asInt();
        if (snapshotVersion != CONTENT_SNAPSHOT_VERSION_V1) {
            throw new BadRequestException("COURSE_REVISION_UNSUPPORTED_SNAPSHOT_VERSION");
        }
        return objectNode;
    }

    private JsonNode parseJsonSafely(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to parse JSON '{}': {}", json, ex.getMessage());
            return objectMapper.createObjectNode();
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

    private void registerPostCommitAutoUpgradeReconciliation(
            Long courseId,
            Long sourceRevisionId,
            Long targetRevisionId
    ) {
        if (courseId == null || sourceRevisionId == null || targetRevisionId == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                autoCompatibleUpgradeExecutor.executePostCommitReconciliation(
                        courseId,
                        sourceRevisionId,
                        targetRevisionId
                );
            }
        });
    }
}
