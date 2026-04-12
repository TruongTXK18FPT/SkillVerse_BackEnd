package com.exe.skillverse_backend.course_service.service;

import com.exe.skillverse_backend.shared.dto.PageResponse;
import org.springframework.data.domain.Pageable;
import com.exe.skillverse_backend.course_service.dto.codingdto.CodingExerciseCreateDTO;
import com.exe.skillverse_backend.course_service.dto.codingdto.CodingExerciseDetailDTO;
import com.exe.skillverse_backend.course_service.dto.codingdto.CodingExerciseUpdateDTO;
import com.exe.skillverse_backend.course_service.dto.codingdto.CodingSubmissionCreateDTO;
import com.exe.skillverse_backend.course_service.dto.codingdto.CodingSubmissionDetailDTO;
import com.exe.skillverse_backend.course_service.dto.codingdto.CodingTestCaseCreateDTO;
import com.exe.skillverse_backend.course_service.dto.codingdto.CodingTestCaseDTO;
import com.exe.skillverse_backend.course_service.dto.codingdto.CodingTestCaseUpdateDTO;

/**
 * CODELAB_LEGACY: This feature is deprecated.
 * No code execution engine is integrated — submissions are stored as QUEUED and never evaluated.
 * @deprecated since 2026-04-08 — will be removed in a future release
 */
public interface CodelabService {
    
    CodingExerciseDetailDTO createExercise(Long moduleId, CodingExerciseCreateDTO dto, Long actorId);
    
    CodingExerciseDetailDTO updateExercise(Long exerciseId, CodingExerciseUpdateDTO dto, Long actorId);
    
    void deleteExercise(Long exerciseId, Long actorId);

    CodingTestCaseDTO addTestCase(Long exerciseId, CodingTestCaseCreateDTO dto, Long actorId);
    
    CodingTestCaseDTO updateTestCase(Long testCaseId, CodingTestCaseUpdateDTO dto, Long actorId);
    
    void deleteTestCase(Long testCaseId, Long actorId);

    CodingSubmissionDetailDTO submit(Long exerciseId, Long userId, CodingSubmissionCreateDTO dto);
    
    PageResponse<CodingSubmissionDetailDTO> listSubmissions(Long exerciseId, Pageable p);
}
