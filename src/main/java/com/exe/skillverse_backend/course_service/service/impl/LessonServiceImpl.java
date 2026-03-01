package com.exe.skillverse_backend.course_service.service.impl;

import com.exe.skillverse_backend.course_service.dto.lessondto.LessonBriefDTO;
import com.exe.skillverse_backend.course_service.dto.lessondto.LessonDetailDTO;
import com.exe.skillverse_backend.course_service.dto.lessondto.LessonCreateDTO;
import com.exe.skillverse_backend.course_service.dto.lessondto.LessonUpdateDTO;
import com.exe.skillverse_backend.course_service.entity.Lesson;
import com.exe.skillverse_backend.course_service.entity.Module;
import com.exe.skillverse_backend.course_service.entity.LessonProgress;
import com.exe.skillverse_backend.course_service.entity.LessonProgressId;
import com.exe.skillverse_backend.course_service.mapper.LessonMapper;
import com.exe.skillverse_backend.course_service.repository.LessonRepository;
import com.exe.skillverse_backend.course_service.repository.LessonProgressRepository;
import com.exe.skillverse_backend.course_service.repository.ModuleRepository;
import com.exe.skillverse_backend.course_service.service.CourseLearningProgressService;
import com.exe.skillverse_backend.course_service.service.LessonService;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.shared.entity.Media;
import com.exe.skillverse_backend.shared.exception.AccessDeniedException;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import com.exe.skillverse_backend.shared.repository.MediaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LessonServiceImpl implements LessonService {

    private final LessonRepository lessonRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final ModuleRepository moduleRepository;
    private final MediaRepository mediaRepository;
    private final LessonMapper lessonMapper;
    private final Clock clock;
    private final UserRepository userRepository;
    private final CourseLearningProgressService courseLearningProgressService;

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

        // TODO: Check policy for cascade delete of quizzes/assignments/exercises
        // For now, allow deletion (repositories have cascade configurations)
        long relatedContentCount = countRelatedContent(lessonId);
        if (relatedContentCount > 0) {
            log.warn("Lesson {} has {} related content items that will be deleted",
                    lessonId, relatedContentCount);
        }

        lessonRepository.delete(lesson);
        log.info("Lesson {} deleted by actor {}", lessonId, actorId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LessonBriefDTO> listLessonsByModule(Long moduleId) {
        log.debug("Listing lessons for module {}", moduleId);

        // Verify module exists
        getModuleOrThrow(moduleId);

        List<Lesson> lessons = lessonRepository.findByModuleIdOrderByOrderIndexAsc(moduleId);
        return lessons.stream()
                .map(lessonMapper::toBriefDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public LessonDetailDTO getLesson(Long lessonId) {
        Lesson lesson = getLessonOrThrow(lessonId);
        return lessonMapper.toDetailDto(lesson);
    }

    @Override
    @Transactional(readOnly = true)
    public LessonBriefDTO getNextLesson(Long moduleId, Long currentLessonId) {
        Lesson current = getLessonOrThrow(currentLessonId);
        if (!current.getModule().getId().equals(moduleId)) {
            throw new NotFoundException("LESSON_NOT_IN_MODULE");
        }
        return lessonRepository.findNextLesson(moduleId, current.getOrderIndex())
                .map(lessonMapper::toBriefDto)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public LessonBriefDTO getPreviousLesson(Long moduleId, Long currentLessonId) {
        Lesson current = getLessonOrThrow(currentLessonId);
        if (!current.getModule().getId().equals(moduleId)) {
            throw new NotFoundException("LESSON_NOT_IN_MODULE");
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
    public List<Long> listCompletedLessonIds(Long courseId, Long userId) {
        return lessonProgressRepository.findCompletedLessonIdsByCourseAndUser(courseId, userId);
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
        // Allow if actor is the author of the course
        if (actorId.equals(authorId)) {
            return;
        }

        // Only ADMIN can bypass the author check.
        // This prevents Mentor A from editing Mentor B's lessons.
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN") || a.equals("ROLE_CONTENT_ADMIN"))) {
            log.debug("Actor {} allowed via admin role", actorId);
            return;
        }

        throw new AccessDeniedException("FORBIDDEN");
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
