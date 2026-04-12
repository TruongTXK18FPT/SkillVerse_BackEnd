package com.exe.skillverse_backend.course_service.service;

import com.exe.skillverse_backend.ai_service.service.RoadmapCompletionSyncService;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.course_service.dto.enrollmentdto.EnrollRequestDTO;
import com.exe.skillverse_backend.course_service.dto.enrollmentdto.EnrollmentDetailDTO;
import com.exe.skillverse_backend.course_service.dto.enrollmentdto.EnrollmentStatsDTO;
import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.course_service.entity.CourseEnrollment;
import com.exe.skillverse_backend.course_service.entity.enums.CourseUpgradePolicy;
import com.exe.skillverse_backend.course_service.entity.enums.EnrollmentStatus;
import com.exe.skillverse_backend.course_service.entity.enums.EntitlementSource;
import com.exe.skillverse_backend.course_service.repository.CourseEnrollmentRepository;
import com.exe.skillverse_backend.course_service.repository.CourseRepository;
import com.exe.skillverse_backend.course_service.service.impl.EnrollmentServiceImpl;
import com.exe.skillverse_backend.shared.dto.PageResponse;
import com.exe.skillverse_backend.shared.exception.AccessDeniedException;
import com.exe.skillverse_backend.shared.exception.ConflictException;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnrollmentServiceImplTest {

    @Mock
    private CourseEnrollmentRepository enrollmentRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoadmapCompletionSyncService roadmapCompletionSyncService;

    private EnrollmentServiceImpl service;

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-04-03T08:15:30Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        service = new EnrollmentServiceImpl(
                enrollmentRepository,
                courseRepository,
                userRepository,
                fixedClock,
                roadmapCompletionSyncService);
        lenient().when(enrollmentRepository.save(any(CourseEnrollment.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("enrollUser should grant ADMIN entitlement for free courses")
    void enrollUser_ShouldGrantAdminEntitlementForFreeCourses() {
        User learner = user(2L, "learner@skillverse.vn");
        Course course = course(10L, null, user(1L, "mentor@skillverse.vn"));
        EnrollRequestDTO request = new EnrollRequestDTO();
        request.setCourseId(course.getId());

        when(courseRepository.findByIdForEnrollmentSnapshot(course.getId())).thenReturn(Optional.of(course));
        when(userRepository.findById(learner.getId())).thenReturn(Optional.of(learner));
        when(enrollmentRepository.existsByCourseIdAndUserId(course.getId(), learner.getId())).thenReturn(false);

        EnrollmentDetailDTO response = service.enrollUser(request, learner.getId());

        ArgumentCaptor<CourseEnrollment> captor = ArgumentCaptor.forClass(CourseEnrollment.class);
        verify(enrollmentRepository).save(captor.capture());
        CourseEnrollment saved = captor.getValue();
        assertEquals(EntitlementSource.ADMIN, saved.getEntitlementSource());
        assertEquals(new CourseEnrollment.CourseEnrollmentId(learner.getId(), course.getId()), saved.getId());
        assertEquals(Instant.now(fixedClock), saved.getEnrollDate());
        assertEquals(77L, saved.getLearningRevisionId());
        assertEquals(CourseUpgradePolicy.MANUAL.name(), saved.getUpgradePolicySnapshot());
        assertEquals("ADMIN", response.getEntitlementSource());
        assertEquals(77L, response.getLearningRevisionId());
        assertFalse(response.isCompleted());
    }

    @Test
    @DisplayName("enrollUser should grant PURCHASE entitlement for paid courses")
    void enrollUser_ShouldGrantPurchaseEntitlementForPaidCourses() {
        User learner = user(2L, "learner@skillverse.vn");
        Course course = course(11L, new BigDecimal("499000"), user(1L, "mentor@skillverse.vn"));
        EnrollRequestDTO request = new EnrollRequestDTO();
        request.setCourseId(course.getId());

        when(courseRepository.findByIdForEnrollmentSnapshot(course.getId())).thenReturn(Optional.of(course));
        when(userRepository.findById(learner.getId())).thenReturn(Optional.of(learner));
        when(enrollmentRepository.existsByCourseIdAndUserId(course.getId(), learner.getId())).thenReturn(false);

        EnrollmentDetailDTO response = service.enrollUser(request, learner.getId());

        assertEquals("PURCHASE", response.getEntitlementSource());
    }

    @Test
    @DisplayName("enrollUser should reject duplicate enrollments")
    void enrollUser_ShouldRejectDuplicateEnrollments() {
        User learner = user(2L, "learner@skillverse.vn");
        Course course = course(10L, null, user(1L, "mentor@skillverse.vn"));
        EnrollRequestDTO request = new EnrollRequestDTO();
        request.setCourseId(course.getId());

        when(courseRepository.findByIdForEnrollmentSnapshot(course.getId())).thenReturn(Optional.of(course));
        when(userRepository.findById(learner.getId())).thenReturn(Optional.of(learner));
        when(enrollmentRepository.existsByCourseIdAndUserId(course.getId(), learner.getId())).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.enrollUser(request, learner.getId()));
        verify(enrollmentRepository, never()).save(any(CourseEnrollment.class));
    }

    @Test
    @DisplayName("getCourseEnrollments should enforce course author ownership")
    void getCourseEnrollments_ShouldEnforceCourseAuthorOwnership() {
        Course course = course(10L, null, user(1L, "mentor@skillverse.vn"));
        when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));

        assertThrows(AccessDeniedException.class,
                () -> service.getCourseEnrollments(course.getId(), PageRequest.of(0, 10), 99L));
    }

    @Test
    @DisplayName("getCourseEnrollments should return mapped enrollments for the course author")
    void getCourseEnrollments_ShouldReturnMappedEnrollmentsForAuthor() {
        User author = user(1L, "mentor@skillverse.vn");
        User learner = user(2L, "learner@skillverse.vn");
        Course course = course(10L, null, author);
        CourseEnrollment enrollment = enrollment(course, learner, EnrollmentStatus.ENROLLED, 35, EntitlementSource.ADMIN);

        when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
        when(enrollmentRepository.findByCourseId(course.getId(), PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(enrollment), PageRequest.of(0, 10), 1));

        PageResponse<EnrollmentDetailDTO> response = service.getCourseEnrollments(course.getId(), PageRequest.of(0, 10),
                author.getId());

        assertEquals(1, response.getItems().size());
        assertEquals(course.getId(), response.getItems().get(0).getCourseId());
        assertEquals(learner.getId(), response.getItems().get(0).getUserId());
    }

    @Test
    @DisplayName("updateProgress should auto-complete enrollments at 100 percent")
    void updateProgress_ShouldAutoCompleteEnrollmentAt100Percent() {
        User learner = user(2L, "learner@skillverse.vn");
        Course course = course(10L, null, user(1L, "mentor@skillverse.vn"));
        CourseEnrollment enrollment = enrollment(course, learner, EnrollmentStatus.ENROLLED, 20, EntitlementSource.ADMIN);
        when(enrollmentRepository.findByCourseIdAndUserId(course.getId(), learner.getId())).thenReturn(Optional.of(enrollment));

        service.updateProgress(course.getId(), learner.getId(), 100);

        assertEquals(100, enrollment.getProgressPercent());
        assertEquals(EnrollmentStatus.COMPLETED, enrollment.getStatus());
        verify(enrollmentRepository).save(enrollment);
        assertNotNull(enrollment.getCompletedAt());
    }

    @Test
    @DisplayName("updateProgress should reject values outside the 0-100 range")
    void updateProgress_ShouldRejectValuesOutsideAllowedRange() {
        assertThrows(IllegalArgumentException.class, () -> service.updateProgress(10L, 2L, 101));
        verify(enrollmentRepository, never()).findByCourseIdAndUserId(anyLong(), anyLong());
    }

    @Test
    @DisplayName("updateCompletionStatus should set progress to 100 when completed")
    void updateCompletionStatus_ShouldSetProgressTo100WhenCompleted() {
        User learner = user(2L, "learner@skillverse.vn");
        Course course = course(10L, null, user(1L, "mentor@skillverse.vn"));
        CourseEnrollment enrollment = enrollment(course, learner, EnrollmentStatus.ENROLLED, 30, EntitlementSource.ADMIN);
        when(enrollmentRepository.findByCourseIdAndUserId(course.getId(), learner.getId())).thenReturn(Optional.of(enrollment));

        service.updateCompletionStatus(course.getId(), learner.getId(), true);

        assertEquals(EnrollmentStatus.COMPLETED, enrollment.getStatus());
        assertEquals(100, enrollment.getProgressPercent());
        assertNotNull(enrollment.getCompletedAt());
    }

    @Test
    @DisplayName("getEnrollmentStats should compute completion metrics for the author")
    void getEnrollmentStats_ShouldComputeCompletionMetricsForAuthor() {
        User author = user(1L, "mentor@skillverse.vn");
        Course course = course(10L, new BigDecimal("199000"), author);

        when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
        when(enrollmentRepository.countByCourseId(course.getId())).thenReturn(10L);
        when(enrollmentRepository.countActiveEnrollmentsByCourseId(course.getId())).thenReturn(7L);
        when(enrollmentRepository.findAverageProgressByCourseId(course.getId())).thenReturn(62.5);
        when(enrollmentRepository.countEnrollmentsSinceByCourseId(eq(course.getId()), any(Instant.class))).thenReturn(3L);
        when(enrollmentRepository.countCompletionsSinceByCourseId(eq(course.getId()), any(Instant.class))).thenReturn(1L);

        EnrollmentStatsDTO stats = service.getEnrollmentStats(course.getId(), author.getId());

        assertEquals(10L, stats.getTotalEnrollments());
        assertEquals(7L, stats.getActiveEnrollments());
        assertEquals(3L, stats.getCompletedEnrollments());
        assertEquals(30.0, stats.getCompletionRate());
        assertEquals(62.5, stats.getAverageProgress());
        assertEquals(3L, stats.getEnrollmentsThisMonth());
        assertEquals(1L, stats.getCompletionsThisMonth());
    }

    @Test
    @DisplayName("getRecentEnrollments should require the actor to exist")
    void getRecentEnrollments_ShouldRequireExistingActor() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> service.getRecentEnrollments(PageRequest.of(0, 10), 99L));
    }

    private User user(Long id, String email) {
        return User.builder()
                .id(id)
                .email(email)
                .firstName("User")
                .lastName(String.valueOf(id))
                .build();
    }

    private Course course(Long id, BigDecimal price, User author) {
        return Course.builder()
                .id(id)
                .title("Course " + id)
                .price(price)
                .author(author)
                .activeRevisionId(77L)
                .upgradePolicy(CourseUpgradePolicy.MANUAL)
                .build();
    }

    private CourseEnrollment enrollment(Course course, User learner, EnrollmentStatus status, int progress,
            EntitlementSource source) {
        return CourseEnrollment.builder()
                .id(new CourseEnrollment.CourseEnrollmentId(learner.getId(), course.getId()))
                .course(course)
                .user(learner)
                .enrollDate(Instant.now(fixedClock))
                .status(status)
                .progressPercent(progress)
                .entitlementSource(source)
                .learningRevisionId(77L)
                .upgradePolicySnapshot(CourseUpgradePolicy.MANUAL.name())
                .build();
    }
}
