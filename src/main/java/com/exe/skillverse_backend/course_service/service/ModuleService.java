package com.exe.skillverse_backend.course_service.service;

import com.exe.skillverse_backend.course_service.dto.moduledto.*;
import java.util.List;

public interface ModuleService {
  ModuleDetailDTO createModule(Long courseId, ModuleCreateDTO dto, Long actorId);

  ModuleDetailDTO updateModule(Long moduleId, ModuleUpdateDTO dto, Long actorId);

  void deleteModule(Long moduleId, Long actorId);

  List<ModuleSummaryDTO> listModules(Long courseId);

  ModuleDetailDTO getModuleDetail(Long moduleId);

  void assignLesson(Long moduleId, Long lessonId, Long actorId);

  ModuleProgressDTO getProgress(Long moduleId, Long userId);
}
