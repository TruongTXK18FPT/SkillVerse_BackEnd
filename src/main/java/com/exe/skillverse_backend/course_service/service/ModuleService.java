package com.exe.skillverse_backend.course_service.service;

import java.util.List;
import com.exe.skillverse_backend.course_service.dto.moduledto.ModuleCreateDTO;
import com.exe.skillverse_backend.course_service.dto.moduledto.ModuleDetailDTO;
import com.exe.skillverse_backend.course_service.dto.moduledto.ModuleProgressDTO;
import com.exe.skillverse_backend.course_service.dto.moduledto.ModuleSummaryDTO;
import com.exe.skillverse_backend.course_service.dto.moduledto.ModuleUpdateDTO;

public interface ModuleService {
  ModuleDetailDTO createModule(Long courseId, ModuleCreateDTO dto, Long actorId);

  ModuleDetailDTO updateModule(Long moduleId, ModuleUpdateDTO dto, Long actorId);

  void deleteModule(Long moduleId, Long actorId);

  List<ModuleSummaryDTO> listModules(Long courseId);

  List<ModuleDetailDTO> listModulesWithContent(Long courseId);

  ModuleDetailDTO getModuleDetail(Long moduleId);

  void assignLesson(Long moduleId, Long lessonId, Long actorId);

  ModuleProgressDTO getProgress(Long moduleId, Long userId);
}
