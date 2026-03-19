package com.exe.skillverse_backend.course_service.service;

import com.exe.skillverse_backend.course_service.dto.progressdto.CourseLearningStatusDTO;
import com.exe.skillverse_backend.course_service.dto.progressdto.CourseLearningRevisionInfoDTO;

public interface CourseLearningProgressService {

    CourseLearningStatusDTO getCourseLearningStatus(Long courseId, Long userId);

    CourseLearningRevisionInfoDTO getLearningRevisionInfo(Long courseId, Long userId);

    CourseLearningRevisionInfoDTO upgradeToActiveRevision(Long courseId, Long userId);

    int recalculateCourseProgress(Long courseId, Long userId);
}
