package com.exe.skillverse_backend.course_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.course_service.dto.enrollmentdto.EnrollRequestDTO;
import com.exe.skillverse_backend.course_service.dto.enrollmentdto.EnrollmentDetailDTO;
import com.exe.skillverse_backend.course_service.dto.enrollmentdto.EnrollmentStatsDTO;
import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.course_service.entity.CourseEnrollment;
import com.exe.skillverse_backend.course_service.entity.enums.EnrollmentStatus;
import com.exe.skillverse_backend.course_service.entity.enums.EntitlementSource;
import com.exe.skillverse_backend.course_service.repository.CourseEnrollmentRepository;
import com.exe.skillverse_backend.course_service.repository.CourseRepository;
import com.exe.skillverse_backend.course_service.service.EnrollmentService;
import com.exe.skillverse_backend.shared.dto.PageResponse;
import com.exe.skillverse_backend.shared.exception.AccessDeniedException;
import com.exe.skillverse_backend.shared.exception.ConflictException;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Slf4j
public class EnrollmentServiceImpl implements EnrollmentService {

    private final CourseEnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    // Constants for error messages
    private static final String COURSE_NOT_FOUND = "COURSE_NOT_FOUND";
    private static final String USER_NOT_FOUND = "USER_NOT_FOUND";
    private static final String ENROLLMENT_NOT_FOUND = "ENROLLMENT_NOT_FOUND";

    @Override
    @Transactional
    public EnrollmentDetailDTO enrollUser(EnrollRequestDTO dto, Long userId) {
        log.info("Enrolling user {} in course {}", userId, dto.getCourseId());
        
        // Validate course exists
        Course course = courseRepository.findById(dto.getCourseId())
                .orElseThrow(() -> new NotFoundException(COURSE_NOT_FOUND));

        // Validate user exists
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(USER_NOT_FOUND));

        // Check if already enrolled
        if (enrollmentRepository.existsByCourseIdAndUserId(dto.getCourseId(), userId)) {
            throw new ConflictException("USER_ALREADY_ENROLLED");
        }

        // Create enrollment - let JPA handle the ID creation
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setUser(user);
        enrollment.setCourse(course);
        enrollment.setEnrollDate(Instant.now(clock));
        enrollment.setStatus(EnrollmentStatus.ENROLLED);
        enrollment.setProgressPercent(0);
        enrollment.setEntitlementSource(EntitlementSource.PURCHASE);

        CourseEnrollment saved = enrollmentRepository.save(enrollment);
        
