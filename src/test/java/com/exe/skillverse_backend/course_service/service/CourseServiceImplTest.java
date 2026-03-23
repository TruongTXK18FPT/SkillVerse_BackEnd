package com.exe.skillverse_backend.course_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.course_service.dto.coursedto.CourseDetailDTO;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.course_service.entity.CourseRevision;
import com.exe.skillverse_backend.course_service.entity.enums.CourseRevisionStatus;
import com.exe.skillverse_backend.course_service.entity.enums.CourseStatus;
import com.exe.skillverse_backend.course_service.entity.enums.CourseUpgradePolicy;
import com.exe.skillverse_backend.course_service.entity.enums.EnrollmentStatus;
import com.exe.skillverse_backend.course_service.mapper.CourseMapper;
import com.exe.skillverse_backend.course_service.policy.CourseDeletionPolicy;
import com.exe.skillverse_backend.course_service.policy.CourseRevisionFeatureProperties;
import com.exe.skillverse_backend.course_service.repository.CourseEnrollmentRepository;
import com.exe.skillverse_backend.course_service.repository.CoursePurchaseRepository;
import com.exe.skillverse_backend.course_service.repository.CourseRepository;
import com.exe.skillverse_backend.course_service.repository.CourseRevisionRepository;
import com.exe.skillverse_backend.course_service.repository.ModuleRepository;
import com.exe.skillverse_backend.course_service.service.impl.CourseServiceImpl;
import com.exe.skillverse_backend.notification_service.service.NotificationService;
import com.exe.skillverse_backend.shared.repository.MediaRepository;
import com.exe.skillverse_backend.shared.service.CloudinaryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseServiceImplTest {

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CourseEnrollmentRepository enrollmentRepository;

    @Mock
    private CoursePurchaseRepository purchaseRepository;

    @Mock
    private ModuleRepository moduleRepository;

    @Mock
    private MediaRepository mediaRepository;

    @Mock
    private CloudinaryService cloudinaryService;

    @Mock
    private CourseMapper courseMapper;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private Clock clock;

    @Mock
    private CourseDeletionPolicy courseDeletionPolicy;

    @Mock
    private CourseRevisionRepository courseRevisionRepository;

    @Mock
    private CourseRevisionFeatureProperties courseRevisionFeatureProperties;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private CourseServiceImpl courseService;

    @Test
    void deleteCourse_archivesByDefaultEvenWhenNoDependencies() {
        Long courseId = 100L;
        Long authorId = 7L;
        Course course = buildCourse(courseId, authorId, CourseStatus.DRAFT);
        Instant now = Instant.parse("2026-03-15T10:00:00Z");

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(enrollmentRepository.countByCourseId(courseId)).thenReturn(0L);
        when(purchaseRepository.countSuccessfulPurchasesByCourseId(courseId)).thenReturn(0L);
        when(courseDeletionPolicy.canHardDelete(CourseStatus.DRAFT, 0L, 0L)).thenReturn(false);
        when(clock.instant()).thenReturn(now);

        courseService.deleteCourse(courseId, authorId);

        ArgumentCaptor<Course> captor = ArgumentCaptor.forClass(Course.class);
        verify(courseRepository).save(captor.capture());
        verify(courseRepository, never()).delete(any(Course.class));
        assertEquals(CourseStatus.ARCHIVED, captor.getValue().getStatus());
    }

    @Test
    void deleteCourse_hardDeletesWhenEnabledAndEligible() {
        Long courseId = 101L;
        Long authorId = 8L;
        Course course = buildCourse(courseId, authorId, CourseStatus.DRAFT);

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(enrollmentRepository.countByCourseId(courseId)).thenReturn(0L);
        when(purchaseRepository.countSuccessfulPurchasesByCourseId(courseId)).thenReturn(0L);
        when(courseDeletionPolicy.canHardDelete(CourseStatus.DRAFT, 0L, 0L)).thenReturn(true);

        courseService.deleteCourse(courseId, authorId);

        verify(courseRepository).delete(course);
        verify(courseRepository, never()).save(any(Course.class));
    }

    @Test
    void deleteCourse_archivesWhenEnabledButHasDependencies() {
        Long courseId = 102L;
        Long authorId = 9L;
        Course course = buildCourse(courseId, authorId, CourseStatus.DRAFT);
        Instant now = Instant.parse("2026-03-15T10:05:00Z");

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(enrollmentRepository.countByCourseId(courseId)).thenReturn(1L);
        when(purchaseRepository.countSuccessfulPurchasesByCourseId(courseId)).thenReturn(0L);
        when(courseDeletionPolicy.canHardDelete(CourseStatus.DRAFT, 1L, 0L)).thenReturn(false);
        when(clock.instant()).thenReturn(now);

        courseService.deleteCourse(courseId, authorId);

        ArgumentCaptor<Course> captor = ArgumentCaptor.forClass(Course.class);
        verify(courseRepository).save(captor.capture());
        verify(courseRepository, never()).delete(any(Course.class));
        assertEquals(CourseStatus.ARCHIVED, captor.getValue().getStatus());
    }

    @Test
    void deleteCourse_archivesWhenEnabledButStatusNotEligibleForHardDelete() {
        Long courseId = 103L;
        Long authorId = 10L;
        Course course = buildCourse(courseId, authorId, CourseStatus.PUBLIC);
        Instant now = Instant.parse("2026-03-15T10:10:00Z");

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(enrollmentRepository.countByCourseId(courseId)).thenReturn(0L);
        when(purchaseRepository.countSuccessfulPurchasesByCourseId(courseId)).thenReturn(0L);
        when(courseDeletionPolicy.canHardDelete(CourseStatus.PUBLIC, 0L, 0L)).thenReturn(false);
        when(clock.instant()).thenReturn(now);

        courseService.deleteCourse(courseId, authorId);

        ArgumentCaptor<Course> captor = ArgumentCaptor.forClass(Course.class);
        verify(courseRepository).save(captor.capture());
        verify(courseRepository, never()).delete(any(Course.class));
        assertEquals(CourseStatus.ARCHIVED, captor.getValue().getStatus());
    }

    @Test
    void getCourse_overlaysActiveRevision_whenReadPathEnabled() {
        Long courseId = 200L;
        Long authorId = 11L;
        Long activeRevisionId = 900L;

        Course course = buildCourse(courseId, authorId, CourseStatus.PUBLIC);
        course.setRevisioningEnabled(true);
        course.setActiveRevisionId(activeRevisionId);

        CourseRevision revision = CourseRevision.builder()
                .id(activeRevisionId)
                .course(course)
                .title("Live revision title")
                .description("Live revision description")
                .shortDescription("Live short")
                .level("ADVANCED")
                .category("Backend")
                .estimatedDurationHours(18)
                .language("vi")
                .price(java.math.BigDecimal.valueOf(120000))
                .currency("VND")
                .learningObjectivesJson(new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(Collections.singletonList("Obj 1")))
                .requirementsJson(new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(Collections.singletonList("Req 1")))
                .build();

        CourseDetailDTO mappedDto = new CourseDetailDTO();
        mappedDto.setTitle("Base course title");
        mappedDto.setDescription("Base description");

        when(courseRepository.findByIdWithAuthorAndModules(courseId)).thenReturn(course);
        when(courseMapper.toDetailDto(course)).thenReturn(mappedDto);
        when(courseRevisionFeatureProperties.isReadEnabled()).thenReturn(true);
        when(courseRevisionRepository.findById(activeRevisionId)).thenReturn(Optional.of(revision));

        CourseDetailDTO result = courseService.getCourse(courseId, null);

        assertEquals("Live revision title", result.getTitle());
        assertEquals("Live revision description", result.getDescription());
        assertEquals("Live short", result.getShortDescription());
        assertEquals("ADVANCED", result.getLevel());
        assertEquals("Backend", result.getCategory());
        assertEquals(18, result.getEstimatedDurationHours());
        assertEquals("vi", result.getLanguage());
        assertEquals("Obj 1", result.getLearningObjectives().get(0));
        assertEquals("Req 1", result.getRequirements().get(0));
    }

    @Test
    void updateUpgradePolicy_updatesManualPolicyAndSyncsEnrollmentSnapshot() {
        Long courseId = 300L;
        Long actorId = 21L;
        Course course = buildCourse(courseId, actorId, CourseStatus.PUBLIC);
        CourseDetailDTO mapped = new CourseDetailDTO();

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course), Optional.of(course));
        when(courseRepository.save(course)).thenReturn(course);
        when(courseMapper.toDetailDto(course)).thenReturn(mapped);
        when(enrollmentRepository.syncUpgradePolicySnapshotByStatus(
                courseId,
            CourseUpgradePolicy.MANUAL.name(),
                EnrollmentStatus.ENROLLED
        )).thenReturn(4);

        CourseDetailDTO result = courseService.updateUpgradePolicy(
                courseId,
                CourseUpgradePolicy.MANUAL,
                actorId
        );

        assertEquals(CourseUpgradePolicy.MANUAL, course.getUpgradePolicy());
        assertEquals(
            "MANUAL: learner giữ revision hiện tại cho đến khi chủ động nâng cấp.",
                result.getUpgradePolicyStatusMessage()
        );
        verify(enrollmentRepository).syncUpgradePolicySnapshotByStatus(
                courseId,
            CourseUpgradePolicy.MANUAL.name(),
                EnrollmentStatus.ENROLLED
        );
        verify(enrollmentRepository, never()).syncUpgradePolicySnapshotByStatus(
                eq(courseId),
            eq(CourseUpgradePolicy.MANUAL.name()),
                eq(EnrollmentStatus.COMPLETED)
        );
    }

    @Test
    void approveCourse_createsInitialApprovedRevisionWhenMissing() {
        Long courseId = 400L;
        Long adminId = 99L;
        Long authorId = 45L;
        Instant now = Instant.parse("2026-03-20T10:00:00Z");

        Course course = buildCourse(courseId, authorId, CourseStatus.PENDING);
        course.setSubmittedAt(Instant.parse("2026-03-20T09:30:00Z"));
        CourseDetailDTO mapped = new CourseDetailDTO();

        CourseRevision savedRevision = CourseRevision.builder()
                .id(7001L)
                .course(course)
                .revisionNumber(1)
                .status(CourseRevisionStatus.APPROVED)
                .build();

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(courseRepository.save(any(Course.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(courseRevisionRepository.findTopByCourseIdOrderByRevisionNumberDesc(courseId))
                .thenReturn(Optional.empty());
        when(courseRevisionRepository.save(any(CourseRevision.class))).thenReturn(savedRevision);
        when(enrollmentRepository.countByCourseId(courseId)).thenReturn(0L);
        when(courseMapper.toDetailDto(course)).thenReturn(mapped);
        when(clock.instant()).thenReturn(now);
        when(objectMapper.valueToTree(any())).thenReturn(new ObjectMapper().createArrayNode());

        CourseDetailDTO result = courseService.approveCourse(courseId, adminId);

        assertEquals(CourseStatus.PUBLIC, course.getStatus());
        assertEquals(7001L, course.getActiveRevisionId());
        assertEquals(7001L, course.getLatestRevisionId());
        assertEquals(Boolean.TRUE, course.getRevisioningEnabled());
        assertEquals(mapped, result);
        verify(courseRevisionRepository).save(any(CourseRevision.class));
    }

    private Course buildCourse(Long courseId, Long authorId, CourseStatus status) {
        User author = User.builder().id(authorId).build();
        return Course.builder()
                .id(courseId)
                .author(author)
                .status(status)
                .title("Course " + courseId)
                .description("desc")
                .build();
    }
}
