package com.exe.skillverse_backend.course_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.entity.PrimaryRole;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.AssignmentSubmissionCreateDTO;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.AssignmentSubmissionDetailDTO;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.AssignmentCreateDTO;
import com.exe.skillverse_backend.course_service.entity.Assignment;
import com.exe.skillverse_backend.course_service.entity.AssignmentSubmission;
import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.course_service.entity.CourseEnrollment;
import com.exe.skillverse_backend.course_service.entity.Module;
import com.exe.skillverse_backend.course_service.entity.enums.EnrollmentStatus;
import com.exe.skillverse_backend.course_service.entity.enums.SubmissionType;
import com.exe.skillverse_backend.course_service.mapper.AssignmentMapper;
import com.exe.skillverse_backend.course_service.mapper.AssignmentSubmissionMapper;
import com.exe.skillverse_backend.course_service.repository.AssignmentCriteriaRepository;
import com.exe.skillverse_backend.course_service.repository.AssignmentRepository;
import com.exe.skillverse_backend.course_service.repository.AssignmentSubmissionRepository;
import com.exe.skillverse_backend.course_service.repository.CourseEnrollmentRepository;
import com.exe.skillverse_backend.course_service.repository.ModuleRepository;
import com.exe.skillverse_backend.course_service.repository.SubmissionCriteriaScoreRepository;
import com.exe.skillverse_backend.course_service.service.impl.AssignmentServiceImpl;
import com.exe.skillverse_backend.course_service.service.impl.RevisionPinnedContentResolver;
import com.exe.skillverse_backend.notification_service.service.NotificationService;
import com.exe.skillverse_backend.shared.repository.MediaRepository;
import com.exe.skillverse_backend.shared.exception.BadRequestException;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import com.exe.skillverse_backend.user_service.entity.UserProfile;
import com.exe.skillverse_backend.user_service.repository.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssignmentServiceImplTest {

    @Mock
    private AssignmentRepository assignmentRepository;

    @Mock
    private AssignmentCriteriaRepository criteriaRepository;

    @Mock
    private SubmissionCriteriaScoreRepository criteriaScoreRepository;

    @Mock
    private AssignmentSubmissionRepository submissionRepository;

    @Mock
    private ModuleRepository moduleRepository;

    @Mock
    private CourseEnrollmentRepository enrollmentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MediaRepository mediaRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private AssignmentMapper assignmentMapper;

    @Mock
    private AssignmentSubmissionMapper submissionMapper;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private Clock clock;

    @Mock
    private CourseLearningProgressService courseLearningProgressService;

        @Mock
        private RevisionPinnedContentResolver revisionPinnedContentResolver;

    @InjectMocks
    private AssignmentServiceImpl assignmentService;

    @Test
    void submit_allowsCompletedEnrollmentStatus() {
        Course course = Course.builder().id(30L).build();
        Module module = Module.builder().id(10L).course(course).build();
        Assignment assignment = Assignment.builder()
                .id(50L)
                .module(module)
                .title("Assignment")
                .submissionType(SubmissionType.TEXT)
                .build();
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setStatus(EnrollmentStatus.COMPLETED);
        User user = User.builder().id(7L).primaryRole(PrimaryRole.USER).build();
        UserProfile userProfile = UserProfile.builder()
                .userId(7L)
                .fullName("Riothai Real Name")
                .build();

        AssignmentSubmissionCreateDTO request = new AssignmentSubmissionCreateDTO();
        request.setSubmissionText("Bai lam cua toi");

        AssignmentSubmission submission = AssignmentSubmission.builder()
                .id(90L)
                .assignment(assignment)
                .user(user)
                .attemptNumber(1)
                .build();
        AssignmentSubmissionDetailDTO expected = AssignmentSubmissionDetailDTO.builder()
                .id(90L)
                .userName("student.local")
                .build();

        when(clock.instant()).thenReturn(Instant.parse("2026-03-01T09:00:00Z"));
        when(assignmentRepository.findById(50L)).thenReturn(Optional.of(assignment));
        when(enrollmentRepository.findByCourseIdAndUserId(30L, 7L)).thenReturn(Optional.of(enrollment));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(userProfileRepository.findByUserId(7L)).thenReturn(Optional.of(userProfile));
        when(submissionRepository.findByAssignmentIdAndUserIdOrderByAttemptNumberDesc(50L, 7L))
                .thenReturn(List.of());
        when(submissionMapper.toEntity(request, assignment, user, null)).thenReturn(submission);
        when(submissionRepository.save(any(AssignmentSubmission.class))).thenReturn(submission);
        when(submissionMapper.toDetailDto(submission)).thenReturn(expected);
        when(criteriaScoreRepository.findBySubmissionId(90L)).thenReturn(List.of());

        AssignmentSubmissionDetailDTO actual = assignmentService.submit(50L, 7L, request);

        assertEquals(90L, actual.getId());
        assertEquals("Riothai Real Name", actual.getUserName());
    }

    @Test
    void createAssignment_rejectsMissingRubricCriteria() {
        User author = User.builder().id(1L).build();
        Course course = Course.builder().id(30L).author(author).build();
        Module module = Module.builder().id(10L).course(course).build();
        AssignmentCreateDTO request = new AssignmentCreateDTO();
        request.setTitle("Assignment without rubric");
        request.setSubmissionType(SubmissionType.TEXT);
        request.setMaxScore(new BigDecimal("100"));
        request.setCriteria(List.of());

        when(moduleRepository.findById(10L)).thenReturn(Optional.of(module));

        BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> assignmentService.createAssignment(10L, request, 1L)
        );

        assertEquals("Assignment must have at least one rubric criterion", ex.getMessage());
    }

        @Test
        void submit_throwsNotFoundWhenAssignmentNotInPinnedSnapshot() {
                User author = User.builder().id(1L).build();
                Course course = Course.builder().id(30L).author(author).revisioningEnabled(true).build();
                Module module = Module.builder().id(10L).course(course).build();
                Assignment assignment = Assignment.builder()
                                .id(50L)
                                .module(module)
                                .submissionType(SubmissionType.TEXT)
                                .build();
                CourseEnrollment enrollment = new CourseEnrollment();
                enrollment.setStatus(EnrollmentStatus.ENROLLED);

                AssignmentSubmissionCreateDTO request = new AssignmentSubmissionCreateDTO();
                request.setSubmissionText("Bai lam draft");

                when(assignmentRepository.findById(50L)).thenReturn(Optional.of(assignment));
                when(enrollmentRepository.findByCourseIdAndUserId(30L, 7L)).thenReturn(Optional.of(enrollment));
                when(revisionPinnedContentResolver.hasLearningAccessEnrollment(course, 7L)).thenReturn(true);
                when(revisionPinnedContentResolver.isAssignmentInPinnedRevision(course, 7L, 50L)).thenReturn(false);

                assertThrows(NotFoundException.class, () -> assignmentService.submit(50L, 7L, request));
        }

    @Test
    void submit_throwsWhenNewestSubmissionPendingGrading() {
        Course course = Course.builder().id(30L).build();
        Module module = Module.builder().id(10L).course(course).build();
        Assignment assignment = Assignment.builder()
                .id(50L)
                .module(module)
                .submissionType(SubmissionType.TEXT)
                .build();
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setStatus(EnrollmentStatus.ENROLLED);
        AssignmentSubmission newest = AssignmentSubmission.builder()
                .id(91L)
                .attemptNumber(1)
                .score(null)
                .isPassed(null)
                .isNewest(true)
                .build();
        User user = User.builder().id(7L).primaryRole(PrimaryRole.USER).build();

        AssignmentSubmissionCreateDTO request = new AssignmentSubmissionCreateDTO();
        request.setSubmissionText("Nop ban cap nhat");

        when(assignmentRepository.findById(50L)).thenReturn(Optional.of(assignment));
        when(enrollmentRepository.findByCourseIdAndUserId(30L, 7L)).thenReturn(Optional.of(enrollment));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(submissionRepository.findByAssignmentIdAndUserIdOrderByAttemptNumberDesc(50L, 7L))
                .thenReturn(List.of(newest));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> assignmentService.submit(50L, 7L, request));
        assertEquals("SUBMISSION_PENDING_GRADING", ex.getMessage());
    }

    @Test
    void submit_throwsWhenNewestSubmissionAlreadyPassed() {
        Course course = Course.builder().id(30L).build();
        Module module = Module.builder().id(10L).course(course).build();
        Assignment assignment = Assignment.builder()
                .id(50L)
                .module(module)
                .submissionType(SubmissionType.TEXT)
                .build();
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setStatus(EnrollmentStatus.ENROLLED);
        AssignmentSubmission newest = AssignmentSubmission.builder()
                .id(92L)
                .attemptNumber(2)
                .score(new BigDecimal("90"))
                .isPassed(true)
                .isNewest(true)
                .build();
        User user = User.builder().id(7L).primaryRole(PrimaryRole.USER).build();

        AssignmentSubmissionCreateDTO request = new AssignmentSubmissionCreateDTO();
        request.setSubmissionText("Nop lai");

        when(assignmentRepository.findById(50L)).thenReturn(Optional.of(assignment));
        when(enrollmentRepository.findByCourseIdAndUserId(30L, 7L)).thenReturn(Optional.of(enrollment));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(submissionRepository.findByAssignmentIdAndUserIdOrderByAttemptNumberDesc(50L, 7L))
                .thenReturn(List.of(newest));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> assignmentService.submit(50L, 7L, request));
        assertEquals("ASSIGNMENT_ALREADY_PASSED", ex.getMessage());
    }

    @Test
    void submit_allowsRetryWhenNewestSubmissionFailedAndCreatesNextAttempt() {
        Course course = Course.builder().id(30L).build();
        Module module = Module.builder().id(10L).course(course).build();
        Assignment assignment = Assignment.builder()
                .id(50L)
                .module(module)
                .title("Retry Assignment")
                .submissionType(SubmissionType.TEXT)
                .build();
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setStatus(EnrollmentStatus.ENROLLED);

        User user = User.builder().id(7L).primaryRole(PrimaryRole.USER).build();
        UserProfile userProfile = UserProfile.builder().userId(7L).fullName("Retry Learner").build();

        AssignmentSubmission newestFailed = AssignmentSubmission.builder()
                .id(93L)
                .attemptNumber(1)
                .score(new BigDecimal("40"))
                .isPassed(false)
                .isNewest(true)
                .isPrevious(false)
                .build();

        AssignmentSubmission draftNewAttempt = AssignmentSubmission.builder()
                .id(94L)
                .assignment(assignment)
                .user(user)
                .build();

        AssignmentSubmission savedAttempt = AssignmentSubmission.builder()
                .id(95L)
                .assignment(assignment)
                .user(user)
                .attemptNumber(2)
                .isNewest(true)
                .isPrevious(false)
                .build();

        AssignmentSubmissionCreateDTO request = new AssignmentSubmissionCreateDTO();
        request.setSubmissionText("Ban retry");

        when(clock.instant()).thenReturn(Instant.parse("2026-03-01T10:00:00Z"));
        when(assignmentRepository.findById(50L)).thenReturn(Optional.of(assignment));
        when(enrollmentRepository.findByCourseIdAndUserId(30L, 7L)).thenReturn(Optional.of(enrollment));
        when(submissionRepository.findByAssignmentIdAndUserIdOrderByAttemptNumberDesc(50L, 7L))
                .thenReturn(List.of(newestFailed));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(userProfileRepository.findByUserId(7L)).thenReturn(Optional.of(userProfile));
        when(submissionMapper.toEntity(request, assignment, user, null)).thenReturn(draftNewAttempt);
        when(submissionRepository.save(any(AssignmentSubmission.class))).thenAnswer(invocation -> {
            AssignmentSubmission toSave = invocation.getArgument(0);
            if (toSave.getId() == null || toSave.getId().equals(94L)) {
                return savedAttempt;
            }
            return toSave;
        });
        when(submissionMapper.toDetailDto(savedAttempt)).thenReturn(AssignmentSubmissionDetailDTO.builder().id(95L).build());
        when(criteriaScoreRepository.findBySubmissionId(95L)).thenReturn(List.of());

        AssignmentSubmissionDetailDTO actual = assignmentService.submit(50L, 7L, request);

        assertEquals(95L, actual.getId());
        assertEquals(false, newestFailed.getIsNewest());
        assertEquals(true, newestFailed.getIsPrevious());
        verify(submissionRepository).save(newestFailed);
    }
}
