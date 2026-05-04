package com.exe.skillverse_backend.course_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.course_service.dto.lessondto.LessonBriefDTO;
import com.exe.skillverse_backend.course_service.dto.lessondto.LessonCreateDTO;
import com.exe.skillverse_backend.course_service.dto.lessondto.LessonDetailDTO;
import com.exe.skillverse_backend.course_service.dto.lessondto.LessonUpdateDTO;
import com.exe.skillverse_backend.course_service.dto.attachmentdto.LessonAttachmentDTO;
import com.exe.skillverse_backend.course_service.dto.moduledto.ModuleDetailDTO;
import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.course_service.entity.Lesson;
import com.exe.skillverse_backend.course_service.entity.LessonProgress;
import com.exe.skillverse_backend.course_service.entity.LessonProgressId;
import com.exe.skillverse_backend.course_service.entity.Module;
import com.exe.skillverse_backend.course_service.entity.enums.CourseStatus;
import com.exe.skillverse_backend.course_service.entity.enums.EnrollmentStatus;
import com.exe.skillverse_backend.course_service.mapper.LessonMapper;
import com.exe.skillverse_backend.course_service.mapper.LessonAttachmentMapper;
import com.exe.skillverse_backend.course_service.repository.CourseEnrollmentRepository;
import com.exe.skillverse_backend.course_service.repository.LessonAttachmentRepository;
import com.exe.skillverse_backend.course_service.repository.LessonProgressRepository;
import com.exe.skillverse_backend.course_service.repository.LessonRepository;
import com.exe.skillverse_backend.course_service.repository.ModuleRepository;
import com.exe.skillverse_backend.course_service.service.CourseLearningProgressService;
import com.exe.skillverse_backend.course_service.service.LessonService;
import com.exe.skillverse_backend.shared.entity.Media;
import com.exe.skillverse_backend.shared.exception.AccessDeniedException;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import com.exe.skillverse_backend.shared.repository.MediaRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LessonServiceImpl implements LessonService {

    private final LessonRepository lessonRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final LessonAttachmentRepository lessonAttachmentRepository;
    private final ModuleRepository moduleRepository;
    private final CourseEnrollmentRepository enrollmentRepository;
    private final MediaRepository mediaRepository;
    private final LessonMapper lessonMapper;
    private final LessonAttachmentMapper attachmentMapper;
    private final Clock clock;
    private final UserRepository userRepository;
    private final CourseLearningProgressService courseLearningProgressService;
    private final RevisionPinnedContentResolver revisionPinnedContentResolver;

    @Override
    @Transactional
    public LessonBriefDTO addLesson(Long moduleId, LessonCreateDTO dto, Long actorId) {
        log.info("Adding lesson '{}' to module {} by actor {}", dto.getTitle(), moduleId, actorId);

        Module module = getModuleOrThrow(moduleId);
        ensureAuthorOrAdmin(actorId, module.getCourse().getAuthor().getId());

        validateCreateLessonRequest(dto);

        // Load video media if provided
        Media videoMedia = null;
        if (dto.getVideoMediaId() != null) {
            videoMedia = mediaRepository.findById(dto.getVideoMediaId())
                    .orElseThrow(() -> new NotFoundException("MEDIA_NOT_FOUND"));
        }

        // Auto-generate orderIndex if not provided
        Integer orderIndex = dto.getOrderIndex();
        if (orderIndex == null) {
            // Use count + 1 as the next order index
            orderIndex = (int) (lessonRepository.countByModuleId(moduleId) + 1);
        }

        Lesson lesson = lessonMapper.toEntity(dto, module, videoMedia);
        lesson.setOrderIndex(orderIndex);
        lesson.setCreatedAt(now());

        Lesson saved = lessonRepository.save(lesson);
        log.info("Lesson {} added to module {} by actor {}", saved.getId(), moduleId, actorId);

        return lessonMapper.toBriefDto(saved);
    }

    @Override
    @Transactional
    public LessonBriefDTO updateLesson(Long lessonId, LessonUpdateDTO dto, Long actorId) {
        log.info("Updating lesson {} by actor {}", lessonId, actorId);

        Lesson lesson = getLessonOrThrow(lessonId);
        ensureAuthorOrAdmin(actorId, lesson.getModule().getCourse().getAuthor().getId());

        validateUpdateLessonRequest(dto);

        // Load new video media if provided
        Media videoMedia = null;
        if (dto.getVideoMediaId() != null) {
            videoMedia = mediaRepository.findById(dto.getVideoMediaId())
                    .orElseThrow(() -> new NotFoundException("MEDIA_NOT_FOUND"));
        }

        // Clear conflicting video source when switching types:
        // - If YouTube URL provided, clear uploaded video
        // - If videoMedia provided, clear YouTube URL
        if (dto.getVideoUrl() != null && !dto.getVideoUrl().isBlank()) {
            lesson.setVideoMedia(null);
            log.debug("Cleared videoMedia for lesson {} because YouTube URL provided", lessonId);
        } else if (videoMedia != null) {
            lesson.setVideoUrl(null);
            log.debug("Cleared videoUrl for lesson {} because video file uploaded", lessonId);
        }

        lessonMapper.updateEntity(lesson, dto, videoMedia);

        Lesson saved = lessonRepository.save(lesson);
        log.info("Lesson {} updated by actor {}", lessonId, actorId);

        return lessonMapper.toBriefDto(saved);
    }

    @Override
    @Transactional
    public void deleteLesson(Long lessonId, Long actorId) {
        log.info("Deleting lesson {} by actor {}", lessonId, actorId);

        Lesson lesson = getLessonOrThrow(lessonId);
        ensureAuthorOrAdmin(actorId, lesson.getModule().getCourse().getAuthor().getId());

        // Cascade delete: Delete attachments and progress for this lesson first
        // to avoid FK constraint violations
        log.info("Cascade deleting attachments and progress for lesson {}", lessonId);

        // Delete all attachments for this lesson
        long attachmentCount = lessonAttachmentRepository.countByLessonId(lessonId);
        if (attachmentCount > 0) {
            lessonAttachmentRepository.deleteByLessonId(lessonId);
            log.info("Deleted {} attachments for lesson {}", attachmentCount, lessonId);
        }

        // Delete all progress records for this lesson
        // Note: LessonProgress uses composite key, we need to find and delete individually
        // or use a custom delete query
        log.debug("Deleting progress records for lesson {}", lessonId);

        // Finally delete the lesson
        lessonRepository.delete(lesson);
        log.info("Lesson {} deleted by actor {}", lessonId, actorId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LessonBriefDTO> listLessonsByModule(Long moduleId, Long actorId) {
        log.debug("Listing lessons for module {}", moduleId);

        Module module = getModuleOrThrow(moduleId);
        Course course = module.getCourse();
        ensureCanReadCourseStructure(course.getId(), course.getAuthor().getId(), course.getStatus(), actorId);

        if (shouldUsePinnedSnapshotForLearner(course, actorId)) {
            ModuleDetailDTO pinnedModule = revisionPinnedContentResolver
                    .resolvePinnedModule(course, actorId, moduleId)
                    .orElseThrow(() -> new NotFoundException("MODULE_NOT_FOUND"));
            return pinnedModule.getLessons() != null ? pinnedModule.getLessons() : List.of();
        }

        List<Lesson> lessons = lessonRepository.findByModuleIdOrderByOrderIndexAsc(moduleId);
        return lessons.stream()
                .map(lessonMapper::toBriefDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public LessonDetailDTO getLesson(Long lessonId, Long actorId) {
        Lesson lesson = getLessonOrThrow(lessonId);
        Course course = lesson.getModule().getCourse();
        ensureCanAccessLearningContent(course.getId(), course.getAuthor().getId(), actorId);
        ensurePinnedLessonAccessibleForLearner(course, actorId, lessonId);
        LessonDetailDTO dto = lessonMapper.toDetailDto(lesson);
        if (lesson.getAttachments() != null) {
            List<LessonAttachmentDTO> attachmentDTOs = lesson.getAttachments().stream()
                    .map(attachmentMapper::toDto)
                    .collect(java.util.stream.Collectors.toList());
            dto.setAttachments(attachmentDTOs);
        }
        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public LessonBriefDTO getNextLesson(Long moduleId, Long currentLessonId, Long actorId) {
        Lesson current = getLessonOrThrow(currentLessonId);
        if (!current.getModule().getId().equals(moduleId)) {
            throw new NotFoundException("LESSON_NOT_IN_MODULE");
        }
        Course course = current.getModule().getCourse();
        ensureCanAccessLearningContent(course.getId(), course.getAuthor().getId(), actorId);

        if (shouldUsePinnedSnapshotForLearner(course, actorId)) {
            ModuleDetailDTO pinnedModule = revisionPinnedContentResolver
                    .resolvePinnedModule(course, actorId, moduleId)
                    .orElseThrow(() -> new NotFoundException("MODULE_NOT_FOUND"));
            List<LessonBriefDTO> pinnedLessons = pinnedModule.getLessons() != null ? pinnedModule.getLessons() : List.of();
            int currentIndex = findPinnedLessonIndex(pinnedLessons, currentLessonId);
            if (currentIndex < 0) {
                throw new NotFoundException("LESSON_NOT_FOUND");
            }
            return currentIndex + 1 < pinnedLessons.size() ? pinnedLessons.get(currentIndex + 1) : null;
        }

        return lessonRepository.findNextLesson(moduleId, current.getOrderIndex())
                .map(lessonMapper::toBriefDto)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public LessonBriefDTO getPreviousLesson(Long moduleId, Long currentLessonId, Long actorId) {
        Lesson current = getLessonOrThrow(currentLessonId);
        if (!current.getModule().getId().equals(moduleId)) {
            throw new NotFoundException("LESSON_NOT_IN_MODULE");
        }
        Course course = current.getModule().getCourse();
        ensureCanAccessLearningContent(course.getId(), course.getAuthor().getId(), actorId);

        if (shouldUsePinnedSnapshotForLearner(course, actorId)) {
            ModuleDetailDTO pinnedModule = revisionPinnedContentResolver
                    .resolvePinnedModule(course, actorId, moduleId)
                    .orElseThrow(() -> new NotFoundException("MODULE_NOT_FOUND"));
            List<LessonBriefDTO> pinnedLessons = pinnedModule.getLessons() != null ? pinnedModule.getLessons() : List.of();
            int currentIndex = findPinnedLessonIndex(pinnedLessons, currentLessonId);
            if (currentIndex < 0) {
                throw new NotFoundException("LESSON_NOT_FOUND");
            }
            return currentIndex > 0 ? pinnedLessons.get(currentIndex - 1) : null;
        }

        return lessonRepository.findPreviousLesson(moduleId, current.getOrderIndex())
                .map(lessonMapper::toBriefDto)
                .orElse(null);
    }

    @Override
    @Transactional
    public void markLessonCompleted(Long moduleId, Long lessonId, Long userId) {
        Lesson lesson = getLessonOrThrow(lessonId);
        if (!lesson.getModule().getId().equals(moduleId)) {
            throw new NotFoundException("LESSON_NOT_IN_MODULE");
        }
        Course course = lesson.getModule().getCourse();
        ensureCanAccessLearningContent(course.getId(), course.getAuthor().getId(), userId);
        ensurePinnedLessonAccessibleForLearner(course, userId, lessonId);

        LessonProgressId id = new LessonProgressId(userId, lessonId);
        boolean exists = lessonProgressRepository.existsByUserAndLesson(userId, lessonId);
        if (!exists) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND"));
            LessonProgress progress = LessonProgress.builder()
                    .id(id)
                    .user(user)
                    .lesson(lesson)
                    .completed(true)
                    .completedAt(now())
                    .build();
            lessonProgressRepository.save(progress);

            courseLearningProgressService.recalculateCourseProgress(
                    lesson.getModule().getCourse().getId(),
                    userId
            );
            log.info("Marked lesson {} as completed for user {}. Course progress updated.", lessonId, userId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> listCompletedLessonIds(Long courseId, Long actorId) {
        Module anyModule = moduleRepository.findByCourseIdOrderByOrderIndexAsc(courseId)
                .stream()
                .findFirst()
                .orElse(null);
        if (anyModule != null) {
            ensureCanAccessLearningContent(anyModule.getCourse().getId(), anyModule.getCourse().getAuthor().getId(), actorId);
        }
        return lessonProgressRepository.findCompletedLessonIdsByCourseAndUser(courseId, actorId);
    }
    
    // ===== Helper Methods =====

    private Module getModuleOrThrow(Long moduleId) {
        return moduleRepository.findById(moduleId)
                .orElseThrow(() -> new NotFoundException("MODULE_NOT_FOUND"));
    }

    private Lesson getLessonOrThrow(Long lessonId) {
        return lessonRepository.findById(lessonId)
                .orElseThrow(() -> new NotFoundException("LESSON_NOT_FOUND"));
    }

    private void ensureAuthorOrAdmin(Long actorId, Long authorId) {
        if (!isAuthorOrAdmin(actorId, authorId)) {
            throw new AccessDeniedException("FORBIDDEN");
        }
    }

    private void ensureCanReadCourseStructure(Long courseId, Long authorId, CourseStatus courseStatus, Long actorId) {
        if (isAuthorOrAdmin(actorId, authorId)) {
            return;
        }
        if (courseStatus == CourseStatus.PUBLIC) {
            return;
        }
        ensureCanAccessLearningContent(courseId, authorId, actorId);
    }

    private void ensureCanAccessLearningContent(Long courseId, Long authorId, Long actorId) {
        if (isAuthorOrAdmin(actorId, authorId)) {
            return;
        }
        enrollmentRepository.findByCourseIdAndUserId(courseId, actorId)
                .filter(enrollment -> hasLearningAccess(enrollment.getStatus()))
                .orElseThrow(() -> new AccessDeniedException("USER_NOT_ENROLLED"));
    }

    private boolean hasLearningAccess(EnrollmentStatus status) {
        return status == EnrollmentStatus.ENROLLED || status == EnrollmentStatus.COMPLETED;
    }

    private boolean shouldUsePinnedSnapshotForLearner(Course course, Long actorId) {
        return course != null
                && Boolean.TRUE.equals(course.getRevisioningEnabled())
                && actorId != null
                && !isAuthorOrAdmin(actorId, course.getAuthor().getId())
                && revisionPinnedContentResolver.hasLearningAccessEnrollment(course, actorId);
    }

    private void ensurePinnedLessonAccessibleForLearner(Course course, Long actorId, Long lessonId) {
        if (!shouldUsePinnedSnapshotForLearner(course, actorId)) {
            return;
        }
        if (!revisionPinnedContentResolver.isLessonInPinnedRevision(course, actorId, lessonId)) {
            throw new NotFoundException("LESSON_NOT_FOUND");
        }
    }

    private int findPinnedLessonIndex(List<LessonBriefDTO> lessons, Long lessonId) {
        if (lessons == null || lessonId == null) {
            return -1;
        }
        for (int index = 0; index < lessons.size(); index++) {
            LessonBriefDTO lesson = lessons.get(index);
            if (lesson != null && lessonId.equals(lesson.getId())) {
                return index;
            }
        }
        return -1;
    }

    private boolean isAuthorOrAdmin(Long actorId, Long authorId) {
        if (actorId == null) {
            throw new AccessDeniedException("UNAUTHORIZED");
        }
        if (actorId.equals(authorId)) {
            return true;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN") || a.equals("ROLE_CONTENT_ADMIN"));
    }

    private long countRelatedContent(Long lessonId) {
        // Count quizzes, assignments, and coding exercises for this lesson
        // TODO: implement actual counting from respective repositories
        // For now, return 0 to allow deletion
        return 0;
    }

    private void validateCreateLessonRequest(LessonCreateDTO dto) {
        if (dto.getTitle() == null || dto.getTitle().isBlank()) {
            throw new IllegalArgumentException("Lesson title is required");
        }
        // Reject if both video sources provided
        boolean hasVideoUrl = dto.getVideoUrl() != null && !dto.getVideoUrl().isBlank();
        boolean hasVideoMedia = dto.getVideoMediaId() != null;
        if (hasVideoUrl && hasVideoMedia) {
            throw new IllegalArgumentException("Cannot provide both YouTube URL and uploaded video. Please choose one.");
        }
        if (dto.getType() == null) {
            throw new IllegalArgumentException("Lesson type is required");
        }
        // TODO: add more validation (duration, content requirements, etc.)
    }

    private void validateUpdateLessonRequest(LessonUpdateDTO dto) {
        if (dto.getTitle() != null && dto.getTitle().isBlank()) {
            throw new IllegalArgumentException("Lesson title cannot be blank");
        }
        // TODO: add more validation
    }

    private Instant now() {
        return Instant.now(clock);
    }
}
