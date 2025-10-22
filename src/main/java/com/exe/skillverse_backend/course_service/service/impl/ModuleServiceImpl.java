package com.exe.skillverse_backend.course_service.service.impl;

import com.exe.skillverse_backend.course_service.dto.moduledto.*;
import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.course_service.entity.Module;
import com.exe.skillverse_backend.course_service.entity.Lesson;
import com.exe.skillverse_backend.course_service.mapper.ModuleMapper;
import com.exe.skillverse_backend.course_service.repository.CourseRepository;
import com.exe.skillverse_backend.course_service.repository.ModuleRepository;
import com.exe.skillverse_backend.course_service.service.ModuleService;
import com.exe.skillverse_backend.shared.exception.AccessDeniedException;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModuleServiceImpl implements ModuleService {

  private final ModuleRepository moduleRepository;
  private final CourseRepository courseRepository;
  private final ModuleMapper moduleMapper;
  private final com.exe.skillverse_backend.course_service.repository.LessonRepository lessonRepository;
  private final com.exe.skillverse_backend.course_service.repository.LessonProgressRepository lessonProgressRepository;

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
  public List<ModuleSummaryDTO> listModules(Long courseId) {
    getCourseOrThrow(courseId);
    return moduleRepository.findByCourseIdOrderByOrderIndexAsc(courseId)
        .stream().map(moduleMapper::toSummaryDto).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public ModuleProgressDTO getProgress(Long moduleId, Long userId) {
    Module module = getModuleOrThrow(moduleId);
    long total = lessonRepository.countByModuleId(module.getId());
    long completed = lessonProgressRepository.countCompletedInModule(userId, moduleId);
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

  private void ensureAuthorOrAdmin(Long actorId, Long authorId) {
    if (!actorId.equals(authorId)) {
      throw new AccessDeniedException("FORBIDDEN");
    }
  }
}
