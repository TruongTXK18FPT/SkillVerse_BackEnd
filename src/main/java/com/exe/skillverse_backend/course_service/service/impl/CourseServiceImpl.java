package com.exe.skillverse_backend.course_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.course_service.dto.coursedto.CourseCreateDTO;
import com.exe.skillverse_backend.course_service.dto.coursedto.CourseDetailDTO;
import com.exe.skillverse_backend.course_service.dto.coursedto.CourseSummaryDTO;
import com.exe.skillverse_backend.course_service.dto.coursedto.CourseUpdateDTO;
import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.course_service.entity.CourseRevision;
import com.exe.skillverse_backend.course_service.entity.CourseSkill;
import com.exe.skillverse_backend.course_service.entity.CourseSkillId;
import com.exe.skillverse_backend.course_service.event.CourseRevisionApprovedEvent;
import com.exe.skillverse_backend.shared.entity.Skill;
import com.exe.skillverse_backend.course_service.entity.enums.CourseRevisionStatus;
import com.exe.skillverse_backend.course_service.entity.enums.CourseStatus;
import com.exe.skillverse_backend.course_service.entity.enums.CourseUpgradePolicy;
import com.exe.skillverse_backend.course_service.entity.enums.EnrollmentStatus;
import com.exe.skillverse_backend.course_service.mapper.CourseMapper;
import com.exe.skillverse_backend.course_service.policy.CourseDeletionPolicy;
import com.exe.skillverse_backend.course_service.policy.CourseRevisionFeatureProperties;
import com.exe.skillverse_backend.course_service.repository.CourseEnrollmentRepository;
import com.exe.skillverse_backend.course_service.repository.CoursePurchaseRepository;
import com.exe.skillverse_backend.course_service.repository.CourseSkillRepository;
import com.exe.skillverse_backend.course_service.repository.CourseRepository;
import com.exe.skillverse_backend.course_service.repository.CourseRevisionRepository;
import com.exe.skillverse_backend.course_service.repository.ModuleRepository;
import com.exe.skillverse_backend.course_service.service.CourseService;
import com.exe.skillverse_backend.course_service.util.CourseRevisionSnapshotAssembler;
import com.exe.skillverse_backend.notification_service.entity.NotificationType;
import com.exe.skillverse_backend.notification_service.service.NotificationService;
import com.exe.skillverse_backend.shared.dto.PageResponse;
import com.exe.skillverse_backend.shared.entity.Media;
import com.exe.skillverse_backend.shared.enums.SkillStatus;
import com.exe.skillverse_backend.shared.util.SkillNameUtils;
import com.exe.skillverse_backend.shared.exception.AccessDeniedException;
import com.exe.skillverse_backend.shared.exception.BadRequestException;
import com.exe.skillverse_backend.shared.exception.ConflictException;
import com.exe.skillverse_backend.shared.exception.MediaOperationException;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import com.exe.skillverse_backend.shared.repository.MediaRepository;
import com.exe.skillverse_backend.shared.repository.SkillRepository;
import com.exe.skillverse_backend.shared.service.CloudinaryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseServiceImpl implements CourseService {

    private final CourseRepository courseRepository;
    private final CourseEnrollmentRepository enrollmentRepository;
    private final CoursePurchaseRepository purchaseRepository;
    private final ModuleRepository moduleRepository;
    private final MediaRepository mediaRepository;
    private final CloudinaryService cloudinaryService;
    private final CourseMapper courseMapper;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final Clock clock;
    private final CourseDeletionPolicy courseDeletionPolicy;
    private final CourseRevisionRepository courseRevisionRepository;
    private final CourseRevisionFeatureProperties courseRevisionFeatureProperties;
    private final SkillRepository skillRepository;
    private final CourseSkillRepository courseSkillRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public CourseDetailDTO createCourse(Long authorId, CourseCreateDTO dto, MultipartFile thumbnailFile) {
        log.info("Creating course with title '{}' by author {}", dto.getTitle(), authorId);

        validateCreateCourseRequest(dto);

        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND"));

        // Handle thumbnail upload if file provided
        Media thumbnail = null;
        if (thumbnailFile != null && !thumbnailFile.isEmpty()) {
            thumbnail = uploadThumbnail(thumbnailFile, authorId);
            dto.setThumbnailMediaId(thumbnail.getId());
        } else if (dto.getThumbnailMediaId() != null) {
            log.info("Loading thumbnail media with ID: {}", dto.getThumbnailMediaId());
            thumbnail = mediaRepository.findByIdWithUser(dto.getThumbnailMediaId());
            if (thumbnail == null) {
                throw new NotFoundException("MEDIA_NOT_FOUND");
            }
            log.info("Loaded thumbnail media: {} - {}", thumbnail.getId(), thumbnail.getUrl());
        }

        Course entity = courseMapper.toEntity(dto, author, thumbnail);
        entity.setStatus(CourseStatus.DRAFT);
        entity.setCreatedAt(now());
        entity.setUpdatedAt(now());

        Course saved = courseRepository.save(entity);
        log.info("Course created with id={} by author={}", saved.getId(), authorId);

        // Sync skill tags → Skill entities (N:N links)
        if (dto.getCourseSkills() != null && !dto.getCourseSkills().isEmpty()) {
            syncCourseSkillLinks(saved.getId(), dto.getCourseSkills());
        }

        return courseMapper.toDetailDto(saved);
    }

    @Override
    @Transactional
    public CourseDetailDTO updateCourse(Long courseId, CourseUpdateDTO dto, Long actorId, MultipartFile thumbnailFile) {
        log.info("Updating course {} by actor {}", courseId, actorId);

        Course course = getCourseOrThrow(courseId);
        ensureAuthorOrAdmin(actorId, course.getAuthor().getId());

        // Policy: allow updates when DRAFT, REJECTED, or SUSPENDED (mentor can edit and resubmit/appeal)
        if (course.getStatus() != CourseStatus.DRAFT
                && course.getStatus() != CourseStatus.REJECTED
                && course.getStatus() != CourseStatus.SUSPENDED) {
            throw new ConflictException("COURSE_NOT_EDITABLE_IN_STATUS_" + course.getStatus());
        }

        validateUpdateCourseRequest(dto);

        // Handle thumbnail upload if file provided
        Media thumbnail = course.getThumbnail(); // Keep existing thumbnail by default
        if (thumbnailFile != null && !thumbnailFile.isEmpty()) {
            thumbnail = uploadThumbnail(thumbnailFile, actorId);
            dto.setThumbnailMediaId(thumbnail.getId());
        } else if (dto.getThumbnailMediaId() != null) {
            log.info("Loading thumbnail media with ID: {}", dto.getThumbnailMediaId());
            thumbnail = mediaRepository.findByIdWithUser(dto.getThumbnailMediaId());
            if (thumbnail == null) {
                throw new NotFoundException("MEDIA_NOT_FOUND");
            }
            log.info("Loaded thumbnail media: {} - {}", thumbnail.getId(), thumbnail.getUrl());
        }

        List<String> normalizedSkills = normalizeCourseSkillUpdate(dto.getCourseSkills());
        boolean skillsProvided = dto.getCourseSkills() != null;
        List<String> existingSkillTags = course.getCourseSkillTags() != null
                ? course.getCourseSkillTags()
                : Collections.emptyList();
        dto.setCourseSkills(
            skillsProvided ? normalizedSkills : new ArrayList<>(existingSkillTags)
        );

        courseMapper.updateEntity(course, dto, thumbnail);
        course.setUpdatedAt(now());

        Course saved = courseRepository.save(course);
        log.info("Course {} updated by actor {}", courseId, actorId);

        // Sync skill tags → Skill entities (N:N links).
        // Only sync when dto explicitly provides courseSkills; null means "no change to skills".
        if (skillsProvided) {
            syncCourseSkillLinks(courseId, normalizedSkills);
        }

        return courseMapper.toDetailDto(saved);
    }

    @Override
    @Transactional
    public void deleteCourse(Long courseId, Long actorId) {
        log.info("Deleting course {} by actor {}", courseId, actorId);

        Course course = getCourseOrThrow(courseId);
        ensureAuthorOrAdmin(actorId, course.getAuthor().getId());

        // Check if course has enrollments/purchases
        long enrollmentCount = enrollmentRepository.countByCourseId(courseId);
        long purchaseCount = purchaseRepository.countSuccessfulPurchasesByCourseId(courseId);

        boolean canHardDelete = courseDeletionPolicy.canHardDelete(
                course.getStatus(),
                enrollmentCount,
                purchaseCount
        );

        if (canHardDelete) {
            log.warn("Hard delete enabled - deleting course {} (status={}, enrollments={}, purchases={})",
                    courseId, course.getStatus(), enrollmentCount, purchaseCount);
            courseRepository.delete(course);
        } else {
            if (course.getStatus() != CourseStatus.ARCHIVED) {
                course.setStatus(CourseStatus.ARCHIVED);
                course.setUpdatedAt(now());
                courseRepository.save(course);
            }
            log.info("Course {} archived (status={}, enrollments={}, purchases={})",
                    courseId, course.getStatus(), enrollmentCount, purchaseCount);
        }

        log.info("Course {} deleted/archived by actor {}", courseId, actorId);
    }

    @Override
    @Transactional(readOnly = true)
    public CourseDetailDTO getCourse(Long id, Long actorId) {
        log.debug("Fetching course details for id {}, actor {}", id, actorId);
        Course course = courseRepository.findByIdWithAuthorAndModules(id);
        if (course == null) {
            throw new NotFoundException("COURSE_NOT_FOUND");
        }

        // PUBLIC courses are visible to everyone
        // DRAFT/PENDING/REJECTED/ARCHIVED require the actor to be the author or admin
        // SUSPENDED: visible only to owner or ADMIN (admin suspended it, should still be viewable)
        if (course.getStatus() != CourseStatus.PUBLIC) {
            if (actorId == null) {
                throw new AccessDeniedException("COURSE_NOT_ACCESSIBLE");
            }
            ensureAuthorOrAdmin(actorId, course.getAuthor().getId());
        }

        CourseDetailDTO detail = courseMapper.toDetailDto(course);
        applyRevisionToDetail(detail, loadActiveRevisionForReadPath(course));
        return detail;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourseDetailDTO> getCoursesByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        // Only return PUBLIC courses for batch fetch (public-facing endpoint)
        List<Course> courses = courseRepository.findByIdInAndStatus(ids, CourseStatus.PUBLIC);
        return courses.stream()
                .map(course -> {
                    CourseDetailDTO dto = courseMapper.toDetailDto(course);
                    applyRevisionToDetail(dto, loadActiveRevisionForReadPath(course));
                    return dto;
                })
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CourseSummaryDTO> listCourses(String q, CourseStatus status, Pageable pageable) {
        log.debug("Listing courses with query '{}', status '{}', page {}", q, status, pageable.getPageNumber());

        Page<Course> page;

        if (q != null && !q.isBlank()) {
            if (status != null) {
                page = courseRepository.findByStatusAndTitleContainingIgnoreCase(status, q, pageable);
            } else {
                page = courseRepository.findByTitleContainingIgnoreCase(q, pageable);
            }
        } else {
            if (status != null) {
                // Use query with eager loading to avoid LazyInitializationException
                page = courseRepository.findByStatusWithAuthor(status, pageable);
            } else {
                // Use query with eager loading to avoid LazyInitializationException
                page = courseRepository.findAllWithAuthor(pageable);
            }
        }

        Map<Long, CourseRevision> activeRevisionMap = loadActiveRevisionsForReadPath(page.getContent());

        List<CourseSummaryDTO> summaries = page.getContent().stream()
                .map(course -> {
                    CourseSummaryDTO summary = courseMapper.toSummaryDto(course);
                    applyRevisionToSummary(summary, activeRevisionMap.get(course.getActiveRevisionId()));
                    return summary;
                })
                .toList();

        return PageResponse.<CourseSummaryDTO>builder()
                .items(summaries)
                .page(page.getNumber())
                .size(page.getSize())
                .total(page.getTotalElements())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CourseSummaryDTO> listCoursesByAuthor(Long authorId, Pageable pageable) {
        log.debug("Listing courses by author {}, page {}", authorId, pageable.getPageNumber());

        // Use query with eager loading to avoid LazyInitializationException
        Page<Course> page = courseRepository.findByAuthorIdWithAuthor(authorId, pageable);

        // Batch fetch module counts to avoid N+1 query
        Map<Long, Integer> moduleCountMap = getModuleCountMap(page.getContent());
        Map<Long, CourseRevision> activeRevisionMap = loadActiveRevisionsForReadPath(page.getContent());

        List<CourseSummaryDTO> courseSummaries = page.getContent().stream()
                .map(course -> {
                    CourseSummaryDTO summary = courseMapper.toSummaryDto(course);
                    applyRevisionToSummary(summary, activeRevisionMap.get(course.getActiveRevisionId()));
                    summary.setModuleCount(moduleCountMap.getOrDefault(course.getId(), 0));
                    return summary;
                })
                .toList();

        return PageResponse.<CourseSummaryDTO>builder()
                .items(courseSummaries)
                .page(page.getNumber())
                .size(page.getSize())
                .total(page.getTotalElements())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CourseSummaryDTO> listCoursesByAuthor(Long authorId, CourseStatus status, Pageable pageable) {
        log.debug("Listing courses by author {} with status {}, page {}", authorId, status, pageable.getPageNumber());

        Page<Course> page = courseRepository.findByAuthorIdAndStatus(authorId, status, pageable);

        Map<Long, Integer> moduleCountMap = getModuleCountMap(page.getContent());
        Map<Long, CourseRevision> activeRevisionMap = loadActiveRevisionsForReadPath(page.getContent());

        List<CourseSummaryDTO> courseSummaries = page.getContent().stream()
                .map(course -> {
                    CourseSummaryDTO summary = courseMapper.toSummaryDto(course);
                    applyRevisionToSummary(summary, activeRevisionMap.get(course.getActiveRevisionId()));
                    summary.setModuleCount(moduleCountMap.getOrDefault(course.getId(), 0));
                    return summary;
                })
                .toList();

        return PageResponse.<CourseSummaryDTO>builder()
                .items(courseSummaries)
                .page(page.getNumber())
                .size(page.getSize())
                .total(page.getTotalElements())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CourseSummaryDTO> listCoursesByAuthorNonArchived(Long authorId, Pageable pageable) {
        log.debug("Listing non-archived courses by author {}, page {}", authorId, pageable.getPageNumber());

        Page<Course> page = courseRepository.findByAuthorIdNonArchived(authorId, pageable);

        Map<Long, Integer> moduleCountMap = getModuleCountMap(page.getContent());
        Map<Long, CourseRevision> activeRevisionMap = loadActiveRevisionsForReadPath(page.getContent());

        List<CourseSummaryDTO> courseSummaries = page.getContent().stream()
                .map(course -> {
                    CourseSummaryDTO summary = courseMapper.toSummaryDto(course);
                    applyRevisionToSummary(summary, activeRevisionMap.get(course.getActiveRevisionId()));
                    summary.setModuleCount(moduleCountMap.getOrDefault(course.getId(), 0));
                    return summary;
                })
                .toList();

        return PageResponse.<CourseSummaryDTO>builder()
                .items(courseSummaries)
                .page(page.getNumber())
                .size(page.getSize())
                .total(page.getTotalElements())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> getCourseStatsByAuthor(Long authorId) {
        log.debug("Fetching course statistics by status for author {}", authorId);
        Map<String, Long> stats = new HashMap<>();
        for (CourseStatus status : CourseStatus.values()) {
            stats.put(status.name(), courseRepository.countByAuthorIdAndStatus(authorId, status));
        }
        stats.put("ALL", courseRepository.countByAuthorId(authorId));
        return stats;
    }

    @Override
    @Transactional
    public CourseDetailDTO submitCourseForApproval(Long courseId, Long actorId) {
        log.info("Submitting course {} for approval by actor {}", courseId, actorId);

        Course course = getCourseOrThrow(courseId);
        ensureAuthorOrAdmin(actorId, course.getAuthor().getId());

        // Allow submission from DRAFT, REJECTED, or SUSPENDED status (appeal)
        if (course.getStatus() != CourseStatus.DRAFT
                && course.getStatus() != CourseStatus.REJECTED
                && course.getStatus() != CourseStatus.SUSPENDED) {
            throw new ConflictException("COURSE_CANNOT_BE_SUBMITTED_IN_STATUS_" + course.getStatus());
        }

        // Clear rejection info when resubmitting
        course.setRejectionReason(null);
        course.setRejectedAt(null);

        // Clear suspension info when appealing
        course.setSuspensionReason(null);
        course.setSuspendedAt(null);
        course.setSuspendedBy(null);
        course.setStatus(CourseStatus.PENDING);
        course.setSubmittedAt(now());
        course.setUpdatedAt(now());

        Course saved = courseRepository.save(course);
        log.info("Course {} submitted for approval by actor {}", courseId, actorId);

        return courseMapper.toDetailDto(saved);
    }

    @Override
    @Transactional
    public CourseDetailDTO approveCourse(Long courseId, Long adminId) {
        log.info("Admin {} approving course {}", adminId, courseId);

        Course course = getCourseOrThrow(courseId);

        // Only PENDING courses can be approved
        if (course.getStatus() != CourseStatus.PENDING) {
            throw new ConflictException("COURSE_CANNOT_BE_APPROVED_IN_STATUS_" + course.getStatus());
        }

        course.setStatus(CourseStatus.PUBLIC);
        course.setPublishedAt(now());
        course.setUpdatedAt(now());

        Course saved = courseRepository.save(course);
        ensureInitialApprovedRevisionExists(saved, adminId);
        saved = courseRepository.save(saved);

        if (saved.getActiveRevisionId() != null) {
            eventPublisher.publishEvent(
                new CourseRevisionApprovedEvent(this, saved.getId(), saved.getActiveRevisionId())
            );
        }

        log.info("Course {} approved by admin {}", courseId, adminId);

        // Notify the course author
        notificationService.createNotification(
                course.getAuthor().getId(),
                "Khóa học đã được duyệt",
                "Khóa học '" + course.getTitle() + "' đã được admin duyệt và công khai.",
                NotificationType.COURSE_RESTORED,
                courseId.toString()
        );

        // If course has enrolled students (e.g. re-approved after appeal), notify them
        long enrolledCount = enrollmentRepository.countByCourseId(courseId);
        if (enrolledCount > 0) {
            enrollmentRepository.findByCourseId(courseId, Pageable.unpaged())
                    .forEach(enrollment -> notificationService.createNotification(
                            enrollment.getUser().getId(),
                            "Khóa học đã mở lại",
                            "Khóa học '" + course.getTitle() + "' đã được duyệt lại. Bạn có thể tiếp tục học tập.",
                            NotificationType.COURSE_RESTORED,
                            courseId.toString()
                    ));
        }

        return courseMapper.toDetailDto(saved);
    }

    @Override
    @Transactional
    public CourseDetailDTO rejectCourse(Long courseId, Long adminId, String reason) {
        log.info("Admin {} rejecting course {} with reason: {}", adminId, courseId, reason);

        Course course = getCourseOrThrow(courseId);

        // Only PENDING courses can be rejected
        if (course.getStatus() != CourseStatus.PENDING) {
            throw new ConflictException("COURSE_CANNOT_BE_REJECTED_IN_STATUS_" + course.getStatus());
        }

        course.setStatus(CourseStatus.REJECTED);
        course.setRejectionReason(reason);
        course.setRejectedAt(now());
        course.setUpdatedAt(now());

        Course saved = courseRepository.save(course);
        log.info("Course {} rejected by admin {} with reason: {}", courseId, adminId, reason);

        // Notify the course author about the rejection
        notificationService.createNotification(
                course.getAuthor().getId(),
                "Kh\u00f3a h\u1ecdc b\u1ecb t\u1eeb ch\u1ed1i",
                "Kh\u00f3a h\u1ecdc '" + course.getTitle() + "' b\u1ecb t\u1eeb ch\u1ed1i d\u00eayệt."
                        + (reason != null && !reason.isBlank() ? " L\u00fd do: " + reason : ""),
                NotificationType.COURSE_REJECTED,
                courseId.toString()
        );

        return courseMapper.toDetailDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CourseSummaryDTO> listCoursesByStatus(CourseStatus status, Pageable pageable) {
        log.debug("Listing courses with status '{}', page {}", status, pageable.getPageNumber());

        // Use query with eager loading to avoid LazyInitializationException
        Page<Course> page = courseRepository.findByStatusWithAuthor(status, pageable);

        // Batch fetch module counts to avoid N+1 query
        Map<Long, Integer> moduleCountMap = getModuleCountMap(page.getContent());
        Map<Long, CourseRevision> activeRevisionMap = loadActiveRevisionsForReadPath(page.getContent());

        List<CourseSummaryDTO> courseSummaries = page.getContent().stream()
                .map(course -> {
                    CourseSummaryDTO summary = courseMapper.toSummaryDto(course);
                    applyRevisionToSummary(summary, activeRevisionMap.get(course.getActiveRevisionId()));
                    summary.setModuleCount(moduleCountMap.getOrDefault(course.getId(), 0));
                    return summary;
                })
                .toList();

        return PageResponse.<CourseSummaryDTO>builder()
                .items(courseSummaries)
                .page(page.getNumber())
                .size(page.getSize())
                .total(page.getTotalElements())
                .build();
    }

    @Override
    @Transactional
    public CourseDetailDTO suspendCourse(Long courseId, Long adminId, String reason) {
        log.info("Admin {} suspending course {} with reason: {}", adminId, courseId, reason);

        Course course = getCourseOrThrow(courseId);

        // Only PUBLIC courses can be suspended (already-archived courses stay archived)
        if (course.getStatus() != CourseStatus.PUBLIC) {
            throw new ConflictException("COURSE_CANNOT_BE_SUSPENDED_IN_STATUS_" + course.getStatus());
        }

        course.setStatus(CourseStatus.SUSPENDED);
        course.setSuspensionReason(reason);
        course.setSuspendedAt(now());
        course.setSuspendedBy(adminId);
        course.setUpdatedAt(now());

        Course saved = courseRepository.save(course);
        log.info("Course {} suspended by admin {}", courseId, adminId);

        // Notify the course author
        notificationService.createNotification(
                course.getAuthor().getId(),
                "Khóa học bị tạm khóa",
                "Khóa học '" + course.getTitle() + "' đã bị tạm khóa bởi Admin."
                        + (reason != null && !reason.isBlank() ? " Lý do: " + reason : ""),
                NotificationType.COURSE_SUSPENDED,
                courseId.toString()
        );

        // Notify all enrolled students
        enrollmentRepository.findByCourseId(courseId, Pageable.unpaged())
                .forEach(enrollment -> notificationService.createNotification(
                        enrollment.getUser().getId(),
                        "Khóa học bị tạm khóa",
                        "Khóa học '" + course.getTitle() + "' mà bạn đang học đã bị tạm khóa để xem xét. "
                                + "Tiến độ học tập của bạn được giữ nguyên và sẽ khả dụng khi khóa học được mở lại.",
                        NotificationType.COURSE_SUSPENDED,
                        courseId.toString()
                ));

        return courseMapper.toDetailDto(saved);
    }

    @Override
    @Transactional
    public CourseDetailDTO restoreCourse(Long courseId, Long adminId) {
        log.info("Admin {} restoring course {} from suspension", adminId, courseId);

        Course course = getCourseOrThrow(courseId);

        if (course.getStatus() != CourseStatus.SUSPENDED) {
            throw new ConflictException("COURSE_CANNOT_BE_RESTORED_IN_STATUS_" + course.getStatus());
        }

        course.setStatus(CourseStatus.PUBLIC);
        course.setSuspensionReason(null);
        course.setSuspendedAt(null);
        course.setSuspendedBy(null);
        course.setUpdatedAt(now());

        Course saved = courseRepository.save(course);
        log.info("Course {} restored to PUBLIC by admin {}", courseId, adminId);

        // Notify the course author
        notificationService.createNotification(
                course.getAuthor().getId(),
                "Khóa học đã được mở lại",
                "Khóa học '" + course.getTitle() + "' đã được admin duyệt và mở lại thành công.",
                NotificationType.COURSE_RESTORED,
                courseId.toString()
        );

        // Notify all enrolled students that the course is available again
        enrollmentRepository.findByCourseId(courseId, Pageable.unpaged())
                .forEach(enrollment -> notificationService.createNotification(
                        enrollment.getUser().getId(),
                        "Khóa học đã mở lại",
                        "Khóa học '" + course.getTitle() + "' đã được mở lại. Bạn có thể tiếp tục học tập.",
                        NotificationType.COURSE_RESTORED,
                        courseId.toString()
                ));

        return courseMapper.toDetailDto(saved);
    }

    @Override
    @Transactional
    public CourseDetailDTO updateUpgradePolicy(Long courseId, CourseUpgradePolicy policy, Long actorId) {
        log.info("Updating course {} upgrade policy to {} by actor {}", courseId, policy, actorId);

        Course course = getCourseOrThrow(courseId);
        ensureAuthorOrAdmin(actorId, course.getAuthor().getId());

        CourseUpgradePolicy effectivePolicy = policy;

        course.setUpgradePolicy(effectivePolicy);
        course.setUpdatedAt(now());

        courseRepository.save(course);
        int syncedEnrollments = enrollmentRepository.syncUpgradePolicySnapshotByStatus(
                courseId,
            effectivePolicy.name(),
                EnrollmentStatus.ENROLLED
        );
        log.info("Synced upgrade policy snapshot for {} enrolled learners in course {}", syncedEnrollments, courseId);

        /*
         * syncUpgradePolicySnapshotByStatus uses a bulk update query.
         * Repository method currently clears persistence context, so entities may become detached.
         * Re-load a managed Course instance before mapping to avoid LazyInitializationException.
         */
        Course refreshedCourse = getCourseOrThrow(courseId);
        CourseDetailDTO detailDTO = courseMapper.toDetailDto(refreshedCourse);
        detailDTO.setUpgradePolicyStatusMessage(
            buildUpgradePolicyStatusMessage(effectivePolicy)
        );
        return detailDTO;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> getCourseStats() {
        log.debug("Fetching course statistics by status");
        Map<String, Long> stats = new HashMap<>();
        for (CourseStatus status : CourseStatus.values()) {
            stats.put(status.name(), courseRepository.countByStatus(status));
        }
        stats.put("ALL", courseRepository.count());
        return stats;
    }

    // ===== Helper Methods =====

    private Map<Long, CourseRevision> loadActiveRevisionsForReadPath(List<Course> courses) {
        if (!courseRevisionFeatureProperties.isReadEnabled() || courses == null || courses.isEmpty()) {
            return Collections.emptyMap();
        }

        Set<Long> revisionIds = courses.stream()
                .filter(this::canUseRevisionReadPath)
                .map(Course::getActiveRevisionId)
                .collect(Collectors.toSet());

        if (revisionIds.isEmpty()) {
            return Collections.emptyMap();
        }

        return courseRevisionRepository.findAllById(revisionIds).stream()
                .collect(Collectors.toMap(CourseRevision::getId, revision -> revision));
    }

    private CourseRevision loadActiveRevisionForReadPath(Course course) {
        if (!canUseRevisionReadPath(course)) {
            return null;
        }

        return courseRevisionRepository.findById(course.getActiveRevisionId()).orElse(null);
    }

    private boolean canUseRevisionReadPath(Course course) {
        return course != null
                && courseRevisionFeatureProperties.isReadEnabled()
                && Boolean.TRUE.equals(course.getRevisioningEnabled())
                && course.getActiveRevisionId() != null;
    }

    private String buildUpgradePolicyStatusMessage(CourseUpgradePolicy policy) {
        return "MANUAL: learner giữ revision hiện tại cho đến khi chủ động nâng cấp.";
    }

    private void ensureInitialApprovedRevisionExists(Course course, Long adminId) {
        if (course == null || course.getId() == null) {
            return;
        }
        if (course.getActiveRevisionId() != null) {
            return;
        }
        if (courseRevisionRepository.findTopByCourseIdOrderByRevisionNumberDesc(course.getId()).isPresent()) {
            return;
        }

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
                .learningObjectivesJson(objectMapper.valueToTree(course.getLearningObjectives()))
                .requirementsJson(objectMapper.valueToTree(course.getRequirements()))
                .courseSkillTagsJson(objectMapper.valueToTree(
                    course.getCourseSkillTags() != null ? course.getCourseSkillTags() : Collections.emptyList()
                ))
                .contentSnapshotJson(CourseRevisionSnapshotAssembler.buildCourseContentSnapshot(
                        objectMapper,
                        course,
                        1
                ))
                .sourceRevisionId(null)
                .sourceCourseStatus(course.getStatus().name())
                .snapshotVersion(1)
                .createdBy(adminId)
                .createdAt(now())
                .submittedAt(course.getSubmittedAt() != null ? course.getSubmittedAt() : now())
                .approvedAt(now())
                .updatedAt(now())
                .build();

        CourseRevision savedRevision = courseRevisionRepository.save(initialRevision);
        course.setActiveRevisionId(savedRevision.getId());
        course.setLatestRevisionId(savedRevision.getId());
        course.setRevisioningEnabled(Boolean.TRUE);
    }

    /**
     * Apply active revision metadata to CourseDetailDTO.
     *
     * <p>Note: This method overlays course-level metadata (title, description, etc.) from the
     * active revision. However, CourseDetailDTO.modules reflects the live module structure
     * from the entity mapping, not the pinned snapshot. For enrolled learners,
     * RevisionPinnedContentResolver provides the correct pinned view. The public batch
     * endpoint intentionally returns live structure for simplicity (no actorId required).
     */
    private void applyRevisionToDetail(CourseDetailDTO detail, CourseRevision revision) {
        if (detail == null || revision == null) {
            return;
        }

        detail.setTitle(revision.getTitle());
        detail.setDescription(revision.getDescription());
        detail.setShortDescription(revision.getShortDescription());
        detail.setLevel(revision.getLevel());
        detail.setCategory(revision.getCategory());
        detail.setEstimatedDurationHours(revision.getEstimatedDurationHours());
        detail.setLanguage(revision.getLanguage());
        detail.setPrice(revision.getPrice());
        detail.setCurrency(revision.getCurrency());
        detail.setLearningObjectives(toStringList(revision.getLearningObjectivesJson()));
        detail.setRequirements(toStringList(revision.getRequirementsJson()));
        detail.setCourseSkills(toStringList(revision.getCourseSkillTagsJson()));
    }

    private void applyRevisionToSummary(CourseSummaryDTO summary, CourseRevision revision) {
        if (summary == null || revision == null) {
            return;
        }

        summary.setTitle(revision.getTitle());
        summary.setShortDescription(revision.getShortDescription());
        summary.setLevel(revision.getLevel());
        summary.setCategory(revision.getCategory());
        summary.setEstimatedDurationHours(revision.getEstimatedDurationHours());
        summary.setLanguage(revision.getLanguage());
        summary.setPrice(revision.getPrice());
        summary.setCurrency(revision.getCurrency());
        summary.setLessonCount(countLessonLikeItemsFromRevisionSnapshot(revision.getContentSnapshotJson()));
    }

    private int countLessonLikeItemsFromRevisionSnapshot(JsonNode contentSnapshotJson) {
        if (contentSnapshotJson == null || !contentSnapshotJson.isObject()) {
            return 0;
        }

        JsonNode modulesNode = contentSnapshotJson.path("modules");
        if (!modulesNode.isArray()) {
            return 0;
        }

        int total = 0;
        for (JsonNode moduleNode : modulesNode) {
            if (moduleNode == null || !moduleNode.isObject()) {
                continue;
            }

            JsonNode lessonsNode = moduleNode.path("lessons");
            if (lessonsNode.isArray()) {
                total += lessonsNode.size();
                continue;
            }

            // Backward compatibility for very old snapshot structures.
            JsonNode legacyItemsNode = moduleNode.path("items");
            JsonNode legacyQuizzesNode = moduleNode.path("quizzes");
            JsonNode legacyAssignmentsNode = moduleNode.path("assignments");

            if (legacyItemsNode.isArray()) {
                total += legacyItemsNode.size();
            }
            if (legacyQuizzesNode.isArray()) {
                total += legacyQuizzesNode.size();
            }
            if (legacyAssignmentsNode.isArray()) {
                total += legacyAssignmentsNode.size();
            }
        }

        return total;
    }

    private List<String> toStringList(JsonNode jsonNode) {
        if (jsonNode == null || !jsonNode.isArray()) {
            return Collections.emptyList();
        }

        return StreamSupport.stream(jsonNode.spliterator(), false)
                .map(node -> node == null || node.isNull() ? null : node.asText())
                .filter(value -> value != null && !value.isBlank())
                .toList();
    }

    /**
     * Batch fetch module counts for a list of courses.
     * Uses a single query instead of N queries (fixes N+1 problem).
     */
    private Map<Long, Integer> getModuleCountMap(List<Course> courses) {
        if (courses.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> courseIds = courses.stream().map(Course::getId).toList();
        List<Object[]> counts = moduleRepository.countByCourseIds(courseIds);
        return counts.stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> ((Long) row[1]).intValue()
                ));
    }

    private Course getCourseOrThrow(Long id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("COURSE_NOT_FOUND"));
    }

    private void ensureAuthorOrAdmin(Long actorId, Long authorId) {
        // Allow if actor is the author
        if (actorId.equals(authorId)) {
            return;
        }

        // Check if actor has ADMIN role via SecurityContext
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN") || a.equals("ROLE_CONTENT_ADMIN"))) {
            log.debug("Actor {} allowed via admin role", actorId);
            return;
        }

        throw new AccessDeniedException("FORBIDDEN");
    }

    private void validateCreateCourseRequest(CourseCreateDTO dto) {
        if (dto.getTitle() == null || dto.getTitle().isBlank()) {
            throw new IllegalArgumentException("Course title is required");
        }
        // TODO: add more validation (description length, level validity, etc.)
    }

    private void validateUpdateCourseRequest(CourseUpdateDTO dto) {
        if (dto.getTitle() != null && dto.getTitle().isBlank()) {
            throw new IllegalArgumentException("Course title cannot be blank");
        }
        // TODO: add more validation
    }

    private Instant now() {
        return Instant.now(clock);
    }

    /**
     * Upload thumbnail file to Cloudinary and save as Media entity.
     * Extracted from Controller to follow SRP (Single Responsibility Principle).
     */
    private Media uploadThumbnail(MultipartFile thumbnailFile, Long uploaderId) {
        try {
            log.info("Uploading thumbnail file: {}", thumbnailFile.getOriginalFilename());

            String folder = "skillverse/user_" + uploaderId;
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
            thumbnail.setUploadedAt(LocalDateTime.now());
            thumbnail.setCloudinaryPublicId(publicId);
            thumbnail.setCloudinaryResourceType(resourceType);

            Media savedThumbnail = mediaRepository.save(thumbnail);
            log.info("Thumbnail uploaded successfully: {} - {}", savedThumbnail.getId(), savedThumbnail.getUrl());
            return savedThumbnail;
        } catch (Exception e) {
            log.error("Failed to upload thumbnail: {}", e.getMessage());
            throw new MediaOperationException("Thumbnail upload failed: " + e.getMessage(), e);
        }
    }

    // ========== Ban/Unban Cascade Methods ==========

    @Override
    @Transactional
    public int restoreAllSuspendedCoursesByAuthor(Long authorId) {
        List<Course> suspendedCourses = courseRepository
                .findByAuthorIdAndStatus(authorId, CourseStatus.SUSPENDED, Pageable.unpaged())
                .getContent();
        for (Course course : suspendedCourses) {
            course.setStatus(CourseStatus.PUBLIC);
            course.setSuspensionReason(null);
            course.setSuspendedAt(null);
            course.setSuspendedBy(null);
            course.setUpdatedAt(now());
            courseRepository.save(course);
        }
        log.info("Restored {} suspended courses for unbanned mentor {}", suspendedCourses.size(), authorId);
        return suspendedCourses.size();
    }

    /**
     * Syncs course_skill (N:N) links from plain skill tag names.
     * Called after course creation or update.
     *
     * <p>Flow:
     * <ul>
     *   <li>For each tag name → findOrCreate Skill entity (upsert)</li>
     *   <li>For each Skill → create CourseSkill link if not exists</li>
     *   <li>Remove CourseSkill links for tags no longer present</li>
     * </ul>
     *
     * <p>Keeps course_skill_tags (ElementCollection) in sync with course_skill (N:N entity).
     * The plain String tags are used for BM25 indexing; the entity links enable taxonomy.
     *
     * @param courseId the course ID
     * @param skillNames list of skill tag names (e.g. ["JAVA", "SPRING"])
     */
    @Transactional
    public void syncCourseSkillLinks(Long courseId, List<String> skillNames) {
        if (skillNames == null || skillNames.isEmpty()) {
            // No skills → delete all existing links
            courseSkillRepository.deleteByCourseId(courseId);
            log.debug("[SkillLink] Course {} has no skill tags, cleared all links", courseId);
            return;
        }

        // Deduplicate raw skill names; canonical keys are used only for lookup/comparison.
        List<String> sanitizedNames = skillNames.stream()
                .filter(n -> n != null && !n.isBlank())
            .map(String::trim)
                .distinct()
                .collect(Collectors.toList());

        Set<String> desiredCanonicalKeys = sanitizedNames.stream()
            .map(SkillNameUtils::normalize)
            .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

        // Fetch Course once to satisfy @MapsId FK requirement on CourseSkill
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalStateException("Course " + courseId + " not found"));

        // Step 1: Resolve existing ACTIVE Skill entities. Course flows must not create taxonomy data.
        for (String rawName : sanitizedNames) {
            Skill skill = resolveExistingActiveSkillByTag(rawName);

            // Step 2: Create CourseSkill link if not exists
            if (!courseSkillRepository.existsByCourseIdAndSkillId(courseId, skill.getId())) {
                CourseSkill link = CourseSkill.builder()
                        .id(new CourseSkillId(courseId, skill.getId()))
                        .course(course)
                        .skill(skill)
                        .build();
                courseSkillRepository.save(link);
                log.debug("[SkillLink] Linked course {} to skill '{}' (id={})",
                        courseId, rawName, skill.getId());
            }
        }

        // Step 3: Remove links for tags no longer present
                List<Skill> currentSkills = courseSkillRepository.findSkillsByCourseId(courseId);
                List<Skill> toRemove = currentSkills.stream()
                    .filter(skill -> !desiredCanonicalKeys.contains(resolveSkillCanonicalKey(skill)))
                .collect(Collectors.toList());

                for (Skill skillToRemove : toRemove) {
                    courseSkillRepository.deleteByCourseIdAndSkillId(courseId, skillToRemove.getId());
                    log.debug("[SkillLink] Unlinked course {} from skill '{}' (id={})",
                        courseId, skillToRemove.getName(), skillToRemove.getId());
        }

                Set<String> currentCanonicalKeys = currentSkills.stream()
                    .map(this::resolveSkillCanonicalKey)
                    .collect(Collectors.toSet());
                long addedCount = desiredCanonicalKeys.stream()
                    .filter(key -> !currentCanonicalKeys.contains(key))
                    .count();

        log.info("[SkillLink] Synced {} skill links for course {} (added={}, removed={})",
                    sanitizedNames.size(), courseId, addedCount, toRemove.size());
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

                private String resolveSkillCanonicalKey(Skill skill) {
                if (skill == null) {
                    return null;
                }
                return skill.getCanonicalKey() != null && !skill.getCanonicalKey().isBlank()
                    ? skill.getCanonicalKey()
                    : SkillNameUtils.normalize(skill.getName());
                }

    private List<String> normalizeCourseSkillUpdate(List<String> courseSkills) {
        if (courseSkills == null) {
            return null;
        }

        List<String> normalized = courseSkills.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();

        if (normalized.size() == 1 && "__EMPTY__".equals(normalized.get(0))) {
            return Collections.emptyList();
        }

        return normalized.stream()
                .filter(value -> !"__EMPTY__".equals(value))
                .toList();
    }
}