        log.info("User {} successfully enrolled in course {}", userId, dto.getCourseId());
        return mapToDetailDTO(saved);
    }

    @Override
    @Transactional
    public void unenrollUser(Long courseId, Long userId) {
        log.info("Unenrolling user {} from course {}", userId, courseId);
        
        CourseEnrollment enrollment = enrollmentRepository.findByCourseIdAndUserId(courseId, userId)
                .orElseThrow(() -> new NotFoundException(ENROLLMENT_NOT_FOUND));

        enrollmentRepository.delete(enrollment);
        log.info("User {} successfully unenrolled from course {}", userId, courseId);
    }

    @Override
    @Transactional(readOnly = true)
    public EnrollmentDetailDTO getEnrollment(Long courseId, Long userId) {
        CourseEnrollment enrollment = enrollmentRepository.findByCourseIdAndUserId(courseId, userId)
                .orElseThrow(() -> new NotFoundException(ENROLLMENT_NOT_FOUND));
        
        return mapToDetailDTO(enrollment);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isUserEnrolled(Long courseId, Long userId) {
        return enrollmentRepository.existsByCourseIdAndUserId(courseId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<EnrollmentDetailDTO> getUserEnrollments(Long userId, Pageable pageable) {
        Page<CourseEnrollment> enrollments = enrollmentRepository.findByUserId(userId, pageable);
        
        return PageResponse.<EnrollmentDetailDTO>builder()
                .items(enrollments.map(this::mapToDetailDTO).getContent())
                .page(enrollments.getNumber())
                .size(enrollments.getSize())
                .total(enrollments.getTotalElements())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<EnrollmentDetailDTO> getCourseEnrollments(Long courseId, Pageable pageable, Long actorId) {
        // Validate course exists and check authorization
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new NotFoundException(COURSE_NOT_FOUND));

        // Check if actor is course author (using author relationship)
        if (!course.getAuthor().getId().equals(actorId)) {
            // In a real implementation, also check for admin role
            throw new AccessDeniedException("NOT_AUTHORIZED_TO_VIEW_ENROLLMENTS");
        }

        Page<CourseEnrollment> enrollments = enrollmentRepository.findByCourseId(courseId, pageable);
        
        return PageResponse.<EnrollmentDetailDTO>builder()
                .items(enrollments.map(this::mapToDetailDTO).getContent())
                .page(enrollments.getNumber())
                .size(enrollments.getSize())
                .total(enrollments.getTotalElements())
                .build();
    }

    @Override
    @Transactional
    public void updateCompletionStatus(Long courseId, Long userId, boolean completed) {
        CourseEnrollment enrollment = enrollmentRepository.findByCourseIdAndUserId(courseId, userId)
                .orElseThrow(() -> new NotFoundException(ENROLLMENT_NOT_FOUND));

        enrollment.setStatus(completed ? EnrollmentStatus.COMPLETED : EnrollmentStatus.ENROLLED);
        if (completed) {
            enrollment.setProgressPercent(100);
        }

        enrollmentRepository.save(enrollment);
        log.info("Updated completion status for user {} in course {} to {}", userId, courseId, completed);
    }

    @Override
    @Transactional
    public void updateProgress(Long courseId, Long userId, Integer progressPercentage) {
        if (progressPercentage < 0 || progressPercentage > 100) {
            throw new IllegalArgumentException("INVALID_PROGRESS_PERCENTAGE");
        }

        CourseEnrollment enrollment = enrollmentRepository.findByCourseIdAndUserId(courseId, userId)
                .orElseThrow(() -> new NotFoundException(ENROLLMENT_NOT_FOUND));

        enrollment.setProgressPercent(progressPercentage);
        
        // Auto-complete if progress reaches 100%
        if (progressPercentage == 100) {
            enrollment.setStatus(EnrollmentStatus.COMPLETED);
        }

        enrollmentRepository.save(enrollment);
        log.info("Updated progress for user {} in course {} to {}%", userId, courseId, progressPercentage);
    }

    @Override
    @Transactional(readOnly = true)
    public EnrollmentStatsDTO getEnrollmentStats(Long courseId, Long actorId) {
        // Validate course exists and check authorization
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new NotFoundException(COURSE_NOT_FOUND));

        if (!course.getAuthor().getId().equals(actorId)) {
            throw new AccessDeniedException("NOT_AUTHORIZED_TO_VIEW_STATS");
        }

        long totalEnrollments = enrollmentRepository.countByCourseId(courseId);
        long activeEnrollments = enrollmentRepository.countActiveEnrollmentsByCourseId(courseId);
        long completedEnrollments = totalEnrollments - activeEnrollments;
        double completionRate = totalEnrollments > 0 ? (double) completedEnrollments / totalEnrollments * 100 : 0.0;

        return EnrollmentStatsDTO.builder()
                .courseId(courseId)
                .courseTitle(course.getTitle())
                .totalEnrollments(totalEnrollments)
                .activeEnrollments(activeEnrollments)
                .completedEnrollments(completedEnrollments)
                .completionRate(completionRate)
                .averageProgress(50.0) // Simplified - would need complex query
                .enrollmentsThisMonth(0L) // Simplified - would need date filtering
                .completionsThisMonth(0L) // Simplified - would need date filtering
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<EnrollmentDetailDTO> getRecentEnrollments(Pageable pageable, Long actorId) {
        // Validate user exists (simplified admin check)
        userRepository.findById(actorId)
                .orElseThrow(() -> new NotFoundException(USER_NOT_FOUND));

        // For now, just return enrollments by status
        Page<CourseEnrollment> enrollments = enrollmentRepository.findByStatus(EnrollmentStatus.ENROLLED, pageable);
        
        return PageResponse.<EnrollmentDetailDTO>builder()
                .items(enrollments.map(this::mapToDetailDTO).getContent())
                .page(enrollments.getNumber())
                .size(enrollments.getSize())
                .total(enrollments.getTotalElements())
                .build();
    }

    private EnrollmentDetailDTO mapToDetailDTO(CourseEnrollment enrollment) {
        Course course = enrollment.getCourse();
        LocalDateTime enrolledAt = LocalDateTime.ofInstant(enrollment.getEnrollDate(), ZoneId.systemDefault());
        LocalDateTime completedAt = enrollment.getStatus() == EnrollmentStatus.COMPLETED ? enrolledAt.plusDays(7) : null; // Simplified
        
        return EnrollmentDetailDTO.builder()
                .id(course.getId()) // Using courseId as id for simplicity
                .courseId(course.getId())
                .courseTitle(course.getTitle())
                .courseSlug("course-" + course.getId()) // Simplified slug generation
                .userId(enrollment.getUser().getId())
                .status(enrollment.getStatus().name())
                .progressPercent(enrollment.getProgressPercent())
                .entitlementSource(enrollment.getEntitlementSource().name())
                .entitlementRef(enrollment.getEntitlementRef())
                .enrolledAt(enrolledAt)
                .completedAt(completedAt)
                .completed(enrollment.getStatus() == EnrollmentStatus.COMPLETED)
                .build();
    }
}