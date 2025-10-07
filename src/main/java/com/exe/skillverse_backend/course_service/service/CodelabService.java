package com.exe.skillverse_backend.course_service.service;

import com.exe.skillverse_backend.course_service.dto.codingdto.*;
import com.exe.skillverse_backend.shared.dto.PageResponse;
import org.springframework.data.domain.Pageable;

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