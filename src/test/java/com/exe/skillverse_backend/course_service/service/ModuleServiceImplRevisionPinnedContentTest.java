package com.exe.skillverse_backend.course_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.course_service.dto.moduledto.ModuleDetailDTO;
import com.exe.skillverse_backend.course_service.dto.moduledto.ModuleSummaryDTO;
import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.course_service.entity.enums.CourseStatus;
import com.exe.skillverse_backend.course_service.mapper.ModuleMapper;
import com.exe.skillverse_backend.course_service.repository.AssignmentRepository;
import com.exe.skillverse_backend.course_service.repository.CourseEnrollmentRepository;
import com.exe.skillverse_backend.course_service.repository.CourseRepository;
import com.exe.skillverse_backend.course_service.repository.LessonProgressRepository;
import com.exe.skillverse_backend.course_service.repository.LessonRepository;
import com.exe.skillverse_backend.course_service.repository.ModuleRepository;
import com.exe.skillverse_backend.course_service.repository.QuizRepository;
import com.exe.skillverse_backend.course_service.service.impl.ModuleServiceImpl;
import com.exe.skillverse_backend.course_service.service.impl.RevisionPinnedContentResolver;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ModuleServiceImplRevisionPinnedContentTest {

    @Mock
    private ModuleRepository moduleRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CourseEnrollmentRepository enrollmentRepository;

    @Mock
    private ModuleMapper moduleMapper;

    @Mock
    private LessonRepository lessonRepository;

    @Mock
    private LessonProgressRepository lessonProgressRepository;

    @Mock
    private AssignmentRepository assignmentRepository;

    @Mock
    private QuizRepository quizRepository;

    @Mock
    private RevisionPinnedContentResolver revisionPinnedContentResolver;

    private ModuleServiceImpl moduleService;

    @BeforeEach
    void setUp() {
        moduleService = new ModuleServiceImpl(
                moduleRepository,
                courseRepository,
                enrollmentRepository,
                moduleMapper,
                lessonRepository,
                lessonProgressRepository,
                assignmentRepository,
                quizRepository,
                revisionPinnedContentResolver
        );
    }

    @Test
    void shouldUseRevisionPinnedResolverForLearnerWhenListingModulesWithContent() {
        Long courseId = 88L;
        Long actorId = 99L;
        Course course = buildCourse(courseId, 1L, CourseStatus.PUBLIC);
        List<ModuleDetailDTO> pinned = List.of(
                new ModuleDetailDTO(501L, "Pinned module", "Snapshot", 0, null, null, List.of(), List.of(), List.of())
        );

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(revisionPinnedContentResolver.resolveModulesWithContent(course, actorId)).thenReturn(Optional.of(pinned));

        List<ModuleDetailDTO> result = moduleService.listModulesWithContent(courseId, actorId);

        assertSame(pinned, result);
        verify(moduleRepository, never()).findByCourseIdOrderByOrderIndexAsc(anyLong());
    }

    @Test
    void shouldUseRevisionPinnedResolverForLearnerWhenListingModuleSummaries() {
        Long courseId = 89L;
        Long actorId = 100L;
        Course course = buildCourse(courseId, 1L, CourseStatus.PUBLIC);
        List<ModuleSummaryDTO> pinned = List.of(
                new ModuleSummaryDTO(601L, "Pinned summary", "Snapshot", 1)
        );

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(revisionPinnedContentResolver.resolveModuleSummaries(course, actorId)).thenReturn(Optional.of(pinned));

        List<ModuleSummaryDTO> result = moduleService.listModules(courseId, actorId);

        assertEquals(1, result.size());
        assertSame(pinned, result);
        verify(moduleRepository, never()).findByCourseIdOrderByOrderIndexAsc(anyLong());
    }

    @Test
    void shouldBlockFallbackToLiveModulesWhenRevisionEnabledAndResolverEmpty() {
        Long courseId = 90L;
        Long actorId = 101L;
        Course course = buildCourse(courseId, 1L, CourseStatus.PUBLIC);
        course.setRevisioningEnabled(Boolean.TRUE);

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(revisionPinnedContentResolver.resolveModuleSummaries(course, actorId)).thenReturn(Optional.empty());

        List<ModuleSummaryDTO> result = moduleService.listModules(courseId, actorId);

        assertTrue(result.isEmpty());
        verify(moduleRepository, never()).findByCourseIdOrderByOrderIndexAsc(anyLong());
    }

    private Course buildCourse(Long id, Long authorId, CourseStatus status) {
        return Course.builder()
                .id(id)
                .title("Course")
                .author(User.builder().id(authorId).email("author@test.local").build())
                .status(status)
                .build();
    }
}
