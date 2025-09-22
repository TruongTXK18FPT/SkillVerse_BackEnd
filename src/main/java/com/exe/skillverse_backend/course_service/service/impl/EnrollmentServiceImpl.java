package com.exe.skillverse_backend.course_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.course_service.dto.enrollmentdto.EnrollRequestDTO;
import com.exe.skillverse_backend.course_service.dto.enrollmentdto.EnrollmentDetailDTO;
import com.exe.skillverse_backend.course_service.dto.enrollmentdto.EnrollmentStatsDTO;
import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.course_service.entity.CourseEnrollment;
import com.exe.skillverse_backend.course_service.entity.enums.EnrollmentStatus;
import com.exe.skillverse_backend.course_service.entity.enums.EntitlementSource;
import com.exe.skillverse_backend.course_service.mapper.CourseEnrollmentMapper;
import com.exe.skillverse_backend.course_service.repository.CourseEnrollmentRepository;
import com.exe.skillverse_backend.course_service.repository.CourseRepository;
import com.exe.skillverse_backend.course_service.service.EnrollmentService;
import com.exe.skillverse_backend.shared.dto.PageResponse;
import com.exe.skillverse_backend.shared.exception.AccessDeniedException;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnrollmentServiceImpl implements EnrollmentService {

    private final CourseEnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;
    private final CourseEnrollmentMapper enrollmentMapper;

    @Override
    @Transactional
    public EnrollmentDetailDTO enrollUser(EnrollRequestDTO dto, Long userId) {
        log.info("Enrolling user {} in course {}", userId, dto.getCourseId());
        
        Course course = getCourseOrThrow(dto.getCourseId());
        
        // Check if already enrolled
        Optional<CourseEnrollment> existingEnrollment = enrollmentRepository
                .findByCourseIdAndUserId(dto.getCourseId(), userId);
        
        if (existingEnrollment.isPresent()) {
            CourseEnrollment enrollment = existingEnrollment.get();
            if (enrollment.getStatus() == EnrollmentStatus.ENROLLED) {
                throw new IllegalArgumentException("USER_ALREADY_ENROLLED");
            }
            // Reactivate if previously dropped
            enrollment.setStatus(EnrollmentStatus.ENROLLED);
            enrollment.setProgressPercent(0);
            
            CourseEnrollment saved = enrollmentRepository.save(enrollment);
            log.info("User {} re-enrolled in course {}", userId, dto.getCourseId());
            return enrollmentMapper.toDetailDto(saved);
        }
        
        // Create new enrollment - need to create the composite ID
        User user = User.builder().id(userId).build(); // Placeholder for AuthService integration
        
        CourseEnrollment enrollment = CourseEnrollment.builder()
                .user(user)
                .course(course)
                .status(EnrollmentStatus.ENROLLED)
                .progressPercent(0)
                .entitlementSource(EntitlementSource.PURCHASE)
                .build();
        
        CourseEnrollment saved = enrollmentRepository.save(enrollment);
        log.info("User {} enrolled in course {}", userId, dto.getCourseId());
        
        return enrollmentMapper.toDetailDto(saved);
    }

    @Override
    @Transactional
    public void unenrollUser(Long courseId, Long userId) {
        log.info("Unenrolling user {} from course {}", userId, courseId);
        
        CourseEnrollment enrollment = getEnrollmentOrThrow(courseId, userId);
        
        // Soft delete by setting status to DROPPED
        enrollment.setStatus(EnrollmentStatus.DROPPED);
        enrollmentRepository.save(enrollment);
        
        log.info("User {} unenrolled from course {}", userId, courseId);
    }

    @Override
    @Transactional(readOnly = true)
    public EnrollmentDetailDTO getEnrollment(Long courseId, Long userId) {
        log.debug("Getting enrollment for user {} in course {}", userId, courseId);
        
        CourseEnrollment enrollment = getEnrollmentOrThrow(courseId, userId);
        return enrollmentMapper.toDetailDto(enrollment);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isUserEnrolled(Long courseId, Long userId) {
        return enrollmentRepository.findByCourseIdAndUserId(courseId, userId)
                .map(enrollment -> enrollment.getStatus() == EnrollmentStatus.ENROLLED)
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<EnrollmentDetailDTO> getUserEnrollments(Long userId, Pageable pageable) {
        log.debug("Getting enrollments for user {} with page {}", userId, pageable.getPageNumber());
        
        Page<CourseEnrollment> enrollments = enrollmentRepository
                .findByUserId(userId, pageable);
        
        return PageResponse.<EnrollmentDetailDTO>builder()
                .items(enrollments.map(enrollmentMapper::toDetailDto).toList())
                .page(enrollments.getNumber())
                .size(enrollments.getSize())
                .total(enrollments.getTotalElements())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<EnrollmentDetailDTO> getCourseEnrollments(Long courseId, Pageable pageable, Long actorId) {
        log.debug("Getting enrollments for course {} by actor {}", courseId, actorId);
        
        Course course = getCourseOrThrow(courseId);
        ensureAuthorOrAdmin(actorId, course.getAuthor().getId());
        
        Page<CourseEnrollment> enrollments = enrollmentRepository
                .findByCourseId(courseId, pageable);
        
        return PageResponse.<EnrollmentDetailDTO>builder()
                .items(enrollments.map(enrollmentMapper::toDetailDto).toList())
                .page(enrollments.getNumber())
                .size(enrollments.getSize())
                .total(enrollments.getTotalElements())
                .build();
    }

    @Override
    @Transactional
    public void updateCompletionStatus(Long courseId, Long userId, boolean completed) {
        log.info("Updating completion status for user {} in course {} to {}", userId, courseId, completed);
        
        CourseEnrollment enrollment = getEnrollmentOrThrow(courseId, userId);
        
        if (completed) {
            enrollment.setStatus(EnrollmentStatus.COMPLETED);
            enrollment.setProgressPercent(100);
        } else {
            enrollment.setStatus(EnrollmentStatus.ENROLLED);
        }
        
        enrollmentRepository.save(enrollment);
        log.info("Completion status updated for user {} in course {}", userId, courseId);
    }

    @Override
    @Transactional
    public void updateProgress(Long courseId, Long userId, Integer progressPercentage) {
        log.debug("Updating progress for user {} in course {} to {}%", userId, courseId, progressPercentage);
        
        if (progressPercentage < 0 || progressPercentage > 100) {
            throw new IllegalArgumentException("Progress percentage must be between 0 and 100");
        }
        
        CourseEnrollment enrollment = getEnrollmentOrThrow(courseId, userId);
        enrollment.setProgressPercent(progressPercentage);
        
        // Auto-complete if 100%
        if (progressPercentage == 100 && enrollment.getStatus() == EnrollmentStatus.ENROLLED) {
            enrollment.setStatus(EnrollmentStatus.COMPLETED);
        }
        
        enrollmentRepository.save(enrollment);
    }

    @Override
    @Transactional(readOnly = true)
    public EnrollmentStatsDTO getEnrollmentStats(Long courseId, Long actorId) {
        log.debug("Getting enrollment stats for course {} by actor {}", courseId, actorId);
        
        Course course = getCourseOrThrow(courseId);
        ensureAuthorOrAdmin(actorId, course.getAuthor().getId());
        
        long totalEnrollments = enrollmentRepository.countByCourseId(courseId);
        long activeEnrollments = enrollmentRepository.countActiveEnrollmentsByCourseId(courseId);
        long completedEnrollments = enrollmentRepository.findByCourseId(courseId, Pageable.unpaged())
                .stream()
                .mapToLong(e -> e.getStatus() == EnrollmentStatus.COMPLETED ? 1 : 0)
                .sum();
        
        Double completionRate = activeEnrollments > 0 ? 
                (double) completedEnrollments / totalEnrollments * 100 : 0.0;
        
        // Calculate average progress (simplified)
        Double averageProgress = enrollmentRepository.findByCourseId(courseId, Pageable.unpaged())
                .stream()
                .mapToInt(CourseEnrollment::getProgressPercent)
                .average()
                .orElse(0.0);
        
        // Monthly stats (simplified - using current month)
        long enrollmentsThisMonth = totalEnrollments; // Placeholder
        long completionsThisMonth = completedEnrollments; // Placeholder
        
        return EnrollmentStatsDTO.builder()
                .courseId(courseId)
                .courseTitle(course.getTitle())
                .totalEnrollments(totalEnrollments)
                .activeEnrollments(activeEnrollments)
                .completedEnrollments(completedEnrollments)
                .completionRate(completionRate)
                .averageProgress(averageProgress)
                .enrollmentsThisMonth(enrollmentsThisMonth)
                .completionsThisMonth(completionsThisMonth)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<EnrollmentDetailDTO> getRecentEnrollments(Pageable pageable, Long actorId) {
        log.debug("Getting recent enrollments by admin {}", actorId);
        
        // Admin check will be enhanced with role-based access control
        ensureAdmin(actorId);
        
        Page<CourseEnrollment> enrollments = enrollmentRepository
                .findByStatus(EnrollmentStatus.ENROLLED, pageable);
        
        return PageResponse.<EnrollmentDetailDTO>builder()
                .items(enrollments.map(enrollmentMapper::toDetailDto).toList())
                .page(enrollments.getNumber())
                .size(enrollments.getSize())
                .total(enrollments.getTotalElements())
                .build();
    }

    // ===== Helper Methods =====
    
    private Course getCourseOrThrow(Long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new NotFoundException("COURSE_NOT_FOUND"));
    }
    
    private CourseEnrollment getEnrollmentOrThrow(Long courseId, Long userId) {
        return enrollmentRepository.findByCourseIdAndUserId(courseId, userId)
                .orElseThrow(() -> new NotFoundException("ENROLLMENT_NOT_FOUND"));
    }

    private void ensureAuthorOrAdmin(Long actorId, Long authorId) {
        // Auth/Role service integration will be implemented for admin checking
        if (!actorId.equals(authorId)) {
            // Role checking via AuthService will be integrated here
            throw new AccessDeniedException("FORBIDDEN");
        }
    }
    
    private void ensureAdmin(Long actorId) {
        // Admin role checking will be enhanced with AuthService integration
        // For now, allowing all users (placeholder implementation)
        log.debug("Admin check for user {} - placeholder implementation", actorId);
    }
}