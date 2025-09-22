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
        
        // TODO: Load User entity from authorId via AuthService/UserRepository
        User author = User.builder().id(authorId).build(); // Placeholder for now
        
        // Load thumbnail media if provided
        Media thumbnail = null;
        if (dto.getThumbnailMediaId() != null) {
            thumbnail = mediaRepository.findById(dto.getThumbnailMediaId())
                    .orElseThrow(() -> new NotFoundException("MEDIA_NOT_FOUND"));
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
        Media thumbnail = null;
        if (dto.getThumbnailMediaId() != null) {
            thumbnail = mediaRepository.findById(dto.getThumbnailMediaId())
                    .orElseThrow(() -> new NotFoundException("MEDIA_NOT_FOUND"));
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
        Course course = getCourseOrThrow(id);
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
                page = courseRepository.findByStatus(status, pageable);
            } else {
                page = courseRepository.findAll(pageable);
            }
        }
        
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
}