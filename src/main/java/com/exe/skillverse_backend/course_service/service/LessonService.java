package com.exe.skillverse_backend.course_service.service;

import com.exe.skillverse_backend.course_service.dto.lessondto.LessonBriefDTO;
import com.exe.skillverse_backend.course_service.dto.lessondto.LessonDetailDTO;
import com.exe.skillverse_backend.course_service.dto.lessondto.LessonCreateDTO;
import com.exe.skillverse_backend.course_service.dto.lessondto.LessonUpdateDTO;

import java.util.List;

public interface LessonService {

    LessonBriefDTO addLesson(Long moduleId, LessonCreateDTO dto, Long actorId);

    LessonBriefDTO updateLesson(Long lessonId, LessonUpdateDTO dto, Long actorId);

    void deleteLesson(Long lessonId, Long actorId);

    List<LessonBriefDTO> listLessonsByModule(Long moduleId);

    LessonDetailDTO getLesson(Long lessonId);

    // Navigation
    LessonBriefDTO getNextLesson(Long moduleId, Long currentLessonId);

    LessonBriefDTO getPreviousLesson(Long moduleId, Long currentLessonId);

    // Progress
    void markLessonCompleted(Long moduleId, Long lessonId, Long userId);
}