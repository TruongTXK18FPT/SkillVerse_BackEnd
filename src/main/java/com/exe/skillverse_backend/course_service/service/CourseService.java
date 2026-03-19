package com.exe.skillverse_backend.course_service.service;

import com.exe.skillverse_backend.course_service.dto.coursedto.CourseCreateDTO;
import com.exe.skillverse_backend.course_service.dto.coursedto.CourseDetailDTO;
import com.exe.skillverse_backend.course_service.dto.coursedto.CourseSummaryDTO;
import com.exe.skillverse_backend.course_service.dto.coursedto.CourseUpdateDTO;
import com.exe.skillverse_backend.course_service.entity.enums.CourseUpgradePolicy;
import com.exe.skillverse_backend.course_service.entity.enums.CourseStatus;
import com.exe.skillverse_backend.shared.dto.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

public interface CourseService {
    
    CourseDetailDTO createCourse(Long authorId, CourseCreateDTO dto, MultipartFile thumbnailFile);
    
    CourseDetailDTO updateCourse(Long courseId, CourseUpdateDTO dto, Long actorId, MultipartFile thumbnailFile);
    
    void deleteCourse(Long courseId, Long actorId);
    
    CourseDetailDTO getCourse(Long id, Long actorId);
    
    PageResponse<CourseSummaryDTO> listCourses(String q, CourseStatus status, Pageable p);
    
    PageResponse<CourseSummaryDTO> listCoursesByAuthor(Long authorId, Pageable pageable);
    
    // Course approval workflow methods
    CourseDetailDTO submitCourseForApproval(Long courseId, Long actorId);

    CourseDetailDTO approveCourse(Long courseId, Long adminId);

    CourseDetailDTO rejectCourse(Long courseId, Long adminId, String reason);

    /** Suspend a PUBLIC course due to violations (admin-only) */
    CourseDetailDTO suspendCourse(Long courseId, Long adminId, String reason);

    /** Restore a SUSPENDED course back to PUBLIC (admin-only) */
    CourseDetailDTO restoreCourse(Long courseId, Long adminId);

    CourseDetailDTO updateUpgradePolicy(Long courseId, CourseUpgradePolicy policy, Long actorId);

    PageResponse<CourseSummaryDTO> listCoursesByStatus(CourseStatus status, Pageable pageable);

    /** Get course counts grouped by status (for admin dashboard) */
    Map<String, Long> getCourseStats();
}
