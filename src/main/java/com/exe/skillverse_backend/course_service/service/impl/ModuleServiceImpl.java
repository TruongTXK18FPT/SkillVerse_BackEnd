package com.exe.skillverse_backend.course_service.service.impl;

import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.course_service.entity.Lesson;
import com.exe.skillverse_backend.course_service.entity.Module;
import com.exe.skillverse_backend.course_service.entity.Assignment;
import com.exe.skillverse_backend.course_service.entity.Quiz;
import com.exe.skillverse_backend.course_service.entity.enums.CourseStatus;
import com.exe.skillverse_backend.course_service.entity.enums.EnrollmentStatus;
import com.exe.skillverse_backend.course_service.mapper.ModuleMapper;
import com.exe.skillverse_backend.course_service.repository.CourseEnrollmentRepository;
import com.exe.skillverse_backend.course_service.repository.CourseRepository;
import com.exe.skillverse_backend.course_service.repository.LessonProgressRepository;
import com.exe.skillverse_backend.course_service.repository.LessonRepository;
import com.exe.skillverse_backend.course_service.repository.ModuleRepository;
import com.exe.skillverse_backend.course_service.service.ModuleService;
import com.exe.skillverse_backend.shared.exception.AccessDeniedException;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.exe.skillverse_backend.course_service.dto.moduledto.ModuleCreateDTO;
import com.exe.skillverse_backend.course_service.dto.moduledto.ModuleDetailDTO;
import com.exe.skillverse_backend.course_service.dto.moduledto.ModuleProgressDTO;
import com.exe.skillverse_backend.course_service.dto.moduledto.ModuleSummaryDTO;
import com.exe.skillverse_backend.course_service.dto.moduledto.ModuleUpdateDTO;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModuleServiceImpl implements ModuleService {

  private final ModuleRepository moduleRepository;
  private final CourseRepository courseRepository;
  private final CourseEnrollmentRepository enrollmentRepository;
  private final ModuleMapper moduleMapper;
  private final LessonRepository lessonRepository;
  private final LessonProgressRepository lessonProgressRepository;
  private final RevisionPinnedContentResolver revisionPinnedContentResolver;

  @Override
  @Transactional
  public ModuleDetailDTO createModule(Long courseId, ModuleCreateDTO dto, Long actorId) {
    Course course = getCourseOrThrow(courseId);
    ensureAuthorOrAdmin(actorId, course.getAuthor().getId());

    Integer orderIndex = dto.getOrderIndex();
    if (orderIndex == null) {
      orderIndex = (int) (moduleRepository.countByCourseId(courseId) + 1);
    }

    Module entity = moduleMapper.toEntity(dto, course);
    entity.setOrderIndex(orderIndex);
    Module saved = moduleRepository.save(entity);
    return moduleMapper.toDetailDto(saved);
  }

  @Override
  @Transactional
  public ModuleDetailDTO updateModule(Long moduleId, ModuleUpdateDTO dto, Long actorId) {
    Module module = getModuleOrThrow(moduleId);
    ensureAuthorOrAdmin(actorId, module.getCourse().getAuthor().getId());
    moduleMapper.updateEntity(module, dto);
    Module saved = moduleRepository.save(module);
    return moduleMapper.toDetailDto(saved);
  }

  @Override
  @Transactional
  public void deleteModule(Long moduleId, Long actorId) {
    Module module = getModuleOrThrow(moduleId);
    ensureAuthorOrAdmin(actorId, module.getCourse().getAuthor().getId());
    moduleRepository.delete(module);
  }

  @Override
  @Transactional(readOnly = true)
  public List<ModuleSummaryDTO> listModules(Long courseId, Long actorId) {
    Course course = getCourseOrThrow(courseId);
    ensureCanReadCourseStructure(course, actorId);
    if (!isAuthorOrAdmin(actorId, course.getAuthor().getId())) {
      Optional<List<ModuleSummaryDTO>> revisionPinnedModules =
          revisionPinnedContentResolver.resolveModuleSummaries(course, actorId);
      if (revisionPinnedModules.isPresent()) {
        return revisionPinnedModules.get();
      }
      if (Boolean.TRUE.equals(course.getRevisioningEnabled())) {
        log.warn(
            "Blocking fallback to live module summaries for learner {} in revision-enabled course {}",
            actorId,
            courseId
        );
        return List.of();
      }
    }
    return moduleRepository.findByCourseIdOrderByOrderIndexAsc(courseId)
        .stream().map(moduleMapper::toSummaryDto).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<ModuleDetailDTO> listModulesWithContent(Long courseId, Long actorId) {
    Course course = getCourseOrThrow(courseId);
    ensureCanReadCourseStructure(course, actorId);
    if (!isAuthorOrAdmin(actorId, course.getAuthor().getId())) {
      Optional<List<ModuleDetailDTO>> revisionPinnedModules =
          revisionPinnedContentResolver.resolveModulesWithContent(course, actorId);
      if (revisionPinnedModules.isPresent()) {
        return revisionPinnedModules.get();
      }
      if (Boolean.TRUE.equals(course.getRevisioningEnabled())) {
        log.warn(
            "Blocking fallback to live module content for learner {} in revision-enabled course {}",
            actorId,
            courseId
        );
        return List.of();
      }
    }
    List<Module> modules = moduleRepository.findByCourseIdWithContent(courseId);
    for (Module module : modules) {
      module.getLessons().sort(Comparator
          .comparing(Lesson::getOrderIndex, Comparator.nullsLast(Integer::compareTo))
          .thenComparing(Lesson::getId, Comparator.nullsLast(Long::compareTo)));
      module.getQuizzes().sort(Comparator
          .comparing(Quiz::getOrderIndex, Comparator.nullsLast(Integer::compareTo))
          .thenComparing(Quiz::getId, Comparator.nullsLast(Long::compareTo)));
      module.setAssignments(new LinkedHashSet<>(module.getAssignments().stream()
          .sorted(Comparator
              .comparing(Assignment::getOrderIndex, Comparator.nullsLast(Integer::compareTo))
              .thenComparing(Assignment::getId, Comparator.nullsLast(Long::compareTo)))
          .toList()));
    }
    return modules.stream().map(moduleMapper::toDetailDto).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public ModuleDetailDTO getModuleDetail(Long moduleId, Long actorId) {
    Module module = getModuleOrThrow(moduleId);
    ensureCanReadCourseStructure(module.getCourse(), actorId);
    return moduleMapper.toDetailDto(module);
  }

  @Override
  @Transactional(readOnly = true)
  public ModuleProgressDTO getProgress(Long moduleId, Long actorId) {
    Module module = getModuleOrThrow(moduleId);
    ensureCanAccessLearningContent(module.getCourse(), actorId);
    long total = lessonRepository.countByModuleId(module.getId());
    long completed = lessonProgressRepository.countCompletedInModule(actorId, moduleId);
    int percent = total == 0 ? 0 : (int) Math.round((completed * 100.0) / total);
    return ModuleProgressDTO.builder()
        .completedLessons(completed)
        .totalLessons(total)
        .percent(percent)
        .build();
  }

  @Override
  @Transactional
  public void assignLesson(Long moduleId, Long lessonId, Long actorId) {
    Module module = getModuleOrThrow(moduleId);
    Lesson lesson = lessonRepository.findById(lessonId)
        .orElseThrow(() -> new NotFoundException("LESSON_NOT_FOUND"));
    ensureAuthorOrAdmin(actorId, module.getCourse().getAuthor().getId());
    if (!lesson.getModule().getCourse().getId().equals(module.getCourse().getId())) {
      throw new IllegalArgumentException("LESSON_NOT_IN_SAME_COURSE");
    }
    lesson.setModule(module);
    lessonRepository.save(lesson);
  }

  private Course getCourseOrThrow(Long id) {
    return courseRepository.findById(id).orElseThrow(() -> new NotFoundException("COURSE_NOT_FOUND"));
  }

  private Module getModuleOrThrow(Long id) {
    return moduleRepository.findById(id).orElseThrow(() -> new NotFoundException("MODULE_NOT_FOUND"));
  }

  private void ensureCanReadCourseStructure(Course course, Long actorId) {
    if (actorId == null) {
      throw new AccessDeniedException("UNAUTHORIZED");
    }
    if (isAuthorOrAdmin(actorId, course.getAuthor().getId())) {
      return;
    }
    if (course.getStatus() == CourseStatus.PUBLIC) {
      return;
    }
    ensureCanAccessLearningContent(course, actorId);
  }

  private void ensureCanAccessLearningContent(Course course, Long actorId) {
    if (actorId == null) {
      throw new AccessDeniedException("UNAUTHORIZED");
    }
    if (isAuthorOrAdmin(actorId, course.getAuthor().getId())) {
      return;
    }
    enrollmentRepository.findByCourseIdAndUserId(course.getId(), actorId)
        .filter(enrollment -> hasLearningAccess(enrollment.getStatus()))
        .orElseThrow(() -> new AccessDeniedException("USER_NOT_ENROLLED"));
  }

  private boolean hasLearningAccess(EnrollmentStatus status) {
    return status == EnrollmentStatus.ENROLLED || status == EnrollmentStatus.COMPLETED;
  }

  private boolean isAuthorOrAdmin(Long actorId, Long authorId) {
    if (actorId == null) {
      return false;
    }
    if (actorId.equals(authorId)) {
      return true;
    }

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return auth != null && auth.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .anyMatch(a -> a.equals("ROLE_ADMIN") || a.equals("ROLE_CONTENT_ADMIN"));
  }

  private void ensureAuthorOrAdmin(Long actorId, Long authorId) {
    if (!isAuthorOrAdmin(actorId, authorId)) {
      throw new AccessDeniedException("FORBIDDEN");
    }
  }
}
