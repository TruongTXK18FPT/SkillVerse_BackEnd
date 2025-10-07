package com.exe.skillverse_backend.course_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.course_service.dto.coursedto.CourseCreateDTO;
import com.exe.skillverse_backend.course_service.dto.coursedto.CourseDetailDTO;
import com.exe.skillverse_backend.course_service.dto.coursedto.CourseSummaryDTO;
import com.exe.skillverse_backend.course_service.dto.coursedto.CourseUpdateDTO;
import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.course_service.entity.enums.CourseStatus;
import com.exe.skillverse_backend.course_service.mapper.CourseMapper;
import com.exe.skillverse_backend.course_service.repository.CourseRepository;
import com.exe.skillverse_backend.course_service.repository.CourseEnrollmentRepository;
import com.exe.skillverse_backend.course_service.repository.CoursePurchaseRepository;
import com.exe.skillverse_backend.course_service.service.CourseService;
import com.exe.skillverse_backend.shared.dto.PageResponse;
import com.exe.skillverse_backend.shared.entity.Media;
import com.exe.skillverse_backend.shared.exception.AccessDeniedException;
import com.exe.skillverse_backend.shared.exception.ConflictException;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import com.exe.skillverse_backend.shared.repository.MediaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseServiceImpl implements CourseService {

    private final CourseRepository courseRepository;
    private final CourseEnrollmentRepository enrollmentRepository;
    private final CoursePurchaseRepository purchaseRepository;
    private final MediaRepository mediaRepository;
    private final CourseMapper courseMapper;
    private final Clock clock;
    // TODO: inject AuthService for user validation
    // TODO: inject ApplicationEventPublisher for events

    @Override
    @Transactional
    public CourseDetailDTO createCourse(Long authorId, CourseCreateDTO dto) {
        log.info("Creating course with title '{}' by author {}", dto.getTitle(), authorId);
        
        validateCreateCourseRequest(dto);

        User author = User.builder().id(authorId).build(); // Placeholder for now
        
        // Load thumbnail media if provided
        Media thumbnail = null;
        if (dto.getThumbnailMediaId() != null) {
            log.info("Loading thumbnail media with ID: {}", dto.getThumbnailMediaId());
            thumbnail = mediaRepository.findByIdWithUser(dto.getThumbnailMediaId());
            if (thumbnail == null) {
                throw new NotFoundException("MEDIA_NOT_FOUND");
            }
            log.info("Loaded thumbnail media: {} - {}", thumbnail.getId(), thumbnail.getUrl());
        }
        
        Course entity = courseMapper.toEntity(dto, author, thumbnail);
        entity.setStatus(CourseStatus.DRAFT);
        entity.setCreatedAt(now());
        entity.setUpdatedAt(now());
        
        Course saved = courseRepository.save(entity);
        log.info("Course created with id={} by author={}", saved.getId(), authorId);
        
        return courseMapper.toDetailDto(saved);
    }

    @Override
    @Transactional
    public CourseDetailDTO updateCourse(Long courseId, CourseUpdateDTO dto, Long actorId) {
        log.info("Updating course {} by actor {}", courseId, actorId);
        
        Course course = getCourseOrThrow(courseId);
        ensureAuthorOrAdmin(actorId, course.getAuthor().getId());
        
        // Policy: only allow updates when DRAFT (or create versioning)
        if (course.getStatus() != CourseStatus.DRAFT) {
            throw new ConflictException("COURSE_NOT_EDITABLE_IN_STATUS_" + course.getStatus());
        }
        
        validateUpdateCourseRequest(dto);
        
        // Load new thumbnail if provided
        Media thumbnail = course.getThumbnail(); // Keep existing thumbnail by default
        if (dto.getThumbnailMediaId() != null) {
            log.info("Loading thumbnail media with ID: {}", dto.getThumbnailMediaId());
            thumbnail = mediaRepository.findByIdWithUser(dto.getThumbnailMediaId());
            if (thumbnail == null) {
                throw new NotFoundException("MEDIA_NOT_FOUND");
            }
            log.info("Loaded thumbnail media: {} - {}", thumbnail.getId(), thumbnail.getUrl());
        }
        
        courseMapper.updateEntity(course, dto, thumbnail);
        course.setUpdatedAt(now());
        
        Course saved = courseRepository.save(course);
        log.info("Course {} updated by actor {}", courseId, actorId);
        
        return courseMapper.toDetailDto(saved);
    }

    @Override
    @Transactional
    public void deleteCourse(Long courseId, Long actorId) {
        log.info("Deleting course {} by actor {}", courseId, actorId);
        
        Course course = getCourseOrThrow(courseId);
        ensureAuthorOrAdmin(actorId, course.getAuthor().getId());
        
        // Check if course has enrollments/purchases
        long enrollmentCount = enrollmentRepository.countByCourseId(courseId);
        long purchaseCount = purchaseRepository.countSuccessfulPurchasesByCourseId(courseId);
        
        if (enrollmentCount > 0 || purchaseCount > 0) {
            log.warn("Course {} has {} enrollments and {} purchases, marking as ARCHIVED", 
                    courseId, enrollmentCount, purchaseCount);
            course.setStatus(CourseStatus.ARCHIVED);
            course.setUpdatedAt(now());
            courseRepository.save(course);
        } else {
            courseRepository.delete(course);
        }
        
        log.info("Course {} deleted/archived by actor {}", courseId, actorId);
    }

    @Override
    @Transactional(readOnly = true)
    public CourseDetailDTO getCourse(Long id) {
        log.debug("Fetching course details for id {}", id);
        Course course = courseRepository.findByIdWithAuthorAndModules(id);
        if (course == null) {
            throw new NotFoundException("COURSE_NOT_FOUND");
        }
        return courseMapper.toDetailDto(course);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CourseSummaryDTO> listCourses(String q, CourseStatus status, Pageable pageable) {
        log.debug("Listing courses with query '{}', status '{}', page {}", q, status, pageable.getPageNumber());
        
        Page<Course> page;
        
        if (q != null && !q.isBlank()) {
            if (status != null) {
                page = courseRepository.findByStatusAndTitleContainingIgnoreCase(status, q, pageable);
            } else {
                page = courseRepository.findByTitleContainingIgnoreCase(q, pageable);
            }
        } else {
            if (status != null) {
                // Use query with eager loading to avoid LazyInitializationException
                page = courseRepository.findByStatusWithAuthor(status, pageable);
            } else {
                // Use query with eager loading to avoid LazyInitializationException
                page = courseRepository.findAllWithAuthor(pageable);
            }
        }
        
        return PageResponse.<CourseSummaryDTO>builder()
                .items(page.map(courseMapper::toSummaryDto).getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .total(page.getTotalElements())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CourseSummaryDTO> listCoursesByAuthor(Long authorId, Pageable pageable) {
        log.debug("Listing courses by author {}, page {}", authorId, pageable.getPageNumber());
        
        // Use query with eager loading to avoid LazyInitializationException
        Page<Course> page = courseRepository.findByAuthorIdWithAuthor(authorId, pageable);
        
        return PageResponse.<CourseSummaryDTO>builder()
                .items(page.map(courseMapper::toSummaryDto).getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .total(page.getTotalElements())
                .build();
    }

    @Override
    @Transactional
    public CourseDetailDTO submitCourseForApproval(Long courseId, Long actorId) {
        log.info("Submitting course {} for approval by actor {}", courseId, actorId);
        
        Course course = getCourseOrThrow(courseId);
        ensureAuthorOrAdmin(actorId, course.getAuthor().getId());
        
        // Only DRAFT courses can be submitted for approval
        if (course.getStatus() != CourseStatus.DRAFT) {
            throw new ConflictException("COURSE_CANNOT_BE_SUBMITTED_IN_STATUS_" + course.getStatus());
        }
        
        course.setStatus(CourseStatus.PENDING);
        course.setUpdatedAt(now());
        
        Course saved = courseRepository.save(course);
        log.info("Course {} submitted for approval by actor {}", courseId, actorId);
        
        return courseMapper.toDetailDto(saved);
    }

    @Override
    @Transactional
    public CourseDetailDTO approveCourse(Long courseId, Long adminId) {
        log.info("Admin {} approving course {}", adminId, courseId);
        
        Course course = getCourseOrThrow(courseId);
        
        // Only PENDING courses can be approved
        if (course.getStatus() != CourseStatus.PENDING) {
            throw new ConflictException("COURSE_CANNOT_BE_APPROVED_IN_STATUS_" + course.getStatus());
        }
        
        course.setStatus(CourseStatus.PUBLIC);
        course.setUpdatedAt(now());
        
        Course saved = courseRepository.save(course);
        log.info("Course {} approved by admin {}", courseId, adminId);
        
        // TODO: Publish event for course approval notification
        
        return courseMapper.toDetailDto(saved);
    }

    @Override
    @Transactional
    public CourseDetailDTO rejectCourse(Long courseId, Long adminId, String reason) {
        log.info("Admin {} rejecting course {} with reason: {}", adminId, courseId, reason);
        
        Course course = getCourseOrThrow(courseId);
        
        // Only PENDING courses can be rejected
        if (course.getStatus() != CourseStatus.PENDING) {
            throw new ConflictException("COURSE_CANNOT_BE_REJECTED_IN_STATUS_" + course.getStatus());
        }
        
        course.setStatus(CourseStatus.DRAFT);
        course.setUpdatedAt(now());
        
        Course saved = courseRepository.save(course);
        log.info("Course {} rejected by admin {} with reason: {}", courseId, adminId, reason);
        
        // TODO: Publish event for course rejection notification with reason
        
        return courseMapper.toDetailDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CourseSummaryDTO> listCoursesByStatus(CourseStatus status, Pageable pageable) {
        log.debug("Listing courses with status '{}', page {}", status, pageable.getPageNumber());
        
        // Use query with eager loading to avoid LazyInitializationException
        Page<Course> page = courseRepository.findByStatusWithAuthor(status, pageable);
        
        return PageResponse.<CourseSummaryDTO>builder()
                .items(page.map(courseMapper::toSummaryDto).getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .total(page.getTotalElements())
                .build();
    }

    // ===== Helper Methods =====
    
    private Course getCourseOrThrow(Long id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("COURSE_NOT_FOUND"));
    }

    private void ensureAuthorOrAdmin(Long actorId, Long authorId) {
        // TODO: call Auth/Role service to check if actor is ADMIN
        if (!actorId.equals(authorId)) {
            // TODO: implement proper role checking via AuthService
            throw new AccessDeniedException("FORBIDDEN");
        }
    }

    private void validateCreateCourseRequest(CourseCreateDTO dto) {
        if (dto.getTitle() == null || dto.getTitle().isBlank()) {
            throw new IllegalArgumentException("Course title is required");
        }
        // TODO: add more validation (description length, level validity, etc.)
    }

    private void validateUpdateCourseRequest(CourseUpdateDTO dto) {
        if (dto.getTitle() != null && dto.getTitle().isBlank()) {
            throw new IllegalArgumentException("Course title cannot be blank");
        }
        // TODO: add more validation
    }

    private Instant now() {
        return Instant.now(clock);
    }

    @Override
    @Transactional(readOnly = true)
    public long getTotalCourseCount() {
        return courseRepository.count();
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<java.util.Map<String, Object>> getAllCoursesForDebug() {
        List<Course> courses = courseRepository.findAll();
        return courses.stream()
                .map(course -> {
                    Map<String, Object> courseInfo = new HashMap<>();
                    courseInfo.put("id", course.getId());
                    courseInfo.put("title", course.getTitle());
                    courseInfo.put("status", course.getStatus());
                    courseInfo.put("level", course.getLevel());
                    courseInfo.put("authorId", course.getAuthor().getId());
                    courseInfo.put("moduleCount", course.getModules().size());
                    return courseInfo;
                })
                .toList();
    }
}