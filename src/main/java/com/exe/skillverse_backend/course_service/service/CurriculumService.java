package com.exe.skillverse_backend.course_service.service;

import com.exe.skillverse_backend.course_service.dto.curriculumdto.CurriculumUpsertRequestDTO;
import com.exe.skillverse_backend.course_service.dto.curriculumdto.CurriculumUpsertResponseDTO;

public interface CurriculumService {
    CurriculumUpsertResponseDTO upsertCurriculum(Long courseId, CurriculumUpsertRequestDTO request, Long actorId);
}
