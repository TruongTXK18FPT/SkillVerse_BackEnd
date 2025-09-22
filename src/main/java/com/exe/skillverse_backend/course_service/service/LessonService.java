package com.exe.skillverse_backend.course_service.service;

import com.exe.skillverse_backend.course_service.dto.lessondto.LessonBriefDTO;
import com.exe.skillverse_backend.course_service.dto.lessondto.LessonCreateDTO;
import com.exe.skillverse_backend.course_service.dto.lessondto.LessonUpdateDTO;

import java.util.List;

public interface LessonService {
    
    LessonBriefDTO addLesson(Long courseId, LessonCreateDTO dto, Long actorId);
    
    LessonBriefDTO updateLesson(Long lessonId, LessonUpdateDTO dto, Long actorId);
    
    void deleteLesson(Long lessonId, Long actorId);
    
    List<LessonBriefDTO> listLessonsByCourse(Long courseId);
}