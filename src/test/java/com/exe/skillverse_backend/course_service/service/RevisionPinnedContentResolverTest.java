package com.exe.skillverse_backend.course_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.course_service.dto.moduledto.ModuleDetailDTO;
import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.course_service.entity.CourseEnrollment;
import com.exe.skillverse_backend.course_service.entity.CourseRevision;
import com.exe.skillverse_backend.course_service.entity.enums.CourseStatus;
import com.exe.skillverse_backend.course_service.entity.enums.CourseRevisionStatus;
import com.exe.skillverse_backend.course_service.entity.enums.EnrollmentStatus;
import com.exe.skillverse_backend.course_service.repository.CourseEnrollmentRepository;
import com.exe.skillverse_backend.course_service.repository.CourseRevisionRepository;
import com.exe.skillverse_backend.course_service.service.impl.RevisionPinnedContentResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RevisionPinnedContentResolverTest {

    @Mock
    private CourseEnrollmentRepository enrollmentRepository;

    @Mock
    private CourseRevisionRepository courseRevisionRepository;

    private RevisionPinnedContentResolver resolver;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        resolver = new RevisionPinnedContentResolver(enrollmentRepository, courseRevisionRepository);
        objectMapper = new ObjectMapper();
    }

    @Test
    void shouldResolvePinnedRevisionSnapshotToModuleDetailDto() throws Exception {
        Long actorId = 100L;
        Long courseId = 10L;
        Long revisionId = 99L;

        Course course = buildCourse(courseId, 1L);
        course.setActiveRevisionId(revisionId);

        CourseEnrollment enrollment = CourseEnrollment.builder()
                .course(course)
                .user(User.builder().id(actorId).build())
                .status(EnrollmentStatus.ENROLLED)
                .learningRevisionId(revisionId)
                .build();

        String snapshotJson = """
                {
                  "snapshotVersion": 1,
                  "modules": [
                    {
                      "id": 501,
                      "orderIndex": 0,
                      "title": "Module Snapshot",
                      "description": "Snapshot module",
                      "lessons": [
                        {
                          "id": 701,
                          "orderIndex": 0,
                          "title": "Reading A",
                          "type": "reading",
                          "durationMin": 5,
                          "contentText": "Noi dung moi"
                        },
                        {
                          "id": 702,
                          "orderIndex": 1,
                          "title": "Quiz A",
                          "type": "quiz",
                          "passScore": 85,
                          "quizDescription": "Quiz desc",
                          "questions": [
                            {"id": 9001, "text": "Q1"},
                            {"id": 9002, "text": "Q2"}
                          ]
                        },
                        {
                                                                                                        "id": 703,
                          "orderIndex": 2,
                          "title": "Assignment A",
                          "type": "assignment",
                          "assignmentDescription": "Ass desc",
                          "assignmentSubmissionType": "TEXT",
                          "assignmentMaxScore": 100
                        }
                      ]
                    }
                  ]
                }
                """;

        CourseRevision revision = CourseRevision.builder()
                .id(revisionId)
                .course(course)
                .revisionNumber(2)
                .status(CourseRevisionStatus.APPROVED)
                .contentSnapshotJson(objectMapper.readTree(snapshotJson))
                .createdAt(Instant.now())
                .build();

        when(enrollmentRepository.findByCourseIdAndUserId(courseId, actorId))
                .thenReturn(Optional.of(enrollment));
        when(courseRevisionRepository.findByIdAndCourse_Id(revisionId, courseId))
                .thenReturn(Optional.of(revision));

        Optional<List<ModuleDetailDTO>> resolved = resolver.resolveModulesWithContent(course, actorId);

        assertTrue(resolved.isPresent());
        List<ModuleDetailDTO> modules = resolved.get();
        assertEquals(1, modules.size());

        ModuleDetailDTO module = modules.get(0);
        assertEquals(501L, module.getId());
        assertEquals("Module Snapshot", module.getTitle());
        assertEquals(1, module.getLessons().size());
        assertEquals(1, module.getQuizzes().size());
        assertEquals(1, module.getAssignments().size());

        assertEquals("Reading A", module.getLessons().get(0).getTitle());
        assertEquals(300, module.getLessons().get(0).getDurationSec());
        assertEquals("Noi dung moi", module.getLessons().get(0).getContentText());

        assertEquals("Quiz A", module.getQuizzes().get(0).getTitle());
        assertEquals(85, module.getQuizzes().get(0).getPassScore());
        assertEquals(2, module.getQuizzes().get(0).getQuestionCount());

        assertEquals("Assignment A", module.getAssignments().get(0).getTitle());
        assertEquals(703L, module.getAssignments().get(0).getId());
    }

    @Test
    void shouldReturnEmptyWhenActorIsNotEnrolled() {
        Long actorId = 200L;
        Course course = buildCourse(20L, 2L);

        when(enrollmentRepository.findByCourseIdAndUserId(course.getId(), actorId))
                .thenReturn(Optional.empty());

        Optional<List<ModuleDetailDTO>> resolved = resolver.resolveModulesWithContent(course, actorId);
        assertFalse(resolved.isPresent());
    }

    @Test
    void shouldNotFallbackToLatestDraftRevisionWhenLearningPinAndActiveRevisionAreMissing() throws Exception {
        Long actorId = 300L;
        Long courseId = 30L;
        Long draftRevisionId = 901L;

        Course course = buildCourse(courseId, 3L);
        course.setActiveRevisionId(null);
        course.setLatestRevisionId(draftRevisionId);

        CourseEnrollment enrollment = CourseEnrollment.builder()
                .course(course)
                .user(User.builder().id(actorId).build())
                .status(EnrollmentStatus.ENROLLED)
                .learningRevisionId(null)
                .build();

        when(enrollmentRepository.findByCourseIdAndUserId(courseId, actorId))
                .thenReturn(Optional.of(enrollment));
        when(courseRevisionRepository.findTopByCourseIdAndStatusOrderByRevisionNumberDesc(
                courseId,
                CourseRevisionStatus.APPROVED
        )).thenReturn(Optional.empty());

        Optional<List<ModuleDetailDTO>> resolved = resolver.resolveModulesWithContent(course, actorId);

        assertTrue(resolved.isEmpty());
    }

    @Test
    void shouldFallbackToLatestApprovedRevisionWhenLearningPinAndActiveRevisionAreMissing() throws Exception {
        Long actorId = 301L;
        Long courseId = 31L;
        Long approvedRevisionId = 902L;

        Course course = buildCourse(courseId, 3L);
        course.setActiveRevisionId(null);
        course.setLatestRevisionId(999L);

        CourseEnrollment enrollment = CourseEnrollment.builder()
                .course(course)
                .user(User.builder().id(actorId).build())
                .status(EnrollmentStatus.ENROLLED)
                .learningRevisionId(null)
                .build();

        CourseRevision approvedRevision = CourseRevision.builder()
                .id(approvedRevisionId)
                .course(course)
                .revisionNumber(1)
                .status(CourseRevisionStatus.APPROVED)
                .contentSnapshotJson(objectMapper.readTree("""
                        {
                          "snapshotVersion": 1,
                          "modules": [
                            {
                              "id": 1,
                              "title": "Approved module",
                              "lessons": []
                            }
                          ]
                        }
                        """))
                .createdAt(Instant.now())
                .build();

        when(enrollmentRepository.findByCourseIdAndUserId(courseId, actorId))
                .thenReturn(Optional.of(enrollment));
        when(courseRevisionRepository.findTopByCourseIdAndStatusOrderByRevisionNumberDesc(
                courseId,
                CourseRevisionStatus.APPROVED
        )).thenReturn(Optional.of(approvedRevision));
        when(courseRevisionRepository.findByIdAndCourse_Id(approvedRevisionId, courseId))
                .thenReturn(Optional.of(approvedRevision));

        Optional<List<ModuleDetailDTO>> resolved = resolver.resolveModulesWithContent(course, actorId);

        assertTrue(resolved.isPresent());
        assertEquals(1, resolved.get().size());
        assertEquals("Approved module", resolved.get().get(0).getTitle());
    }

    @Test
    void shouldSkipItemsWithoutPositiveIdsInPinnedSnapshot() throws Exception {
        Long actorId = 302L;
        Long courseId = 32L;
        Long revisionId = 903L;

        Course course = buildCourse(courseId, 3L);
        course.setActiveRevisionId(revisionId);

        CourseEnrollment enrollment = CourseEnrollment.builder()
                .course(course)
                .user(User.builder().id(actorId).build())
                .status(EnrollmentStatus.ENROLLED)
                .learningRevisionId(revisionId)
                .build();

        CourseRevision revision = CourseRevision.builder()
                .id(revisionId)
                .course(course)
                .revisionNumber(2)
                .status(CourseRevisionStatus.APPROVED)
                .contentSnapshotJson(objectMapper.readTree("""
                        {
                          "snapshotVersion": 1,
                          "modules": [
                            {
                              "id": 888,
                              "title": "Module with mixed ids",
                              "lessons": [
                                {"id": 777, "type": "reading", "title": "Valid reading"},
                                {"type": "quiz", "title": "Missing id quiz"}
                              ]
                            }
                          ]
                        }
                        """))
                .createdAt(Instant.now())
                .build();

        when(enrollmentRepository.findByCourseIdAndUserId(courseId, actorId))
                .thenReturn(Optional.of(enrollment));
        when(courseRevisionRepository.findByIdAndCourse_Id(revisionId, courseId))
                .thenReturn(Optional.of(revision));

        Optional<List<ModuleDetailDTO>> resolved = resolver.resolveModulesWithContent(course, actorId);

        assertTrue(resolved.isPresent());
        assertEquals(1, resolved.get().size());
        ModuleDetailDTO module = resolved.get().get(0);
        assertEquals(1, module.getLessons().size());
        assertEquals(0, module.getQuizzes().size());
        assertEquals(777L, module.getLessons().get(0).getId());
    }

    @Test
    void shouldPrioritizeSnapshotArraySequenceOverLegacyOrderIndexValues() throws Exception {
        Long actorId = 303L;
        Long courseId = 33L;
        Long revisionId = 904L;

        Course course = buildCourse(courseId, 3L);
        course.setActiveRevisionId(revisionId);

        CourseEnrollment enrollment = CourseEnrollment.builder()
                .course(course)
                .user(User.builder().id(actorId).build())
                .status(EnrollmentStatus.ENROLLED)
                .learningRevisionId(revisionId)
                .build();

        CourseRevision revision = CourseRevision.builder()
                .id(revisionId)
                .course(course)
                .revisionNumber(2)
                .status(CourseRevisionStatus.APPROVED)
                .contentSnapshotJson(objectMapper.readTree("""
                        {
                          "snapshotVersion": 1,
                          "modules": [
                            {
                              "id": 1001,
                              "orderIndex": 99,
                              "title": "Module first in array",
                              "lessons": [
                                {"id": 2001, "type": "quiz", "title": "Quiz first", "orderIndex": 999},
                                {"id": 2002, "type": "reading", "title": "Reading second", "orderIndex": 0},
                                {"id": 2003, "type": "assignment", "title": "Assignment third", "orderIndex": 0}
                              ]
                            },
                            {
                              "id": 1002,
                              "orderIndex": -5,
                              "title": "Module second in array",
                              "lessons": []
                            }
                          ]
                        }
                        """))
                .createdAt(Instant.now())
                .build();

        when(enrollmentRepository.findByCourseIdAndUserId(courseId, actorId))
                .thenReturn(Optional.of(enrollment));
        when(courseRevisionRepository.findByIdAndCourse_Id(revisionId, courseId))
                .thenReturn(Optional.of(revision));

        Optional<List<ModuleDetailDTO>> resolved = resolver.resolveModulesWithContent(course, actorId);

        assertTrue(resolved.isPresent());
        List<ModuleDetailDTO> modules = resolved.get();
        assertEquals(2, modules.size());
        assertEquals(1001L, modules.get(0).getId());
        assertEquals(0, modules.get(0).getOrderIndex());
        assertEquals(1002L, modules.get(1).getId());
        assertEquals(1, modules.get(1).getOrderIndex());

        ModuleDetailDTO firstModule = modules.get(0);
        assertEquals(1, firstModule.getQuizzes().size());
        assertEquals(1, firstModule.getLessons().size());
        assertEquals(1, firstModule.getAssignments().size());
        assertEquals(0, firstModule.getQuizzes().get(0).getOrderIndex());
        assertEquals(1, firstModule.getLessons().get(0).getOrderIndex());
        assertEquals(2, firstModule.getAssignments().get(0).getOrderIndex());
    }

    private Course buildCourse(Long courseId, Long authorId) {
        return Course.builder()
                .id(courseId)
                .title("Course")
                .status(CourseStatus.PUBLIC)
                .author(User.builder().id(authorId).email("author@test.local").build())
                .build();
    }
}
