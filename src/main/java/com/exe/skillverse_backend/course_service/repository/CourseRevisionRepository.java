package com.exe.skillverse_backend.course_service.repository;

import com.exe.skillverse_backend.course_service.entity.CourseRevision;
import com.exe.skillverse_backend.course_service.entity.enums.CourseRevisionStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface CourseRevisionRepository extends JpaRepository<CourseRevision, Long> {

    @Transactional(readOnly = true)
    Optional<CourseRevision> findTopByCourseIdOrderByRevisionNumberDesc(Long courseId);

    @Transactional(readOnly = true)
    Optional<CourseRevision> findTopByCourseIdAndStatusOrderByRevisionNumberDesc(
            Long courseId,
            CourseRevisionStatus status
    );

    @Transactional(readOnly = true)
    boolean existsByCourseIdAndStatusIn(Long courseId, Collection<CourseRevisionStatus> statuses);

    @Transactional(readOnly = true)
    Optional<CourseRevision> findByCourseIdAndRevisionNumber(Long courseId, Integer revisionNumber);

    @Transactional(readOnly = true)
    Optional<CourseRevision> findByIdAndCourse_Id(Long id, Long courseId);

    @Transactional(readOnly = true)
    Page<CourseRevision> findByCourseId(Long courseId, Pageable pageable);

    @Transactional(readOnly = true)
    Page<CourseRevision> findByCourseIdAndStatus(Long courseId, CourseRevisionStatus status, Pageable pageable);

    @Transactional(readOnly = true)
    Page<CourseRevision> findByStatus(CourseRevisionStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT cr FROM CourseRevision cr WHERE cr.id = :id")
    Optional<CourseRevision> findByIdForApproval(@Param("id") Long id);
}
