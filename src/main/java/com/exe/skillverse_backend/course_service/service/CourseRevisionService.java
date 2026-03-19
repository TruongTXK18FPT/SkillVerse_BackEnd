package com.exe.skillverse_backend.course_service.service;

import com.exe.skillverse_backend.course_service.dto.coursedto.CourseRevisionDTO;
import com.exe.skillverse_backend.course_service.dto.coursedto.CourseRevisionUpdateDTO;
import com.exe.skillverse_backend.course_service.entity.enums.CourseRevisionStatus;
import com.exe.skillverse_backend.shared.dto.PageResponse;
import org.springframework.data.domain.Pageable;

public interface CourseRevisionService {

    CourseRevisionDTO createRevision(Long courseId, Long actorId);

    CourseRevisionDTO getRevision(Long revisionId, Long actorId);

    PageResponse<CourseRevisionDTO> listCourseRevisions(
            Long courseId,
            Long actorId,
            CourseRevisionStatus status,
            Pageable pageable
    );

    PageResponse<CourseRevisionDTO> listAdminRevisions(CourseRevisionStatus status, Pageable pageable);

    CourseRevisionDTO submitRevision(Long revisionId, Long actorId);

    CourseRevisionDTO updateRevision(Long revisionId, CourseRevisionUpdateDTO dto, Long actorId);

    CourseRevisionDTO approveRevision(Long revisionId, Long adminId);

    CourseRevisionDTO rejectRevision(Long revisionId, Long adminId, String reason);
}
