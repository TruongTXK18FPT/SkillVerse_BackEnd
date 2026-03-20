package com.exe.skillverse_backend.course_service.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.course_service.entity.CourseEnrollment;
import com.exe.skillverse_backend.course_service.entity.Lesson;
import com.exe.skillverse_backend.course_service.entity.Module;
import com.exe.skillverse_backend.course_service.entity.enums.CourseStatus;
import com.exe.skillverse_backend.course_service.entity.enums.EnrollmentStatus;
import com.exe.skillverse_backend.course_service.mapper.LessonMapper;
import com.exe.skillverse_backend.course_service.repository.CourseEnrollmentRepository;
import com.exe.skillverse_backend.course_service.repository.LessonProgressRepository;
import com.exe.skillverse_backend.course_service.repository.LessonRepository;
import com.exe.skillverse_backend.course_service.repository.ModuleRepository;
import com.exe.skillverse_backend.course_service.service.impl.LessonServiceImpl;
import com.exe.skillverse_backend.course_service.service.impl.RevisionPinnedContentResolver;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import com.exe.skillverse_backend.shared.repository.MediaRepository;
import java.time.Clock;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LessonServiceImplTest {

    @Mock
    private LessonRepository lessonRepository;

    @Mock
    private LessonProgressRepository lessonProgressRepository;

    @Mock
    private ModuleRepository moduleRepository;

    @Mock
    private CourseEnrollmentRepository enrollmentRepository;

    @Mock
    private MediaRepository mediaRepository;

    @Mock
    private LessonMapper lessonMapper;

    @Mock
    private Clock clock;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CourseLearningProgressService courseLearningProgressService;

    @Mock
    private RevisionPinnedContentResolver revisionPinnedContentResolver;

    @InjectMocks
    private LessonServiceImpl lessonService;

    @Test
    void getLesson_throwsNotFoundWhenLessonOutsidePinnedSnapshot() {
        Course course = Course.builder()
                .id(11L)
                .status(CourseStatus.PUBLIC)
                .revisioningEnabled(true)
                .author(User.builder().id(100L).build())
                .build();
        Module module = Module.builder().id(21L).course(course).build();
        Lesson lesson = Lesson.builder().id(31L).module(module).build();
        CourseEnrollment enrollment = CourseEnrollment.builder().status(EnrollmentStatus.ENROLLED).build();

        when(lessonRepository.findById(31L)).thenReturn(Optional.of(lesson));
        when(enrollmentRepository.findByCourseIdAndUserId(11L, 201L)).thenReturn(Optional.of(enrollment));
        when(revisionPinnedContentResolver.hasLearningAccessEnrollment(course, 201L)).thenReturn(true);
        when(revisionPinnedContentResolver.isLessonInPinnedRevision(course, 201L, 31L)).thenReturn(false);

        assertThrows(NotFoundException.class, () -> lessonService.getLesson(31L, 201L));
    }
}
