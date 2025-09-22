package com.exe.skillverse_backend.course_service.service;

import com.exe.skillverse_backend.course_service.dto.coursedto.CourseCreateDTO;
import com.exe.skillverse_backend.course_service.dto.coursedto.CourseDetailDTO;
import com.exe.skillverse_backend.course_service.dto.coursedto.CourseSummaryDTO;
import com.exe.skillverse_backend.course_service.dto.coursedto.CourseUpdateDTO;
import com.exe.skillverse_backend.course_service.entity.enums.CourseStatus;
import com.exe.skillverse_backend.shared.dto.PageResponse;
import org.springframework.data.domain.Pageable;

public interface CourseService {
    
    CourseDetailDTO createCourse(Long authorId, CourseCreateDTO dto);
    
    CourseDetailDTO updateCourse(Long courseId, CourseUpdateDTO dto, Long actorId);
    
    void deleteCourse(Long courseId, Long actorId);
    
    CourseDetailDTO getCourse(Long id);
    
    PageResponse<CourseSummaryDTO> listCourses(String q, CourseStatus status, Pageable p);
}