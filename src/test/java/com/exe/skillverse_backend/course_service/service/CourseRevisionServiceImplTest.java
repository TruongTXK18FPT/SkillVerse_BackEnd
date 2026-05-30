package com.exe.skillverse_backend.course_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.course_service.dto.coursedto.CourseRevisionDTO;
import com.exe.skillverse_backend.course_service.dto.coursedto.CourseRevisionUpdateDTO;
import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.course_service.entity.CourseRevision;
import com.exe.skillverse_backend.course_service.entity.Lesson;
import com.exe.skillverse_backend.course_service.entity.LessonAttachment;
import com.exe.skillverse_backend.course_service.entity.Module;
import com.exe.skillverse_backend.course_service.entity.Assignment;
import com.exe.skillverse_backend.course_service.entity.AssignmentCriteria;
import com.exe.skillverse_backend.course_service.entity.Quiz;
import com.exe.skillverse_backend.course_service.entity.QuizOption;
import com.exe.skillverse_backend.course_service.entity.QuizQuestion;
import com.exe.skillverse_backend.course_service.entity.enums.AttachmentType;
import com.exe.skillverse_backend.course_service.entity.enums.CourseRevisionStatus;
import com.exe.skillverse_backend.course_service.entity.enums.CourseStatus;
import com.exe.skillverse_backend.course_service.entity.enums.LessonType;
import com.exe.skillverse_backend.course_service.entity.enums.QuestionType;
import com.exe.skillverse_backend.course_service.entity.enums.SubmissionType;
import com.exe.skillverse_backend.course_service.policy.CourseRevisionFeatureProperties;
import com.exe.skillverse_backend.course_service.repository.AssignmentRepository;
import com.exe.skillverse_backend.course_service.repository.CourseRepository;
import com.exe.skillverse_backend.course_service.repository.CourseRevisionRepository;
import com.exe.skillverse_backend.course_service.repository.CourseSkillRepository;
import com.exe.skillverse_backend.course_service.repository.LessonRepository;
import com.exe.skillverse_backend.course_service.repository.ModuleRepository;
import com.exe.skillverse_backend.course_service.repository.QuizRepository;
import com.exe.skillverse_backend.course_service.service.impl.CourseRevisionServiceImpl;
import com.exe.skillverse_backend.shared.service.CloudinaryService;
import com.exe.skillverse_backend.shared.exception.BadRequestException;
import com.exe.skillverse_backend.shared.exception.ConflictException;
import com.exe.skillverse_backend.shared.repository.MediaRepository;
import com.exe.skillverse_backend.shared.repository.SkillRepository;
import com.exe.skillverse_backend.shared.entity.Media;
import com.exe.skillverse_backend.shared.entity.Skill;
import com.exe.skillverse_backend.course_service.entity.CourseSkill;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class CourseRevisionServiceImplTest {
    private long generatedModuleId;
    private long generatedLessonId;
    private long generatedQuizId;
    private long generatedAssignmentId;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CourseRevisionRepository courseRevisionRepository;

    @Mock
    private ModuleRepository moduleRepository;

    @Mock
    private LessonRepository lessonRepository;

    @Mock
    private QuizRepository quizRepository;

    @Mock
    private AssignmentRepository assignmentRepository;

    @Mock
    private MediaRepository mediaRepository;

    @Mock
    private CourseRevisionFeatureProperties courseRevisionFeatureProperties;

    @Mock
    private SkillRepository skillRepository;

    @Mock
    private CourseSkillRepository courseSkillRepository;

        @Mock
        private ApplicationEventPublisher eventPublisher;

        @Mock
        private CloudinaryService cloudinaryService;

    @Mock
    private Clock clock;

    @Mock
    private MeterRegistry meterRegistry;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private CourseRevisionServiceImpl courseRevisionService;

    @BeforeEach
    void setUpRepositorySaveDefaults() {
        generatedModuleId = 20_000L;
        generatedLessonId = 30_000L;
        generatedQuizId = 40_000L;
        generatedAssignmentId = 50_000L;

        lenient().when(moduleRepository.save(any(Module.class))).thenAnswer(invocation -> {
            Module module = invocation.getArgument(0);
            if (module.getId() == null) {
                module.setId(generatedModuleId++);
            }
            return module;
        });
        lenient().when(lessonRepository.save(any(Lesson.class))).thenAnswer(invocation -> {
            Lesson lesson = invocation.getArgument(0);
            if (lesson.getId() == null) {
                lesson.setId(generatedLessonId++);
            }
            return lesson;
        });
        lenient().when(quizRepository.save(any(Quiz.class))).thenAnswer(invocation -> {
            Quiz quiz = invocation.getArgument(0);
            if (quiz.getId() == null) {
                quiz.setId(generatedQuizId++);
            }
            return quiz;
        });
        lenient().when(assignmentRepository.save(any(Assignment.class))).thenAnswer(invocation -> {
            Assignment assignment = invocation.getArgument(0);
            if (assignment.getId() == null) {
                assignment.setId(generatedAssignmentId++);
            }
            return assignment;
        });
    }

    @Test
    void createRevision_throwsWhenWriteFeatureDisabled() {
        when(courseRevisionFeatureProperties.isWriteEnabled()).thenReturn(false);

        assertThrows(ConflictException.class, () -> courseRevisionService.createRevision(200L, 11L));
    }

    @Test
    void createRevision_bootstrapsLegacyInitialApprovedRevisionThenCreatesDraftFromLiveContent() throws Exception {
        Long courseId = 201L;
        Long authorId = 11L;
        Instant now = Instant.parse("2026-03-20T08:00:00Z");

        Module module = Module.builder()
                .id(501L)
                .title("Module A")
                .orderIndex(0)
                .build();
        Lesson lesson = Lesson.builder()
                .id(701L)
                .module(module)
                .title("Lesson A")
                .type(LessonType.READING)
                .orderIndex(0)
                .contentText("Live content")
                .build();

        LessonAttachment attachment = LessonAttachment.builder()
                .id(801L)
                .lesson(lesson)
                .title("Lesson PDF")
                .type(AttachmentType.PDF)
                .externalUrl("https://example.com/lesson-a.pdf")
                .orderIndex(0)
                .build();

        lesson.setAttachments(List.of(attachment));
        module.setLessons(List.of(lesson));

        Course course = Course.builder()
                .id(courseId)
                .author(User.builder().id(authorId).build())
                .status(CourseStatus.PUBLIC)
                .title("Course A")
                .thumbnail(Media.builder()
                        .id(610L)
                        .url("https://cdn.example.com/course-a.png")
                        .type("image/png")
                        .build())
                .modules(List.of(module))
                .courseSkillTags(List.of("JAVA", "PYTHON"))
                .build();

        when(courseRevisionFeatureProperties.isWriteEnabled()).thenReturn(true);
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(courseRevisionRepository.existsByCourseIdAndStatusIn(eq(courseId), any())).thenReturn(false);
        when(courseRevisionRepository.findTopByCourseIdOrderByRevisionNumberDesc(courseId))
                .thenReturn(
                        Optional.empty(),
                        Optional.of(CourseRevision.builder()
                                .id(9000L)
                                .course(course)
                                .revisionNumber(1)
                                .status(CourseRevisionStatus.APPROVED)
                                .build())
                );
        when(clock.instant()).thenReturn(now);
        when(courseRevisionRepository.save(any(CourseRevision.class))).thenAnswer(invocation -> {
            CourseRevision revision = invocation.getArgument(0);
            if (revision.getRevisionNumber() != null && revision.getRevisionNumber() == 1) {
                revision.setId(9000L);
            } else {
                revision.setId(9001L);
            }
            return revision;
        });
        when(courseRepository.save(any(Course.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CourseRevisionDTO created = courseRevisionService.createRevision(courseId, authorId);

        assertEquals(2, created.getRevisionNumber());
        assertEquals(610L, created.getThumbnailMediaId());
        assertEquals("https://cdn.example.com/course-a.png", created.getThumbnailUrl());
        JsonNode snapshot = objectMapper.readTree(created.getContentSnapshotJson());
        assertEquals(1, snapshot.path("snapshotVersion").asInt());
        assertEquals("Module A", snapshot.path("modules").get(0).path("title").asText());
        assertEquals("Lesson A", snapshot.path("modules").get(0).path("lessons").get(0).path("title").asText());
        assertEquals("Live content", snapshot.path("modules").get(0).path("lessons").get(0).path("contentText").asText());
        assertEquals(1, snapshot.path("modules").get(0).path("lessons").get(0).path("attachments").size());
        assertEquals(
                "Lesson PDF",
                snapshot.path("modules").get(0).path("lessons").get(0).path("attachments").get(0).path("name").asText()
        );
        assertEquals(
                "https://example.com/lesson-a.pdf",
                snapshot.path("modules").get(0).path("lessons").get(0).path("attachments").get(0).path("url").asText()
        );
    }

    @Test
    void submitRevision_movesRejectedToPendingAndClearsRejectionReason() {
        Long revisionId = 300L;
        Long authorId = 22L;
        Instant now = Instant.parse("2026-03-16T01:00:00Z");

        Course course = Course.builder()
                .id(100L)
                .author(User.builder().id(authorId).build())
                .status(CourseStatus.PUBLIC)
                .title("Public course")
                .build();

        CourseRevision revision = CourseRevision.builder()
                .id(revisionId)
                .course(course)
                .revisionNumber(2)
                .status(CourseRevisionStatus.REJECTED)
                .title("Rework revision")
                .rejectionReason("Need more detail")
                .build();

        when(courseRevisionFeatureProperties.isWriteEnabled()).thenReturn(true);
        when(courseRevisionRepository.findById(revisionId)).thenReturn(Optional.of(revision));
        when(clock.instant()).thenReturn(now);
        when(courseRevisionRepository.save(any(CourseRevision.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CourseRevisionDTO result = courseRevisionService.submitRevision(revisionId, authorId);

        ArgumentCaptor<CourseRevision> savedCaptor = ArgumentCaptor.forClass(CourseRevision.class);
        verify(courseRevisionRepository).save(savedCaptor.capture());
        assertEquals(CourseRevisionStatus.PENDING, savedCaptor.getValue().getStatus());
        assertNull(savedCaptor.getValue().getRejectionReason());
        assertEquals(now, savedCaptor.getValue().getSubmittedAt());
        assertEquals(CourseRevisionStatus.PENDING, result.getStatus());
        assertNull(result.getRejectionReason());
    }

    @Test
    void submitRevision_throwsWhenNoMeaningfulChangesComparedToActiveRevision() {
        Long revisionId = 301L;
        Long baselineRevisionId = 300L;
        Long authorId = 22L;

        Course course = Course.builder()
                .id(100L)
                .author(User.builder().id(authorId).build())
                .status(CourseStatus.PUBLIC)
                .title("Public course")
                .activeRevisionId(baselineRevisionId)
                .build();

        CourseRevision baseline = CourseRevision.builder()
                .id(baselineRevisionId)
                .course(course)
                .revisionNumber(1)
                .status(CourseRevisionStatus.APPROVED)
                .title("Stable revision")
                .description("Same description")
                .shortDescription("Same short")
                .level("BEGINNER")
                .category("Development")
                .estimatedDurationHours(10)
                .language("vi")
                .currency("VND")
                .build();

        CourseRevision draft = CourseRevision.builder()
                .id(revisionId)
                .course(course)
                .revisionNumber(2)
                .status(CourseRevisionStatus.DRAFT)
                .title("Stable revision")
                .description("Same description")
                .shortDescription("Same short")
                .level("BEGINNER")
                .category("Development")
                .estimatedDurationHours(10)
                .language("vi")
                .currency("VND")
                .build();
        draft.setBaselineSnapshotHash(computeSnapshotHashForDraft(draft));

        when(courseRevisionFeatureProperties.isWriteEnabled()).thenReturn(true);
        when(courseRevisionRepository.findById(revisionId)).thenReturn(Optional.of(draft));

        ConflictException exception = assertThrows(
                ConflictException.class,
                () -> courseRevisionService.submitRevision(revisionId, authorId)
        );

        assertTrue(exception.getMessage().contains("COURSE_REVISION_NO_CHANGES_TO_SUBMIT"));
        verify(courseRevisionRepository, never()).save(argThat(saved -> saved != null
                && saved.getStatus() == CourseRevisionStatus.PENDING
                && saved.getId().equals(revisionId)));
    }

    @Test
    void submitRevision_warnMode_allowsWhenNoMeaningfulChanges() {
        Long revisionId = 311L;
        Long baselineRevisionId = 300L;
        Long authorId = 22L;
        Instant now = Instant.parse("2026-03-16T01:30:00Z");

        Course course = Course.builder()
                .id(100L)
                .author(User.builder().id(authorId).build())
                .status(CourseStatus.PUBLIC)
                .title("Public course")
                .activeRevisionId(baselineRevisionId)
                .build();

        CourseRevision baseline = CourseRevision.builder()
                .id(baselineRevisionId)
                .course(course)
                .revisionNumber(1)
                .status(CourseRevisionStatus.APPROVED)
                .title("Stable revision")
                .description("Same description")
                .shortDescription("Same short")
                .build();

        CourseRevision draft = CourseRevision.builder()
                .id(revisionId)
                .course(course)
                .revisionNumber(2)
                .status(CourseRevisionStatus.DRAFT)
                .title("Stable revision")
                .description("Same description")
                .shortDescription("Same short")
                .sourceRevisionId(baselineRevisionId)
                .build();
        draft.setBaselineSnapshotHash(computeSnapshotHashForDraft(draft));

        when(courseRevisionFeatureProperties.isWriteEnabled()).thenReturn(true);
        when(courseRevisionFeatureProperties.getSubmitChangeCheckMode())
                .thenReturn(CourseRevisionFeatureProperties.SubmitChangeCheckMode.WARN);
        when(courseRevisionRepository.findById(revisionId)).thenReturn(Optional.of(draft));
        when(clock.instant()).thenReturn(now);
        when(courseRevisionRepository.save(any(CourseRevision.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CourseRevisionDTO result = courseRevisionService.submitRevision(revisionId, authorId);

        assertEquals(CourseRevisionStatus.PENDING, result.getStatus());
        verify(courseRevisionRepository).save(argThat(saved -> saved != null
                && saved.getId().equals(revisionId)
                && saved.getStatus() == CourseRevisionStatus.PENDING));
    }

    @Test
    void submitRevision_materializesMissingLessonLikeItemIdBeforeTransitionToPending() {
        Long revisionId = 320L;
        Long authorId = 22L;
        Instant now = Instant.parse("2026-03-20T13:00:00Z");

        Course course = Course.builder()
                .id(100L)
                .author(User.builder().id(authorId).build())
                .status(CourseStatus.PUBLIC)
                .title("Public course")
                .build();

        JsonNode snapshotWithMissingId = objectMapper.createObjectNode()
                .put("snapshotVersion", 1)
                .set("modules", objectMapper.createArrayNode()
                        .add(objectMapper.createObjectNode()
                                .put("id", 1)
                                .put("title", "Module 1")
                                .set("lessons", objectMapper.createArrayNode()
                                        .add(objectMapper.createObjectNode()
                                                .put("type", "reading")
                                                .put("title", "Missing id")))));

        CourseRevision draft = CourseRevision.builder()
                .id(revisionId)
                .course(course)
                .revisionNumber(2)
                .status(CourseRevisionStatus.DRAFT)
                .title("Draft revision")
                .contentSnapshotJson(snapshotWithMissingId)
                .build();

        Module existingModule = Module.builder()
                .id(1L)
                .course(course)
                .title("Module 1")
                .build();

        when(clock.instant()).thenReturn(now);
        when(moduleRepository.findById(1L)).thenReturn(Optional.of(existingModule));
        when(lessonRepository.save(any(Lesson.class))).thenAnswer(invocation -> {
            Lesson lesson = invocation.getArgument(0);
            lesson.setId(777L);
            return lesson;
        });

        when(courseRevisionFeatureProperties.isWriteEnabled()).thenReturn(true);
        when(courseRevisionRepository.findById(revisionId)).thenReturn(Optional.of(draft));
        when(courseRevisionRepository.save(any(CourseRevision.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CourseRevisionDTO result = courseRevisionService.submitRevision(revisionId, authorId);

        assertEquals(CourseRevisionStatus.PENDING, result.getStatus());
        ArgumentCaptor<CourseRevision> savedCaptor = ArgumentCaptor.forClass(CourseRevision.class);
        verify(courseRevisionRepository).save(savedCaptor.capture());
        CourseRevision pendingRevision = savedCaptor.getValue();
        JsonNode savedSnapshot = pendingRevision.getContentSnapshotJson();
        assertEquals(777L, savedSnapshot.path("modules").get(0).path("lessons").get(0).path("id").asLong());
    }

    @Test
    void submitRevision_materializesMissingQuizAndAssignmentIdsBeforePending() {
        Long revisionId = 321L;
        Long authorId = 22L;
        Instant now = Instant.parse("2026-03-20T13:15:00Z");

        Course course = Course.builder()
                .id(100L)
                .author(User.builder().id(authorId).build())
                .status(CourseStatus.PUBLIC)
                .title("Public course")
                .build();

        ObjectNode snapshotWithMissingIds = objectMapper.createObjectNode();
        snapshotWithMissingIds.put("snapshotVersion", 1);

        ArrayNode modulesNode = objectMapper.createArrayNode();
        ObjectNode moduleNode = objectMapper.createObjectNode();
        moduleNode.put("id", 1);
        moduleNode.put("title", "Module 1");

        ArrayNode lessonsNode = objectMapper.createArrayNode();

        ObjectNode quizNode = objectMapper.createObjectNode();
        quizNode.put("type", "quiz");
        quizNode.put("title", "Quiz mới");

        ArrayNode questionsNode = objectMapper.createArrayNode();
        ObjectNode questionNode = objectMapper.createObjectNode();
        questionNode.put("text", "1 + 1 = ?");
        questionNode.put("type", "MULTIPLE_CHOICE");

        ArrayNode optionsNode = objectMapper.createArrayNode();
        optionsNode.add(objectMapper.createObjectNode().put("text", "2").put("correct", true));
        optionsNode.add(objectMapper.createObjectNode().put("text", "3").put("correct", false));
        questionNode.set("options", optionsNode);
        questionsNode.add(questionNode);
        quizNode.set("questions", questionsNode);
        lessonsNode.add(quizNode);

        ObjectNode assignmentNode = objectMapper.createObjectNode();
        assignmentNode.put("type", "assignment");
        assignmentNode.put("title", "Assignment mới");
        assignmentNode.put("assignmentMaxScore", "100");
        ArrayNode criteriaNode = objectMapper.createArrayNode();
        criteriaNode.add(objectMapper.createObjectNode().put("name", "Tiêu chí 1").put("maxPoints", "50"));
        assignmentNode.set("assignmentCriteria", criteriaNode);
        lessonsNode.add(assignmentNode);

        moduleNode.set("lessons", lessonsNode);
        modulesNode.add(moduleNode);
        snapshotWithMissingIds.set("modules", modulesNode);

        CourseRevision draft = CourseRevision.builder()
                .id(revisionId)
                .course(course)
                .revisionNumber(2)
                .status(CourseRevisionStatus.DRAFT)
                .title("Draft revision")
                .contentSnapshotJson(snapshotWithMissingIds)
                .build();

        Module existingModule = Module.builder()
                .id(1L)
                .course(course)
                .title("Module 1")
                .build();

        when(clock.instant()).thenReturn(now);
        when(moduleRepository.findById(1L)).thenReturn(Optional.of(existingModule));
        when(quizRepository.save(any(Quiz.class))).thenAnswer(invocation -> {
            Quiz quiz = invocation.getArgument(0);
            quiz.setId(881L);
            return quiz;
        });
        when(assignmentRepository.save(any(Assignment.class))).thenAnswer(invocation -> {
            Assignment assignment = invocation.getArgument(0);
            assignment.setId(882L);
            return assignment;
        });
        when(courseRevisionFeatureProperties.isWriteEnabled()).thenReturn(true);
        when(courseRevisionRepository.findById(revisionId)).thenReturn(Optional.of(draft));
        when(courseRevisionRepository.save(any(CourseRevision.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CourseRevisionDTO result = courseRevisionService.submitRevision(revisionId, authorId);

        assertEquals(CourseRevisionStatus.PENDING, result.getStatus());
        ArgumentCaptor<CourseRevision> savedCaptor = ArgumentCaptor.forClass(CourseRevision.class);
        verify(courseRevisionRepository).save(savedCaptor.capture());
        JsonNode savedSnapshot = savedCaptor.getValue().getContentSnapshotJson();
        assertEquals(881L, savedSnapshot.path("modules").get(0).path("lessons").get(0).path("id").asLong());
        assertEquals(882L, savedSnapshot.path("modules").get(0).path("lessons").get(1).path("id").asLong());
    }

    @Test
    void submitRevision_materializesWhenItemIdIsNonPositivePlaceholder() {
        Long revisionId = 323L;
        Long authorId = 22L;
        Instant now = Instant.parse("2026-03-20T13:20:00Z");

        Course course = Course.builder()
                .id(100L)
                .author(User.builder().id(authorId).build())
                .status(CourseStatus.PUBLIC)
                .title("Public course")
                .build();

        JsonNode snapshotWithZeroId = objectMapper.createObjectNode()
                .put("snapshotVersion", 1)
                .set("modules", objectMapper.createArrayNode()
                        .add(objectMapper.createObjectNode()
                                .put("id", 1)
                                .put("title", "Module 1")
                                .set("lessons", objectMapper.createArrayNode()
                                        .add(objectMapper.createObjectNode()
                                                .put("id", 0)
                                                .put("type", "reading")
                                                .put("title", "Placeholder id")))));

        CourseRevision draft = CourseRevision.builder()
                .id(revisionId)
                .course(course)
                .revisionNumber(2)
                .status(CourseRevisionStatus.DRAFT)
                .title("Draft revision")
                .contentSnapshotJson(snapshotWithZeroId)
                .build();

        Module existingModule = Module.builder()
                .id(1L)
                .course(course)
                .title("Module 1")
                .build();

        when(clock.instant()).thenReturn(now);
        when(moduleRepository.findById(1L)).thenReturn(Optional.of(existingModule));
        when(lessonRepository.save(any(Lesson.class))).thenAnswer(invocation -> {
            Lesson lesson = invocation.getArgument(0);
            lesson.setId(778L);
            return lesson;
        });
        when(courseRevisionFeatureProperties.isWriteEnabled()).thenReturn(true);
        when(courseRevisionRepository.findById(revisionId)).thenReturn(Optional.of(draft));
        when(courseRevisionRepository.save(any(CourseRevision.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CourseRevisionDTO result = courseRevisionService.submitRevision(revisionId, authorId);

        assertEquals(CourseRevisionStatus.PENDING, result.getStatus());
        JsonNode savedSnapshot = draft.getContentSnapshotJson();
        assertEquals(778L, savedSnapshot.path("modules").get(0).path("lessons").get(0).path("id").asLong());
    }

    @Test
    void submitRevision_clonesExistingLessonIdentityWhenContentChanged() {
        Long revisionId = 324L;
        Long authorId = 22L;
        Instant now = Instant.parse("2026-03-20T13:25:00Z");

        Course course = Course.builder()
                .id(100L)
                .author(User.builder().id(authorId).build())
                .status(CourseStatus.PUBLIC)
                .title("Public course")
                .build();

        Module existingModule = Module.builder()
                .id(1L)
                .course(course)
                .title("Module 1")
                .build();

        Lesson existingLesson = Lesson.builder()
                .id(55L)
                .module(existingModule)
                .title("Bài cũ")
                .type(LessonType.READING)
                .orderIndex(0)
                .contentText("Nội dung cũ")
                .build();

        JsonNode snapshotChangedLesson = objectMapper.createObjectNode()
                .put("snapshotVersion", 1)
                .set("modules", objectMapper.createArrayNode()
                        .add(objectMapper.createObjectNode()
                                .put("id", 1)
                                .set("lessons", objectMapper.createArrayNode()
                                        .add(objectMapper.createObjectNode()
                                                .put("id", 55)
                                                .put("type", "reading")
                                                .put("title", "Bài đã sửa")
                                                .put("contentText", "Nội dung mới")))));

        CourseRevision draft = CourseRevision.builder()
                .id(revisionId)
                .course(course)
                .revisionNumber(2)
                .status(CourseRevisionStatus.DRAFT)
                .title("Draft revision")
                .contentSnapshotJson(snapshotChangedLesson)
                .build();

        when(clock.instant()).thenReturn(now);
        when(courseRevisionFeatureProperties.isWriteEnabled()).thenReturn(true);
        when(courseRevisionRepository.findById(revisionId)).thenReturn(Optional.of(draft));
        when(courseRevisionRepository.save(any(CourseRevision.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(moduleRepository.findById(1L)).thenReturn(Optional.of(existingModule));
        when(lessonRepository.findById(55L)).thenReturn(Optional.of(existingLesson));
        when(lessonRepository.save(any(Lesson.class))).thenAnswer(invocation -> {
            Lesson cloned = invocation.getArgument(0);
            cloned.setId(955L);
            return cloned;
        });

        CourseRevisionDTO result = courseRevisionService.submitRevision(revisionId, authorId);

        assertEquals(CourseRevisionStatus.PENDING, result.getStatus());
        JsonNode savedSnapshot = draft.getContentSnapshotJson();
        assertEquals(955L, savedSnapshot.path("modules").get(0).path("lessons").get(0).path("id").asLong());
    }

    @Test
    void submitRevision_clonesExistingQuizIdentityWhenAnswerChanged() {
        Long revisionId = 325L;
        Long authorId = 22L;
        Instant now = Instant.parse("2026-03-20T13:30:00Z");

        Course course = Course.builder()
                .id(100L)
                .author(User.builder().id(authorId).build())
                .status(CourseStatus.PUBLIC)
                .title("Public course")
                .build();

        Module existingModule = Module.builder()
                .id(1L)
                .course(course)
                .title("Module 1")
                .build();

        QuizOption oldOptionA = QuizOption.builder()
                .id(1001L)
                .optionText("2")
                .isCorrect(true)
                .orderIndex(0)
                .build();
        QuizOption oldOptionB = QuizOption.builder()
                .id(1002L)
                .optionText("3")
                .isCorrect(false)
                .orderIndex(1)
                .build();
        QuizQuestion oldQuestion = QuizQuestion.builder()
                .id(901L)
                .questionText("1 + 1 = ?")
                .questionType(QuestionType.MULTIPLE_CHOICE)
                .score(1)
                .orderIndex(0)
                .options(List.of(oldOptionA, oldOptionB))
                .build();
        Quiz existingQuiz = Quiz.builder()
                .id(66L)
                .module(existingModule)
                .title("Quiz cũ")
                .description("Mô tả")
                .passScore(80)
                .orderIndex(0)
                .questions(List.of(oldQuestion))
                .build();
        oldQuestion.setQuiz(existingQuiz);
        oldOptionA.setQuestion(oldQuestion);
        oldOptionB.setQuestion(oldQuestion);

        JsonNode snapshotChangedQuiz = objectMapper.createObjectNode()
                .put("snapshotVersion", 1)
                .set("modules", objectMapper.createArrayNode()
                        .add(objectMapper.createObjectNode()
                                .put("id", 1)
                                .set("lessons", objectMapper.createArrayNode()
                                        .add(objectMapper.createObjectNode()
                                                .put("id", 66)
                                                .put("type", "quiz")
                                                .put("title", "Quiz cũ")
                                                .put("passScore", 80)
                                                .set("questions", objectMapper.createArrayNode()
                                                        .add(objectMapper.createObjectNode()
                                                                .put("text", "1 + 1 = ?")
                                                                .put("type", "MULTIPLE_CHOICE")
                                                                .put("score", 1)
                                                                .set("options", objectMapper.createArrayNode()
                                                                        .add(objectMapper.createObjectNode().put("text", "2").put("correct", false))
                                                                        .add(objectMapper.createObjectNode().put("text", "3").put("correct", true)))))))));

        CourseRevision draft = CourseRevision.builder()
                .id(revisionId)
                .course(course)
                .revisionNumber(2)
                .status(CourseRevisionStatus.DRAFT)
                .title("Draft revision")
                .contentSnapshotJson(snapshotChangedQuiz)
                .build();

        when(clock.instant()).thenReturn(now);
        when(courseRevisionFeatureProperties.isWriteEnabled()).thenReturn(true);
        when(courseRevisionRepository.findById(revisionId)).thenReturn(Optional.of(draft));
        when(courseRevisionRepository.save(any(CourseRevision.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(moduleRepository.findById(1L)).thenReturn(Optional.of(existingModule));
        when(quizRepository.findById(66L)).thenReturn(Optional.of(existingQuiz));
        when(quizRepository.save(any(Quiz.class))).thenAnswer(invocation -> {
            Quiz cloned = invocation.getArgument(0);
            cloned.setId(966L);
            return cloned;
        });

        CourseRevisionDTO result = courseRevisionService.submitRevision(revisionId, authorId);

        assertEquals(CourseRevisionStatus.PENDING, result.getStatus());
        JsonNode savedSnapshot = draft.getContentSnapshotJson();
        assertEquals(966L, savedSnapshot.path("modules").get(0).path("lessons").get(0).path("id").asLong());
    }

    @Test
    void submitRevision_addLessonInSameModule_keepsExistingQuizAndAssignmentIdentity() {
        Long revisionId = 327L;
        Long authorId = 22L;
        Instant now = Instant.parse("2026-03-20T15:10:00Z");

        Course course = Course.builder()
                .id(100L)
                .author(User.builder().id(authorId).build())
                .status(CourseStatus.PUBLIC)
                .title("Public course")
                .build();

        Module existingModule = Module.builder()
                .id(1L)
                .course(course)
                .title("Module 1")
                .build();

        QuizOption optionA = QuizOption.builder()
                .id(2101L)
                .optionText("2")
                .isCorrect(true)
                .orderIndex(0)
                .build();
        QuizOption optionB = QuizOption.builder()
                .id(2102L)
                .optionText("3")
                .isCorrect(false)
                .orderIndex(1)
                .build();
        QuizQuestion question = QuizQuestion.builder()
                .id(2201L)
                .questionText("1 + 1 = ?")
                .questionType(QuestionType.MULTIPLE_CHOICE)
                .score(1)
                .orderIndex(0)
                .options(List.of(optionA, optionB))
                .build();
        Quiz existingQuiz = Quiz.builder()
                .id(66L)
                .module(existingModule)
                .title("Quiz cũ")
                .description("Mô tả quiz")
                .passScore(80)
                .orderIndex(0)
                .questions(List.of(question))
                .build();
        question.setQuiz(existingQuiz);
        optionA.setQuestion(question);
        optionB.setQuestion(question);

        AssignmentCriteria criteria = AssignmentCriteria.builder()
                .id(2301L)
                .name("Tiêu chí 1")
                .description("Mô tả")
                .maxPoints(new BigDecimal("100"))
                .orderIndex(0)
                .isRequired(true)
                .build();
        Assignment existingAssignment = Assignment.builder()
                .id(77L)
                .module(existingModule)
                .title("Assignment cũ")
                .description("Mô tả assignment")
                .submissionType(SubmissionType.TEXT)
                .maxScore(new BigDecimal("100"))
                .passingScore(new BigDecimal("50"))
                .orderIndex(1)
                .isRequired(true)
                .criteria(List.of(criteria))
                .build();
        criteria.setAssignment(existingAssignment);

        JsonNode snapshotAddLesson = objectMapper.createObjectNode()
                .put("snapshotVersion", 1)
                .set("modules", objectMapper.createArrayNode()
                        .add(objectMapper.createObjectNode()
                                .put("id", 1)
                                .set("lessons", objectMapper.createArrayNode()
                                        .add(objectMapper.createObjectNode()
                                                .put("id", 66)
                                                .put("type", "quiz")
                                                .put("title", "Quiz cũ")
                                                .put("quizDescription", "Mô tả quiz")
                                                .put("passScore", 80)
                                                .set("questions", objectMapper.createArrayNode()
                                                        .add(objectMapper.createObjectNode()
                                                                .put("text", "1 + 1 = ?")
                                                                .put("type", "MULTIPLE_CHOICE")
                                                                .put("score", 1)
                                                                .set("options", objectMapper.createArrayNode()
                                                                        .add(objectMapper.createObjectNode().put("text", "2").put("correct", true))
                                                                        .add(objectMapper.createObjectNode().put("text", "3").put("correct", false))))))
                                        .add(objectMapper.createObjectNode()
                                                .put("id", 77)
                                                .put("type", "assignment")
                                                .put("title", "Assignment cũ")
                                                .put("assignmentDescription", "Mô tả assignment")
                                                .put("assignmentSubmissionType", "TEXT")
                                                .put("assignmentMaxScore", "100")
                                                .put("assignmentPassingScore", "50")
                                                .put("isRequired", true)
                                                .set("assignmentCriteria", objectMapper.createArrayNode()
                                                        .add(objectMapper.createObjectNode()
                                                                .put("name", "Tiêu chí 1")
                                                                .put("description", "Mô tả")
                                                                .put("maxPoints", "100")
                                                                .put("isRequired", true))))
                                        .add(objectMapper.createObjectNode()
                                                .put("type", "reading")
                                                .put("title", "Lesson mới")
                                                .put("contentText", "Nội dung mới")))));

        CourseRevision draft = CourseRevision.builder()
                .id(revisionId)
                .course(course)
                .revisionNumber(2)
                .status(CourseRevisionStatus.DRAFT)
                .title("Draft revision")
                .contentSnapshotJson(snapshotAddLesson)
                .build();

        when(clock.instant()).thenReturn(now);
        when(courseRevisionFeatureProperties.isWriteEnabled()).thenReturn(true);
        when(courseRevisionRepository.findById(revisionId)).thenReturn(Optional.of(draft));
        when(courseRevisionRepository.save(any(CourseRevision.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(moduleRepository.findById(1L)).thenReturn(Optional.of(existingModule));
        when(quizRepository.findById(66L)).thenReturn(Optional.of(existingQuiz));
        when(assignmentRepository.findById(77L)).thenReturn(Optional.of(existingAssignment));
        when(lessonRepository.save(any(Lesson.class))).thenAnswer(invocation -> {
            Lesson newLesson = invocation.getArgument(0);
            newLesson.setId(889L);
            return newLesson;
        });

        CourseRevisionDTO result = courseRevisionService.submitRevision(revisionId, authorId);

        assertEquals(CourseRevisionStatus.PENDING, result.getStatus());
        JsonNode savedSnapshot = draft.getContentSnapshotJson();
        assertEquals(66L, savedSnapshot.path("modules").get(0).path("lessons").get(0).path("id").asLong());
        assertEquals(77L, savedSnapshot.path("modules").get(0).path("lessons").get(1).path("id").asLong());
        assertEquals(889L, savedSnapshot.path("modules").get(0).path("lessons").get(2).path("id").asLong());
        verify(quizRepository, never()).save(any(Quiz.class));
        verify(assignmentRepository, never()).save(any(Assignment.class));
    }

    @Test
    void submitRevision_missingStableLessonAndAssignmentIds_reusesExistingIdentity() {
        Long revisionId = 327L;
        Long authorId = 22L;
        Instant now = Instant.parse("2026-03-20T15:10:00Z");

        Course course = Course.builder()
                .id(100L)
                .author(User.builder().id(authorId).build())
                .status(CourseStatus.PUBLIC)
                .title("Public course")
                .build();

        Module existingModule = Module.builder()
                .id(1L)
                .course(course)
                .title("Module 1")
                .build();

        Lesson existingLesson = Lesson.builder()
                .id(101L)
                .module(existingModule)
                .title("Lesson cũ")
                .contentText("Nội dung cũ")
                .videoUrl(null)
                .type(LessonType.READING)
                .orderIndex(0)
                .build();

        AssignmentCriteria criteria = AssignmentCriteria.builder()
                .id(2301L)
                .name("Tiêu chí 1")
                .description("Mô tả")
                .maxPoints(new BigDecimal("100"))
                .orderIndex(0)
                .isRequired(true)
                .build();
        Assignment existingAssignment = Assignment.builder()
                .id(77L)
                .module(existingModule)
                .title("Assignment cũ")
                .description("Mô tả assignment")
                .submissionType(SubmissionType.TEXT)
                .maxScore(new BigDecimal("100"))
                .passingScore(new BigDecimal("50"))
                .orderIndex(1)
                .isRequired(true)
                .criteria(List.of(criteria))
                .build();
        criteria.setAssignment(existingAssignment);

        JsonNode snapshotMissingIds = objectMapper.createObjectNode()
                .put("snapshotVersion", 1)
                .set("modules", objectMapper.createArrayNode()
                        .add(objectMapper.createObjectNode()
                                .put("id", 1)
                                .set("lessons", objectMapper.createArrayNode()
                                        .add(objectMapper.createObjectNode()
                                                .put("type", "reading")
                                                .put("title", "Lesson cũ")
                                                .put("contentText", "Nội dung cũ"))
                                        .add(objectMapper.createObjectNode()
                                                .put("type", "assignment")
                                                .put("title", "Assignment cũ")
                                                .put("assignmentDescription", "Mô tả assignment")
                                                .put("assignmentSubmissionType", "TEXT")
                                                .put("assignmentMaxScore", "100")
                                                .put("assignmentPassingScore", "50")
                                                .put("isRequired", true)
                                                .set("assignmentCriteria", objectMapper.createArrayNode()
                                                        .add(objectMapper.createObjectNode()
                                                                .put("name", "Tiêu chí 1")
                                                                .put("description", "Mô tả")
                                                                .put("maxPoints", "100")
                                                                .put("isRequired", true))))
                                        .add(objectMapper.createObjectNode()
                                                .put("type", "reading")
                                                .put("title", "Lesson mới")
                                                .put("contentText", "Nội dung mới")))));

        CourseRevision draft = CourseRevision.builder()
                .id(revisionId)
                .course(course)
                .revisionNumber(2)
                .status(CourseRevisionStatus.DRAFT)
                .title("Draft revision")
                .contentSnapshotJson(snapshotMissingIds)
                .build();

        when(clock.instant()).thenReturn(now);
        when(courseRevisionFeatureProperties.isWriteEnabled()).thenReturn(true);
        when(courseRevisionRepository.findById(revisionId)).thenReturn(Optional.of(draft));
        when(courseRevisionRepository.save(any(CourseRevision.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(moduleRepository.findById(1L)).thenReturn(Optional.of(existingModule));
        when(lessonRepository.findByModuleIdOrderByOrderIndexAsc(1L)).thenReturn(List.of(existingLesson));
        when(assignmentRepository.findByModuleIdOrderByOrderIndexAsc(1L)).thenReturn(List.of(existingAssignment));
        when(lessonRepository.save(any(Lesson.class))).thenAnswer(invocation -> {
            Lesson newLesson = invocation.getArgument(0);
            newLesson.setId(889L);
            return newLesson;
        });

        CourseRevisionDTO result = courseRevisionService.submitRevision(revisionId, authorId);

        assertEquals(CourseRevisionStatus.PENDING, result.getStatus());
        JsonNode savedSnapshot = draft.getContentSnapshotJson();
        assertEquals(101L, savedSnapshot.path("modules").get(0).path("lessons").get(0).path("id").asLong());
        assertEquals(77L, savedSnapshot.path("modules").get(0).path("lessons").get(1).path("id").asLong());
        assertEquals(889L, savedSnapshot.path("modules").get(0).path("lessons").get(2).path("id").asLong());
        verify(assignmentRepository, never()).save(any(Assignment.class));
    }

    @Test
    void updateRevision_normalizesCompatibilityMetadataFromTextualValues() throws Exception {
        Long revisionId = 360L;
        Long authorId = 22L;

        Course course = Course.builder()
                .id(100L)
                .author(User.builder().id(authorId).build())
                .status(CourseStatus.PUBLIC)
                .title("Public course")
                .build();

        CourseRevision revision = CourseRevision.builder()
                .id(revisionId)
                .course(course)
                .revisionNumber(2)
                .status(CourseRevisionStatus.DRAFT)
                .title("Draft")
                .build();

        CourseRevisionUpdateDTO dto = new CourseRevisionUpdateDTO();
        dto.setContentSnapshotJson("""
                {
                  "snapshotVersion": 1,
                  "compatibility": {
                    "autoCompatibleOnly": "false",
                    "level": "auto_compatible_only"
                  },
                  "modules": []
                }
                """);

        when(courseRevisionFeatureProperties.isWriteEnabled()).thenReturn(true);
        when(courseRevisionRepository.findById(revisionId)).thenReturn(Optional.of(revision));
        when(courseRevisionRepository.save(any(CourseRevision.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CourseRevisionDTO updated = courseRevisionService.updateRevision(revisionId, dto, authorId, null);

        JsonNode snapshot = objectMapper.readTree(updated.getContentSnapshotJson());
        assertTrue(snapshot.path("compatibility").path("autoCompatibleOnly").isBoolean());
        assertEquals(false, snapshot.path("compatibility").path("autoCompatibleOnly").asBoolean());
                assertEquals("NON_BREAKING", snapshot.path("compatibility").path("level").asText());
    }

    @Test
    void submitRevision_reusesQuizIdentityWhenPassScoreFieldMissing() {
        Long revisionId = 361L;
        Long authorId = 22L;
        Instant now = Instant.parse("2026-03-20T14:00:00Z");

        Course course = Course.builder()
                .id(100L)
                .author(User.builder().id(authorId).build())
                .status(CourseStatus.PUBLIC)
                .title("Public course")
                .build();

        Module existingModule = Module.builder()
                .id(1L)
                .course(course)
                .title("Module 1")
                .build();

        Quiz existingQuiz = Quiz.builder()
                .id(66L)
                .module(existingModule)
                .title("Quiz cũ")
                .description("Mô tả")
                .passScore(80)
                .orderIndex(0)
                .build();

        JsonNode snapshotMissingPassScore = objectMapper.createObjectNode()
                .put("snapshotVersion", 1)
                .set("modules", objectMapper.createArrayNode()
                        .add(objectMapper.createObjectNode()
                                .put("id", 1)
                                .set("lessons", objectMapper.createArrayNode()
                                        .add(objectMapper.createObjectNode()
                                                .put("id", 66)
                                                .put("type", "quiz")
                                                .put("title", "Quiz cũ")
                                                .put("quizDescription", "Mô tả")))));

        CourseRevision draft = CourseRevision.builder()
                .id(revisionId)
                .course(course)
                .revisionNumber(2)
                .status(CourseRevisionStatus.DRAFT)
                .title("Draft revision")
                .contentSnapshotJson(snapshotMissingPassScore)
                .build();

        when(clock.instant()).thenReturn(now);
        when(courseRevisionFeatureProperties.isWriteEnabled()).thenReturn(true);
        when(courseRevisionRepository.findById(revisionId)).thenReturn(Optional.of(draft));
        when(courseRevisionRepository.save(any(CourseRevision.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(moduleRepository.findById(1L)).thenReturn(Optional.of(existingModule));
        when(quizRepository.findById(66L)).thenReturn(Optional.of(existingQuiz));

        CourseRevisionDTO result = courseRevisionService.submitRevision(revisionId, authorId);

        assertEquals(CourseRevisionStatus.PENDING, result.getStatus());
        JsonNode savedSnapshot = draft.getContentSnapshotJson();
        assertEquals(66L, savedSnapshot.path("modules").get(0).path("lessons").get(0).path("id").asLong());
        verify(quizRepository, never()).save(any(Quiz.class));
    }

    @Test
    void submitRevision_clonesQuizIdentityWhenPassScoreExplicitNull() {
        Long revisionId = 362L;
        Long authorId = 22L;
        Instant now = Instant.parse("2026-03-20T14:05:00Z");

        Course course = Course.builder()
                .id(100L)
                .author(User.builder().id(authorId).build())
                .status(CourseStatus.PUBLIC)
                .title("Public course")
                .build();

        Module existingModule = Module.builder()
                .id(1L)
                .course(course)
                .title("Module 1")
                .build();

        Quiz existingQuiz = Quiz.builder()
                .id(66L)
                .module(existingModule)
                .title("Quiz cũ")
                .description("Mô tả")
                .passScore(80)
                .orderIndex(0)
                .build();

        ObjectNode quizNode = objectMapper.createObjectNode();
        quizNode.put("id", 66);
        quizNode.put("type", "quiz");
        quizNode.put("title", "Quiz cũ");
        quizNode.put("quizDescription", "Mô tả");
        quizNode.putNull("passScore");

        JsonNode snapshotNullPassScore = objectMapper.createObjectNode()
                .put("snapshotVersion", 1)
                .set("modules", objectMapper.createArrayNode()
                        .add(objectMapper.createObjectNode()
                                .put("id", 1)
                                .set("lessons", objectMapper.createArrayNode().add(quizNode))));

        CourseRevision draft = CourseRevision.builder()
                .id(revisionId)
                .course(course)
                .revisionNumber(2)
                .status(CourseRevisionStatus.DRAFT)
                .title("Draft revision")
                .contentSnapshotJson(snapshotNullPassScore)
                .build();

        when(clock.instant()).thenReturn(now);
        when(courseRevisionFeatureProperties.isWriteEnabled()).thenReturn(true);
        when(courseRevisionRepository.findById(revisionId)).thenReturn(Optional.of(draft));
        when(courseRevisionRepository.save(any(CourseRevision.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(moduleRepository.findById(1L)).thenReturn(Optional.of(existingModule));
        when(quizRepository.findById(66L)).thenReturn(Optional.of(existingQuiz));
        when(quizRepository.save(any(Quiz.class))).thenAnswer(invocation -> {
            Quiz cloned = invocation.getArgument(0);
            cloned.setId(966L);
            return cloned;
        });

        CourseRevisionDTO result = courseRevisionService.submitRevision(revisionId, authorId);

        assertEquals(CourseRevisionStatus.PENDING, result.getStatus());
        JsonNode savedSnapshot = draft.getContentSnapshotJson();
        assertEquals(966L, savedSnapshot.path("modules").get(0).path("lessons").get(0).path("id").asLong());
        verify(quizRepository).save(any(Quiz.class));
    }

    @Test
    void submitRevision_reusesExistingQuizIdentityWhenOnlyModulePlacementChanges() {
        Long revisionId = 326L;
        Long authorId = 22L;
        Instant now = Instant.parse("2026-03-20T13:35:00Z");

        Course course = Course.builder()
                .id(100L)
                .author(User.builder().id(authorId).build())
                .status(CourseStatus.PUBLIC)
                .title("Public course")
                .build();

        Module oldModule = Module.builder()
                .id(1L)
                .course(course)
                .title("Module old")
                .build();
        Module newModule = Module.builder()
                .id(2L)
                .course(course)
                .title("Module new")
                .build();

        QuizOption optionA = QuizOption.builder()
                .id(1101L)
                .optionText("Đúng")
                .isCorrect(true)
                .orderIndex(0)
                .build();
        QuizQuestion question = QuizQuestion.builder()
                .id(1001L)
                .questionText("Git là gì?")
                .questionType(QuestionType.MULTIPLE_CHOICE)
                .score(1)
                .orderIndex(0)
                .options(List.of(optionA))
                .build();
        Quiz existingQuiz = Quiz.builder()
                .id(66L)
                .module(oldModule)
                .title("Quiz cũ")
                .description("Mô tả")
                .passScore(80)
                .orderIndex(0)
                .questions(List.of(question))
                .build();
        question.setQuiz(existingQuiz);
        optionA.setQuestion(question);

        JsonNode snapshotMovedQuiz = objectMapper.createObjectNode()
                .put("snapshotVersion", 1)
                .set("modules", objectMapper.createArrayNode()
                        .add(objectMapper.createObjectNode()
                                .put("id", 2)
                                .set("lessons", objectMapper.createArrayNode()
                                        .add(objectMapper.createObjectNode()
                                                .put("id", 66)
                                                .put("type", "quiz")
                                                .put("title", "Quiz cũ")
                                                .put("quizDescription", "Mô tả")
                                                .put("passScore", 80)
                                                .set("questions", objectMapper.createArrayNode()
                                                        .add(objectMapper.createObjectNode()
                                                                .put("text", "Git là gì?")
                                                                .put("type", "MULTIPLE_CHOICE")
                                                                .put("score", 1)
                                                                .put("orderIndex", 0)
                                                                .set("options", objectMapper.createArrayNode()
                                                                        .add(objectMapper.createObjectNode()
                                                                                .put("text", "Đúng")
                                                                                .put("correct", true)
                                                                                .put("orderIndex", 0)))))))));

        CourseRevision draft = CourseRevision.builder()
                .id(revisionId)
                .course(course)
                .revisionNumber(2)
                .status(CourseRevisionStatus.DRAFT)
                .title("Draft revision")
                .contentSnapshotJson(snapshotMovedQuiz)
                .build();

        when(clock.instant()).thenReturn(now);
        when(courseRevisionFeatureProperties.isWriteEnabled()).thenReturn(true);
        when(courseRevisionRepository.findById(revisionId)).thenReturn(Optional.of(draft));
        when(courseRevisionRepository.save(any(CourseRevision.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(moduleRepository.findById(2L)).thenReturn(Optional.of(newModule));
        when(quizRepository.findById(66L)).thenReturn(Optional.of(existingQuiz));

        CourseRevisionDTO result = courseRevisionService.submitRevision(revisionId, authorId);

        assertEquals(CourseRevisionStatus.PENDING, result.getStatus());
        JsonNode savedSnapshot = draft.getContentSnapshotJson();
        assertEquals(66L, savedSnapshot.path("modules").get(0).path("lessons").get(0).path("id").asLong());
        verify(quizRepository, never()).save(any(Quiz.class));
    }

    @Test
    void submitRevision_reusesExistingQuizIdentityWhenOptionalFieldsAreMissingInSnapshot() {
        Long revisionId = 327L;
        Long authorId = 22L;
        Instant now = Instant.parse("2026-03-20T13:40:00Z");

        Course course = Course.builder()
                .id(100L)
                .author(User.builder().id(authorId).build())
                .status(CourseStatus.PUBLIC)
                .title("Public course")
                .build();

        Module existingModule = Module.builder()
                .id(1L)
                .course(course)
                .title("Module 1")
                .build();

        QuizOption optionA = QuizOption.builder()
                .id(1201L)
                .optionText("Đúng")
                .isCorrect(true)
                .orderIndex(0)
                .build();
        QuizQuestion question = QuizQuestion.builder()
                .id(1101L)
                .questionText("Git là gì?")
                .questionType(QuestionType.MULTIPLE_CHOICE)
                .score(1)
                .orderIndex(0)
                .options(List.of(optionA))
                .build();
        Quiz existingQuiz = Quiz.builder()
                .id(67L)
                .module(existingModule)
                .title("Quiz ổn định")
                .description("Mô tả")
                .passScore(80)
                .maxAttempts(3)
                .roundingIncrement(1)
                .orderIndex(0)
                .questions(List.of(question))
                .build();
        question.setQuiz(existingQuiz);
        optionA.setQuestion(question);

        JsonNode snapshotWithoutOptionalFields = objectMapper.createObjectNode()
                .put("snapshotVersion", 1)
                .set("modules", objectMapper.createArrayNode()
                        .add(objectMapper.createObjectNode()
                                .put("id", 1)
                                .set("lessons", objectMapper.createArrayNode()
                                        .add(objectMapper.createObjectNode()
                                                .put("id", 67)
                                                .put("type", "quiz")
                                                .put("title", "Quiz ổn định")
                                                .put("quizDescription", "Mô tả")
                                                .put("passScore", 80)
                                                .set("questions", objectMapper.createArrayNode()
                                                        .add(objectMapper.createObjectNode()
                                                                .put("text", "Git là gì?")
                                                                .put("type", "MULTIPLE_CHOICE")
                                                                .put("score", 1)
                                                                .put("orderIndex", 0)
                                                                .set("options", objectMapper.createArrayNode()
                                                                        .add(objectMapper.createObjectNode()
                                                                                .put("text", "Đúng")
                                                                                .put("correct", true)
                                                                                .put("orderIndex", 0)))))))));

        CourseRevision draft = CourseRevision.builder()
                .id(revisionId)
                .course(course)
                .revisionNumber(2)
                .status(CourseRevisionStatus.DRAFT)
                .title("Draft revision")
                .contentSnapshotJson(snapshotWithoutOptionalFields)
                .build();

        when(clock.instant()).thenReturn(now);
        when(courseRevisionFeatureProperties.isWriteEnabled()).thenReturn(true);
        when(courseRevisionRepository.findById(revisionId)).thenReturn(Optional.of(draft));
        when(courseRevisionRepository.save(any(CourseRevision.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(moduleRepository.findById(1L)).thenReturn(Optional.of(existingModule));
        when(quizRepository.findById(67L)).thenReturn(Optional.of(existingQuiz));

        CourseRevisionDTO result = courseRevisionService.submitRevision(revisionId, authorId);

        assertEquals(CourseRevisionStatus.PENDING, result.getStatus());
        JsonNode savedSnapshot = draft.getContentSnapshotJson();
        assertEquals(67L, savedSnapshot.path("modules").get(0).path("lessons").get(0).path("id").asLong());
        verify(quizRepository, never()).save(any(Quiz.class));
    }

    @Test
    void submitRevision_reusesExistingAssignmentIdentityWhenIsRequiredMissingInSnapshot() {
        Long revisionId = 328L;
        Long authorId = 22L;
        Instant now = Instant.parse("2026-03-20T13:42:00Z");

        Course course = Course.builder()
                .id(100L)
                .author(User.builder().id(authorId).build())
                .status(CourseStatus.PUBLIC)
                .title("Public course")
                .build();

        Module existingModule = Module.builder()
                .id(1L)
                .course(course)
                .title("Module 1")
                .build();

        Assignment existingAssignment = Assignment.builder()
                .id(68L)
                .module(existingModule)
                .title("Assignment ổn định")
                .description("Mô tả bài tập")
                .submissionType(SubmissionType.TEXT)
                .maxScore(new BigDecimal("100"))
                .passingScore(new BigDecimal("70"))
                .isRequired(false)
                .orderIndex(0)
                .criteria(List.of())
                .build();

        JsonNode snapshotWithoutRequiredFlag = objectMapper.createObjectNode()
                .put("snapshotVersion", 1)
                .set("modules", objectMapper.createArrayNode()
                        .add(objectMapper.createObjectNode()
                                .put("id", 1)
                                .set("lessons", objectMapper.createArrayNode()
                                        .add(objectMapper.createObjectNode()
                                                .put("id", 68)
                                                .put("type", "assignment")
                                                .put("title", "Assignment ổn định")
                                                .put("assignmentDescription", "Mô tả bài tập")
                                                .put("assignmentSubmissionType", "TEXT")
                                                .put("assignmentMaxScore", "100")
                                                .put("assignmentPassingScore", "70")
                                                .set("assignmentCriteria", objectMapper.createArrayNode())))));

        CourseRevision draft = CourseRevision.builder()
                .id(revisionId)
                .course(course)
                .revisionNumber(2)
                .status(CourseRevisionStatus.DRAFT)
                .title("Draft revision")
                .contentSnapshotJson(snapshotWithoutRequiredFlag)
                .build();

        when(clock.instant()).thenReturn(now);
        when(courseRevisionFeatureProperties.isWriteEnabled()).thenReturn(true);
        when(courseRevisionRepository.findById(revisionId)).thenReturn(Optional.of(draft));
        when(courseRevisionRepository.save(any(CourseRevision.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(moduleRepository.findById(1L)).thenReturn(Optional.of(existingModule));
        when(assignmentRepository.findById(68L)).thenReturn(Optional.of(existingAssignment));

        CourseRevisionDTO result = courseRevisionService.submitRevision(revisionId, authorId);

        assertEquals(CourseRevisionStatus.PENDING, result.getStatus());
        JsonNode savedSnapshot = draft.getContentSnapshotJson();
        assertEquals(68L, savedSnapshot.path("modules").get(0).path("lessons").get(0).path("id").asLong());
        verify(assignmentRepository, never()).save(any(Assignment.class));
    }

    @Test
    void submitRevision_addingLessonInSameModule_keepsQuizIdentityWhenOptionalFieldsAreNull() {
        Long revisionId = 329L;
        Long authorId = 22L;
        Instant now = Instant.parse("2026-03-20T13:44:00Z");

        Course course = Course.builder()
                .id(100L)
                .author(User.builder().id(authorId).build())
                .status(CourseStatus.PUBLIC)
                .title("Public course")
                .build();

        Module existingModule = Module.builder()
                .id(1L)
                .course(course)
                .title("Module 1")
                .build();

        QuizOption optionA = QuizOption.builder()
                .id(1301L)
                .optionText("Đúng")
                .isCorrect(true)
                .orderIndex(0)
                .build();
        QuizQuestion question = QuizQuestion.builder()
                .id(1201L)
                .questionText("Git là gì?")
                .questionType(QuestionType.MULTIPLE_CHOICE)
                .score(1)
                .orderIndex(0)
                .options(List.of(optionA))
                .build();
        Quiz existingQuiz = Quiz.builder()
                .id(69L)
                .module(existingModule)
                .title("Quiz ổn định")
                .description("Mô tả")
                .passScore(80)
                .orderIndex(0)
                .questions(List.of(question))
                .build();
        question.setQuiz(existingQuiz);
        optionA.setQuestion(question);

        ObjectNode existingQuizNode = objectMapper.createObjectNode()
                .put("id", 69)
                .put("type", "quiz")
                .put("title", "Quiz ổn định")
                .put("quizDescription", "Mô tả")
                .put("passScore", 80);
        existingQuizNode.set("questions", objectMapper.createArrayNode()
                .add(objectMapper.createObjectNode()
                        .put("text", "Git là gì?")
                        .put("type", "MULTIPLE_CHOICE")
                        .put("score", 1)
                        .put("orderIndex", 0)
                        .set("options", objectMapper.createArrayNode()
                                .add(objectMapper.createObjectNode()
                                        .put("text", "Đúng")
                                        .put("correct", true)
                                        .put("orderIndex", 0)))));

        JsonNode snapshotWithAddedLesson = objectMapper.createObjectNode()
                .put("snapshotVersion", 1)
                .set("modules", objectMapper.createArrayNode()
                        .add(objectMapper.createObjectNode()
                                .put("id", 1)
                                .set("lessons", objectMapper.createArrayNode()
                                        .add(existingQuizNode)
                                        .add(objectMapper.createObjectNode()
                                                .put("type", "reading")
                                                .put("title", "Bài học mới")
                                                .put("contentText", "Nội dung mới")))));

        CourseRevision draft = CourseRevision.builder()
                .id(revisionId)
                .course(course)
                .revisionNumber(2)
                .status(CourseRevisionStatus.DRAFT)
                .title("Draft revision")
                .contentSnapshotJson(snapshotWithAddedLesson)
                .build();

        when(clock.instant()).thenReturn(now);
        when(courseRevisionFeatureProperties.isWriteEnabled()).thenReturn(true);
        when(courseRevisionRepository.findById(revisionId)).thenReturn(Optional.of(draft));
        when(courseRevisionRepository.save(any(CourseRevision.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(moduleRepository.findById(1L)).thenReturn(Optional.of(existingModule));
        when(quizRepository.findById(69L)).thenReturn(Optional.of(existingQuiz));

        CourseRevisionDTO result = courseRevisionService.submitRevision(revisionId, authorId);

        assertEquals(CourseRevisionStatus.PENDING, result.getStatus());
        JsonNode savedSnapshot = draft.getContentSnapshotJson();
        assertTrue(savedSnapshot.path("compatibility").path("autoCompatibleOnly").asBoolean());
        assertEquals(
                "NON_BREAKING",
                savedSnapshot.path("compatibility").path("level").asText()
        );
        assertEquals(69L, savedSnapshot.path("modules").get(0).path("lessons").get(0).path("id").asLong());
        assertTrue(savedSnapshot.path("modules").get(0).path("lessons").get(1).path("id").asLong() > 0);
        verify(quizRepository, never()).save(any(Quiz.class));
    }

    @Test
    void submitRevision_addingLessonInSameModule_keepsAssignmentIdentityWhenSubmissionTypeIsNull() {
        Long revisionId = 331L;
        Long authorId = 22L;
        Instant now = Instant.parse("2026-03-20T13:46:00Z");

        Course course = Course.builder()
                .id(100L)
                .author(User.builder().id(authorId).build())
                .status(CourseStatus.PUBLIC)
                .title("Public course")
                .build();

        Module existingModule = Module.builder()
                .id(1L)
                .course(course)
                .title("Module 1")
                .build();

        Assignment existingAssignment = Assignment.builder()
                .id(70L)
                .module(existingModule)
                .title("Assignment ổn định")
                .description("Mô tả bài tập")
                .submissionType(SubmissionType.FILE)
                .maxScore(new BigDecimal("100"))
                .passingScore(new BigDecimal("70"))
                .isRequired(true)
                .orderIndex(0)
                .criteria(List.of())
                .build();

        ObjectNode existingAssignmentNode = objectMapper.createObjectNode()
                .put("id", 70)
                .put("type", "assignment")
                .put("title", "Assignment ổn định")
                .put("assignmentDescription", "Mô tả bài tập")
                .put("assignmentMaxScore", "100")
                .put("assignmentPassingScore", "70")
                .put("isRequired", true)
                .set("assignmentCriteria", objectMapper.createArrayNode());
        existingAssignmentNode.putNull("assignmentSubmissionType");

        JsonNode snapshotWithAddedLesson = objectMapper.createObjectNode()
                .put("snapshotVersion", 1)
                .set("modules", objectMapper.createArrayNode()
                        .add(objectMapper.createObjectNode()
                                .put("id", 1)
                                .set("lessons", objectMapper.createArrayNode()
                                        .add(existingAssignmentNode)
                                        .add(objectMapper.createObjectNode()
                                                .put("type", "reading")
                                                .put("title", "Bài học mới")
                                                .put("contentText", "Nội dung mới")))));

        CourseRevision draft = CourseRevision.builder()
                .id(revisionId)
                .course(course)
                .revisionNumber(2)
                .status(CourseRevisionStatus.DRAFT)
                .title("Draft revision")
                .contentSnapshotJson(snapshotWithAddedLesson)
                .build();

        when(clock.instant()).thenReturn(now);
        when(courseRevisionFeatureProperties.isWriteEnabled()).thenReturn(true);
        when(courseRevisionRepository.findById(revisionId)).thenReturn(Optional.of(draft));
        when(courseRevisionRepository.save(any(CourseRevision.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(moduleRepository.findById(1L)).thenReturn(Optional.of(existingModule));
        when(assignmentRepository.findById(70L)).thenReturn(Optional.of(existingAssignment));

        CourseRevisionDTO result = courseRevisionService.submitRevision(revisionId, authorId);

        assertEquals(CourseRevisionStatus.PENDING, result.getStatus());
        JsonNode savedSnapshot = draft.getContentSnapshotJson();
        assertEquals(70L, savedSnapshot.path("modules").get(0).path("lessons").get(0).path("id").asLong());
        assertTrue(savedSnapshot.path("modules").get(0).path("lessons").get(1).path("id").asLong() > 0);
        verify(assignmentRepository, never()).save(any(Assignment.class));
    }

    @Test
    void submitRevision_addingLessonInSameModule_keepsAssignmentIdentityWhenCriteriaRequiredIsNull() {
        Long revisionId = 332L;
        Long authorId = 22L;
        Instant now = Instant.parse("2026-03-20T13:48:00Z");

        Course course = Course.builder()
                .id(100L)
                .author(User.builder().id(authorId).build())
                .status(CourseStatus.PUBLIC)
                .title("Public course")
                .build();

        Module existingModule = Module.builder()
                .id(1L)
                .course(course)
                .title("Module 1")
                .build();

        AssignmentCriteria criteria = AssignmentCriteria.builder()
                .id(7001L)
                .name("Tiêu chí 1")
                .description("Mô tả")
                .maxPoints(new BigDecimal("30"))
                .orderIndex(0)
                .isRequired(true)
                .build();
        Assignment existingAssignment = Assignment.builder()
                .id(71L)
                .module(existingModule)
                .title("Assignment ổn định")
                .description("Mô tả bài tập")
                .submissionType(SubmissionType.TEXT)
                .maxScore(new BigDecimal("100"))
                .passingScore(new BigDecimal("70"))
                .isRequired(true)
                .orderIndex(0)
                .criteria(List.of(criteria))
                .build();
        criteria.setAssignment(existingAssignment);

        ObjectNode criteriaNode = objectMapper.createObjectNode()
                .put("id", 7001)
                .put("name", "Tiêu chí 1")
                .put("description", "Mô tả")
                .put("maxPoints", "30")
                .put("orderIndex", 0);
        criteriaNode.putNull("isRequired");

        JsonNode snapshotWithAddedLesson = objectMapper.createObjectNode()
                .put("snapshotVersion", 1)
                .set("modules", objectMapper.createArrayNode()
                        .add(objectMapper.createObjectNode()
                                .put("id", 1)
                                .set("lessons", objectMapper.createArrayNode()
                                        .add(objectMapper.createObjectNode()
                                                .put("id", 71)
                                                .put("type", "assignment")
                                                .put("title", "Assignment ổn định")
                                                .put("assignmentDescription", "Mô tả bài tập")
                                                .put("assignmentSubmissionType", "TEXT")
                                                .put("assignmentMaxScore", "100")
                                                .put("assignmentPassingScore", "70")
                                                .put("isRequired", true)
                                                .set("assignmentCriteria", objectMapper.createArrayNode().add(criteriaNode)))
                                        .add(objectMapper.createObjectNode()
                                                .put("type", "reading")
                                                .put("title", "Bài học mới")
                                                .put("contentText", "Nội dung mới")))));

        CourseRevision draft = CourseRevision.builder()
                .id(revisionId)
                .course(course)
                .revisionNumber(2)
                .status(CourseRevisionStatus.DRAFT)
                .title("Draft revision")
                .contentSnapshotJson(snapshotWithAddedLesson)
                .build();

        when(clock.instant()).thenReturn(now);
        when(courseRevisionFeatureProperties.isWriteEnabled()).thenReturn(true);
        when(courseRevisionRepository.findById(revisionId)).thenReturn(Optional.of(draft));
        when(courseRevisionRepository.save(any(CourseRevision.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(moduleRepository.findById(1L)).thenReturn(Optional.of(existingModule));
        when(assignmentRepository.findById(71L)).thenReturn(Optional.of(existingAssignment));

        CourseRevisionDTO result = courseRevisionService.submitRevision(revisionId, authorId);

        assertEquals(CourseRevisionStatus.PENDING, result.getStatus());
        JsonNode savedSnapshot = draft.getContentSnapshotJson();
        assertEquals(71L, savedSnapshot.path("modules").get(0).path("lessons").get(0).path("id").asLong());
        assertTrue(savedSnapshot.path("modules").get(0).path("lessons").get(1).path("id").asLong() > 0);
        verify(assignmentRepository, never()).save(any(Assignment.class));
    }

    @Test
    void submitThenApprove_materializesNewLessonQuizAssignmentIds_andApprovePassesIdentityCheck() {
        Long revisionId = 322L;
        Long authorId = 22L;
        Long adminId = 3L;
        Instant now = Instant.parse("2026-03-20T14:00:00Z");

        Course course = Course.builder()
                .id(100L)
                .author(User.builder().id(authorId).build())
                .status(CourseStatus.PUBLIC)
                .title("Public course")
                .activeRevisionId(300L)
                .latestRevisionId(300L)
                .build();

        ObjectNode snapshotWithMissingIds = objectMapper.createObjectNode();
        snapshotWithMissingIds.put("snapshotVersion", 1);

        ArrayNode modulesNode = objectMapper.createArrayNode();
        ObjectNode moduleNode = objectMapper.createObjectNode();
        moduleNode.put("id", 1);
        moduleNode.put("title", "Module 1");

        ArrayNode lessonsNode = objectMapper.createArrayNode();

        ObjectNode readingNode = objectMapper.createObjectNode();
        readingNode.put("type", "reading");
        readingNode.put("title", "Reading mới");
        lessonsNode.add(readingNode);

        ObjectNode quizNode = objectMapper.createObjectNode();
        quizNode.put("type", "quiz");
        quizNode.put("title", "Quiz mới");
        ArrayNode questionsNode = objectMapper.createArrayNode();
        ObjectNode questionNode = objectMapper.createObjectNode();
        questionNode.put("text", "1 + 1 = ?");
        questionNode.put("type", "MULTIPLE_CHOICE");
        questionNode.set("options", objectMapper.createArrayNode()
                .add(objectMapper.createObjectNode().put("text", "2").put("correct", true))
                .add(objectMapper.createObjectNode().put("text", "3").put("correct", false)));
        questionsNode.add(questionNode);
        quizNode.set("questions", questionsNode);
        lessonsNode.add(quizNode);

        ObjectNode assignmentNode = objectMapper.createObjectNode();
        assignmentNode.put("type", "assignment");
        assignmentNode.put("title", "Assignment mới");
        lessonsNode.add(assignmentNode);

        moduleNode.set("lessons", lessonsNode);
        modulesNode.add(moduleNode);
        snapshotWithMissingIds.set("modules", modulesNode);

        CourseRevision draft = CourseRevision.builder()
                .id(revisionId)
                .course(course)
                .revisionNumber(3)
                .status(CourseRevisionStatus.DRAFT)
                .title("Draft revision")
                .contentSnapshotJson(snapshotWithMissingIds)
                .build();

        Module existingModule = Module.builder()
                .id(1L)
                .course(course)
                .title("Module 1")
                .build();

        when(courseRevisionFeatureProperties.isWriteEnabled()).thenReturn(true);
        when(courseRevisionFeatureProperties.isApprovalEnabled()).thenReturn(true);
        when(clock.instant()).thenReturn(now);

        when(courseRevisionRepository.findById(revisionId)).thenReturn(Optional.of(draft));
        when(courseRevisionRepository.findByIdForApproval(revisionId)).thenReturn(Optional.of(draft));
        when(courseRepository.findByIdForRevisionApproval(course.getId())).thenReturn(Optional.of(course));
        when(courseRevisionRepository.save(any(CourseRevision.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(courseRepository.save(any(Course.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(moduleRepository.findById(1L)).thenReturn(Optional.of(existingModule));

        when(lessonRepository.save(any(Lesson.class))).thenAnswer(invocation -> {
            Lesson lesson = invocation.getArgument(0);
            lesson.setId(901L);
            return lesson;
        });
        when(quizRepository.save(any(Quiz.class))).thenAnswer(invocation -> {
            Quiz quiz = invocation.getArgument(0);
            quiz.setId(902L);
            return quiz;
        });
        when(assignmentRepository.save(any(Assignment.class))).thenAnswer(invocation -> {
            Assignment assignment = invocation.getArgument(0);
            assignment.setId(903L);
            return assignment;
        });

        // Mock skill sync for approve step (no skills in this test revision)
        lenient().when(courseSkillRepository.deleteByCourseId(course.getId())).thenReturn(0);
        lenient().when(skillRepository.findByNameIgnoreCase(anyString())).thenReturn(Optional.empty());

        CourseRevisionDTO submitResult = courseRevisionService.submitRevision(revisionId, authorId);
        assertEquals(CourseRevisionStatus.PENDING, submitResult.getStatus());

        JsonNode materializedSnapshot = draft.getContentSnapshotJson();
        assertEquals(901L, materializedSnapshot.path("modules").get(0).path("lessons").get(0).path("id").asLong());
        assertEquals(902L, materializedSnapshot.path("modules").get(0).path("lessons").get(1).path("id").asLong());
        assertEquals(903L, materializedSnapshot.path("modules").get(0).path("lessons").get(2).path("id").asLong());

        CourseRevisionDTO approveResult = courseRevisionService.approveRevision(revisionId, adminId);
        assertEquals(CourseRevisionStatus.APPROVED, approveResult.getStatus());
        assertEquals(revisionId, course.getActiveRevisionId());
        assertEquals(revisionId, course.getLatestRevisionId());
    }

    @Test
    void submitRevision_offMode_allowsWhenNoMeaningfulChanges() {
        Long revisionId = 312L;
        Long baselineRevisionId = 300L;
        Long authorId = 22L;
        Instant now = Instant.parse("2026-03-16T01:45:00Z");

        Course course = Course.builder()
                .id(100L)
                .author(User.builder().id(authorId).build())
                .status(CourseStatus.PUBLIC)
                .title("Public course")
                .activeRevisionId(baselineRevisionId)
                .build();

        CourseRevision baseline = CourseRevision.builder()
                .id(baselineRevisionId)
                .course(course)
                .revisionNumber(1)
                .status(CourseRevisionStatus.APPROVED)
                .title("Stable revision")
                .description("Same description")
                .shortDescription("Same short")
                .build();

        CourseRevision draft = CourseRevision.builder()
                .id(revisionId)
                .course(course)
                .revisionNumber(2)
                .status(CourseRevisionStatus.DRAFT)
                .title("Stable revision")
                .description("Same description")
                .shortDescription("Same short")
                .sourceRevisionId(baselineRevisionId)
                .build();
        draft.setBaselineSnapshotHash(computeSnapshotHashForDraft(draft));

        when(courseRevisionFeatureProperties.isWriteEnabled()).thenReturn(true);
        when(courseRevisionFeatureProperties.getSubmitChangeCheckMode())
                .thenReturn(CourseRevisionFeatureProperties.SubmitChangeCheckMode.OFF);
        when(courseRevisionRepository.findById(revisionId)).thenReturn(Optional.of(draft));
        when(clock.instant()).thenReturn(now);
        when(courseRevisionRepository.save(any(CourseRevision.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CourseRevisionDTO result = courseRevisionService.submitRevision(revisionId, authorId);

        assertEquals(CourseRevisionStatus.PENDING, result.getStatus());
        verify(courseRevisionRepository).save(argThat(saved -> saved != null
                && saved.getId().equals(revisionId)
                && saved.getStatus() == CourseRevisionStatus.PENDING));
    }

    @Test
    void submitRevision_throwsWhenRequireSourceBaselineEnabledAndSourceMissing() {
        Long revisionId = 313L;
        Long authorId = 22L;

        Course course = Course.builder()
                .id(100L)
                .author(User.builder().id(authorId).build())
                .status(CourseStatus.PUBLIC)
                .title("Public course")
                .build();

        CourseRevision draft = CourseRevision.builder()
                .id(revisionId)
                .course(course)
                .revisionNumber(2)
                .status(CourseRevisionStatus.DRAFT)
                .title("Draft revision")
                .build();

        when(courseRevisionFeatureProperties.isWriteEnabled()).thenReturn(true);
        when(courseRevisionFeatureProperties.isRequireSourceBaseline()).thenReturn(true);
        when(courseRevisionFeatureProperties.getRequireSourceBaselineRolloutPercent()).thenReturn(100);
        when(courseRevisionRepository.findById(revisionId)).thenReturn(Optional.of(draft));

        ConflictException exception = assertThrows(
                ConflictException.class,
                () -> courseRevisionService.submitRevision(revisionId, authorId)
        );

        assertTrue(exception.getMessage().contains("COURSE_REVISION_BASELINE_NOT_FOUND"));
        verify(courseRevisionRepository, never()).save(argThat(saved -> saved != null
                && saved.getId().equals(revisionId)
                && saved.getStatus() == CourseRevisionStatus.PENDING));
    }

    @Test
    void submitRevision_requireSourceBaselineCanaryOff_allowsSubmitWhenSourceMissing() {
        Long revisionId = 316L;
        Long authorId = 22L;
        Instant now = Instant.parse("2026-03-16T02:05:00Z");

        Course course = Course.builder()
                .id(121L)
                .author(User.builder().id(authorId).build())
                .status(CourseStatus.PUBLIC)
                .title("Public course")
                .description("Stable")
                .build();

        CourseRevision draft = CourseRevision.builder()
                .id(revisionId)
                .course(course)
                .revisionNumber(2)
                .status(CourseRevisionStatus.DRAFT)
                .title("Public course")
                .description("Stable but changed for submit")
                .build();

        when(courseRevisionFeatureProperties.isWriteEnabled()).thenReturn(true);
        when(courseRevisionFeatureProperties.isRequireSourceBaseline()).thenReturn(true);
        when(courseRevisionFeatureProperties.getRequireSourceBaselineRolloutPercent()).thenReturn(0);
        when(courseRevisionRepository.findById(revisionId)).thenReturn(Optional.of(draft));
        when(clock.instant()).thenReturn(now);
        when(courseRevisionRepository.save(any(CourseRevision.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CourseRevisionDTO result = courseRevisionService.submitRevision(revisionId, authorId);

        assertEquals(CourseRevisionStatus.PENDING, result.getStatus());
    }

    @Test
    void submitRevision_requireSourceBaselineCanaryOnForCourseBucket_blocksWhenSourceMissing() {
        Long revisionId = 317L;
        Long authorId = 22L;

        Course course = Course.builder()
                .id(21L)
                .author(User.builder().id(authorId).build())
                .status(CourseStatus.PUBLIC)
                .title("Public course")
                .build();

        CourseRevision draft = CourseRevision.builder()
                .id(revisionId)
                .course(course)
                .revisionNumber(2)
                .status(CourseRevisionStatus.DRAFT)
                .title("Draft revision")
                .build();

        when(courseRevisionFeatureProperties.isWriteEnabled()).thenReturn(true);
        when(courseRevisionFeatureProperties.isRequireSourceBaseline()).thenReturn(true);
        when(courseRevisionFeatureProperties.getRequireSourceBaselineRolloutPercent()).thenReturn(30);
        when(courseRevisionRepository.findById(revisionId)).thenReturn(Optional.of(draft));

        ConflictException exception = assertThrows(
                ConflictException.class,
                () -> courseRevisionService.submitRevision(revisionId, authorId)
        );

        assertTrue(exception.getMessage().contains("COURSE_REVISION_BASELINE_NOT_FOUND"));
    }

    @Test
    void submitRevision_usesPersistedBaselineHashWithoutResolvingMissingSource() {
        Long revisionId = 314L;
        Long missingSourceRevisionId = 999L;
        Long authorId = 22L;

        Course course = Course.builder()
                .id(100L)
                .author(User.builder().id(authorId).build())
                .status(CourseStatus.PUBLIC)
                .title("Public course")
                .build();

        CourseRevision draft = CourseRevision.builder()
                .id(revisionId)
                .course(course)
                .revisionNumber(2)
                .status(CourseRevisionStatus.DRAFT)
                .title("Stable revision")
                .description("Same description")
                .sourceRevisionId(missingSourceRevisionId)
                .build();
        draft.setBaselineSnapshotHash(computeSnapshotHashForDraft(draft));

        when(courseRevisionFeatureProperties.isWriteEnabled()).thenReturn(true);
        when(courseRevisionRepository.findById(revisionId)).thenReturn(Optional.of(draft));

        ConflictException exception = assertThrows(
                ConflictException.class,
                () -> courseRevisionService.submitRevision(revisionId, authorId)
        );

        assertTrue(exception.getMessage().contains("COURSE_REVISION_NO_CHANGES_TO_SUBMIT"));
        verify(courseRevisionRepository, never()).findById(missingSourceRevisionId);
    }

    @Test
    void submitRevision_allowsWhenPersistedBaselineHashDiffersAndSourceMissing() {
        Long revisionId = 315L;
        Long missingSourceRevisionId = 999L;
        Long authorId = 22L;
        Instant now = Instant.parse("2026-03-16T01:55:00Z");

        Course course = Course.builder()
                .id(100L)
                .author(User.builder().id(authorId).build())
                .status(CourseStatus.PUBLIC)
                .title("Public course")
                .build();

        CourseRevision draft = CourseRevision.builder()
                .id(revisionId)
                .course(course)
                .revisionNumber(2)
                .status(CourseRevisionStatus.DRAFT)
                .title("Stable revision")
                .description("Updated description")
                .sourceRevisionId(missingSourceRevisionId)
                .build();

        CourseRevision baselineLike = CourseRevision.builder()
                .title("Stable revision")
                .description("Same description")
                .build();
        draft.setBaselineSnapshotHash(computeSnapshotHashForDraft(baselineLike));

        when(courseRevisionFeatureProperties.isWriteEnabled()).thenReturn(true);
        when(courseRevisionRepository.findById(revisionId)).thenReturn(Optional.of(draft));
        when(clock.instant()).thenReturn(now);
        when(courseRevisionRepository.save(any(CourseRevision.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CourseRevisionDTO result = courseRevisionService.submitRevision(revisionId, authorId);

        assertEquals(CourseRevisionStatus.PENDING, result.getStatus());
        verify(courseRevisionRepository, never()).findById(missingSourceRevisionId);
        verify(courseRevisionRepository).save(argThat(saved -> saved != null
                && saved.getId().equals(revisionId)
                && saved.getStatus() == CourseRevisionStatus.PENDING));
    }

    @Test
    void submitRevision_throwsWhenNoChangesSinceRejectionEvenInDraft() {
        Long revisionId = 320L;
        Long baselineRevisionId = 300L;
        Long authorId = 22L;

        Course course = Course.builder()
                .id(100L)
                .author(User.builder().id(authorId).build())
                .status(CourseStatus.PUBLIC)
                .title("Public course")
                .activeRevisionId(baselineRevisionId)
                .build();

        CourseRevision baseline = CourseRevision.builder()
                .id(baselineRevisionId)
                .course(course)
                .revisionNumber(1)
                .status(CourseRevisionStatus.APPROVED)
                .title("Stable revision")
                .description("Old description")
                .build();

        CourseRevision draft = CourseRevision.builder()
                .id(revisionId)
                .course(course)
                .revisionNumber(2)
                .status(CourseRevisionStatus.DRAFT)
                .title("Stable revision")
                .description("Updated description")
                .build();

        when(courseRevisionFeatureProperties.isWriteEnabled()).thenReturn(true);
        when(courseRevisionRepository.findById(revisionId)).thenReturn(Optional.of(draft));
        when(courseRevisionRepository.findById(baselineRevisionId)).thenReturn(Optional.of(baseline));
        draft.setRejectedSnapshotHash(computeSnapshotHashForDraft(draft));

        ConflictException noChangesSinceReject = assertThrows(
                ConflictException.class,
                () -> courseRevisionService.submitRevision(revisionId, authorId)
        );
        assertTrue(noChangesSinceReject.getMessage().contains("COURSE_REVISION_NO_CHANGES_SINCE_REJECTION"));
        verify(courseRevisionRepository, never()).save(argThat(saved -> saved != null
                && saved.getStatus() == CourseRevisionStatus.PENDING
                && saved.getId().equals(revisionId)));
    }

    @Test
    void submitRevision_allowsWhenOnlyContentSnapshotChanged() {
        Long revisionId = 330L;
        Long baselineRevisionId = 300L;
        Long authorId = 22L;
        Instant now = Instant.parse("2026-03-16T02:00:00Z");

        Course course = Course.builder()
                .id(100L)
                .author(User.builder().id(authorId).build())
                .status(CourseStatus.PUBLIC)
                .title("Public course")
                .activeRevisionId(baselineRevisionId)
                .build();

        CourseRevision baseline = CourseRevision.builder()
                .id(baselineRevisionId)
                .course(course)
                .revisionNumber(1)
                .status(CourseRevisionStatus.APPROVED)
                .title("Stable revision")
                .description("Same description")
                .contentSnapshotJson(objectMapper.createObjectNode())
                .build();

        ObjectNode changedSnapshot = objectMapper.createObjectNode();
        changedSnapshot.put("snapshotVersion", 1);
        ObjectNode changedModule = objectMapper.createObjectNode();
        changedModule.put("id", 501);
        changedModule.put("title", "Module A");
        changedModule.putArray("lessons");
        changedSnapshot.putArray("modules").add(changedModule);

        CourseRevision draft = CourseRevision.builder()
                .id(revisionId)
                .course(course)
                .revisionNumber(2)
                .status(CourseRevisionStatus.DRAFT)
                .title("Stable revision")
                .description("Same description")
                .contentSnapshotJson(changedSnapshot)
                .sourceRevisionId(baselineRevisionId)
                .build();

        when(courseRevisionFeatureProperties.isWriteEnabled()).thenReturn(true);
        when(courseRevisionRepository.findById(revisionId)).thenReturn(Optional.of(draft));
        when(courseRevisionRepository.findById(baselineRevisionId)).thenReturn(Optional.of(baseline));
        when(clock.instant()).thenReturn(now);
        when(courseRevisionRepository.save(any(CourseRevision.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CourseRevisionDTO result = courseRevisionService.submitRevision(revisionId, authorId);

        assertEquals(CourseRevisionStatus.PENDING, result.getStatus());
        verify(courseRevisionRepository).save(argThat(saved -> saved != null
                && saved.getId().equals(revisionId)
                && saved.getStatus() == CourseRevisionStatus.PENDING));
    }

    @Test
    void updateRevision_throwsWhenContentSnapshotExceedsConfiguredLimit() {
        Long revisionId = 350L;
        Long authorId = 22L;

        Course course = Course.builder()
                .id(100L)
                .author(User.builder().id(authorId).build())
                .status(CourseStatus.PUBLIC)
                .build();

        CourseRevision draft = CourseRevision.builder()
                .id(revisionId)
                .course(course)
                .revisionNumber(2)
                .status(CourseRevisionStatus.DRAFT)
                .title("Draft revision")
                .build();

        CourseRevisionUpdateDTO dto = new CourseRevisionUpdateDTO();
        dto.setContentSnapshotJson("{\"modules\":[{\"id\":1,\"title\":\"very-large-content\"}]}");

        when(courseRevisionFeatureProperties.isWriteEnabled()).thenReturn(true);
        when(courseRevisionFeatureProperties.getMaxContentSnapshotBytes()).thenReturn(8);
        when(courseRevisionRepository.findById(revisionId)).thenReturn(Optional.of(draft));

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> courseRevisionService.updateRevision(revisionId, dto, authorId, null)
        );

        assertTrue(exception.getMessage().contains("COURSE_REVISION_CONTENT_SNAPSHOT_TOO_LARGE"));
        verify(courseRevisionRepository, never()).save(any(CourseRevision.class));
    }

    @Test
    void updateRevision_throwsWhenContentSnapshotVersionUnsupported() {
        Long revisionId = 360L;
        Long authorId = 22L;

        Course course = Course.builder()
                .id(100L)
                .author(User.builder().id(authorId).build())
                .status(CourseStatus.PUBLIC)
                .build();

        CourseRevision draft = CourseRevision.builder()
                .id(revisionId)
                .course(course)
                .revisionNumber(2)
                .status(CourseRevisionStatus.DRAFT)
                .title("Draft revision")
                .build();

        CourseRevisionUpdateDTO dto = new CourseRevisionUpdateDTO();
        dto.setContentSnapshotJson("{\"snapshotVersion\":2,\"modules\":[]}");

        when(courseRevisionFeatureProperties.isWriteEnabled()).thenReturn(true);
        when(courseRevisionRepository.findById(revisionId)).thenReturn(Optional.of(draft));

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> courseRevisionService.updateRevision(revisionId, dto, authorId, null)
        );

        assertTrue(exception.getMessage().contains("COURSE_REVISION_UNSUPPORTED_SNAPSHOT_VERSION"));
        verify(courseRevisionRepository, never()).save(any(CourseRevision.class));
    }

    @Test
    void updateRevision_setsDefaultSnapshotVersionWhenMissing() {
        Long revisionId = 361L;
        Long authorId = 22L;

        Course course = Course.builder()
                .id(100L)
                .author(User.builder().id(authorId).build())
                .status(CourseStatus.PUBLIC)
                .build();

        CourseRevision draft = CourseRevision.builder()
                .id(revisionId)
                .course(course)
                .revisionNumber(2)
                .status(CourseRevisionStatus.DRAFT)
                .title("Draft revision")
                .build();

        CourseRevisionUpdateDTO dto = new CourseRevisionUpdateDTO();
        dto.setContentSnapshotJson("{\"modules\":[]}");

        when(courseRevisionFeatureProperties.isWriteEnabled()).thenReturn(true);
        when(courseRevisionFeatureProperties.getMaxContentSnapshotBytes()).thenReturn(1_048_576);
        when(courseRevisionRepository.findById(revisionId)).thenReturn(Optional.of(draft));
        when(courseRevisionRepository.save(any(CourseRevision.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CourseRevisionDTO result = courseRevisionService.updateRevision(revisionId, dto, authorId, null);

        assertEquals(revisionId, result.getId());
        assertTrue(result.getContentSnapshotJson().contains("\"snapshotVersion\":1"));
    }

    @Test
    void updateRevision_allowsDraftSnapshotWithoutEntityIds() {
        Long revisionId = 362L;
        Long authorId = 22L;

        Course course = Course.builder()
                .id(100L)
                .author(User.builder().id(authorId).build())
                .status(CourseStatus.PUBLIC)
                .build();

        CourseRevision draft = CourseRevision.builder()
                .id(revisionId)
                .course(course)
                .revisionNumber(2)
                .status(CourseRevisionStatus.DRAFT)
                .title("Draft revision")
                .build();

        CourseRevisionUpdateDTO dto = new CourseRevisionUpdateDTO();
        dto.setContentSnapshotJson("""
                {
                  "snapshotVersion": 1,
                  "modules": [
                    {
                      "title": "Module chưa lưu id",
                      "lessons": [
                        {
                          "type": "reading",
                          "title": "Lesson local draft"
                        }
                      ]
                    }
                  ]
                }
                """);

        when(courseRevisionFeatureProperties.isWriteEnabled()).thenReturn(true);
        when(courseRevisionFeatureProperties.getMaxContentSnapshotBytes()).thenReturn(1_048_576);
        when(courseRevisionRepository.findById(revisionId)).thenReturn(Optional.of(draft));
        when(courseRevisionRepository.save(any(CourseRevision.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CourseRevisionDTO result = courseRevisionService.updateRevision(revisionId, dto, authorId, null);

        assertEquals(revisionId, result.getId());
        assertTrue(result.getContentSnapshotJson().contains("Lesson local draft"));
    }

    @Test
    void submitRevision_rejectedWithLargeSnapshot_noChangesSinceReject_throwsConflict() throws Exception {
        Long revisionId = 340L;
        Long baselineRevisionId = 339L;
        Long authorId = 22L;

        Course course = Course.builder()
                .id(100L)
                .author(User.builder().id(authorId).build())
                .status(CourseStatus.PUBLIC)
                .title("Public course")
                .activeRevisionId(baselineRevisionId)
                .build();

        CourseRevision baseline = CourseRevision.builder()
                .id(baselineRevisionId)
                .course(course)
                .revisionNumber(1)
                .status(CourseRevisionStatus.APPROVED)
                .title("Stable revision")
                .description("Stable description")
                .contentSnapshotJson(buildLargeSnapshot(false, false))
                .build();

        CourseRevision rejected = CourseRevision.builder()
                .id(revisionId)
                .course(course)
                .revisionNumber(2)
                .status(CourseRevisionStatus.REJECTED)
                .title("Stable revision")
                .description("Rejected revision with big snapshot")
                .contentSnapshotJson(buildLargeSnapshot(false, true))
                .sourceRevisionId(baselineRevisionId)
                .build();
        rejected.setRejectedSnapshotHash(computeSnapshotHashForDraft(rejected));

        when(courseRevisionFeatureProperties.isWriteEnabled()).thenReturn(true);
        when(courseRevisionRepository.findById(revisionId)).thenReturn(Optional.of(rejected));
        when(courseRevisionRepository.findById(baselineRevisionId)).thenReturn(Optional.of(baseline));

        ConflictException exception = assertThrows(
                ConflictException.class,
                () -> courseRevisionService.submitRevision(revisionId, authorId)
        );

        assertTrue(exception.getMessage().contains("COURSE_REVISION_NO_CHANGES_SINCE_REJECTION"));
        verify(courseRevisionRepository, never()).save(argThat(saved -> saved != null
                && saved.getId().equals(revisionId)
                && saved.getStatus() == CourseRevisionStatus.PENDING));
    }

    @Test
    void submitRevision_rejectedWithLargeSnapshot_moduleOrderChanged_allowsResubmit() throws Exception {
        Long revisionId = 341L;
        Long baselineRevisionId = 339L;
        Long authorId = 22L;
        Instant now = Instant.parse("2026-03-16T02:10:00Z");

        Course course = Course.builder()
                .id(100L)
                .author(User.builder().id(authorId).build())
                .status(CourseStatus.PUBLIC)
                .title("Public course")
                .activeRevisionId(baselineRevisionId)
                .build();

        CourseRevision baseline = CourseRevision.builder()
                .id(baselineRevisionId)
                .course(course)
                .revisionNumber(1)
                .status(CourseRevisionStatus.APPROVED)
                .title("Stable revision")
                .description("Stable description")
                .contentSnapshotJson(buildLargeSnapshot(false, false))
                .build();

        CourseRevision rejected = CourseRevision.builder()
                .id(revisionId)
                .course(course)
                .revisionNumber(2)
                .status(CourseRevisionStatus.REJECTED)
                .title("Stable revision")
                .description("Rejected revision with big snapshot")
                .contentSnapshotJson(buildLargeSnapshot(false, true))
                .sourceRevisionId(baselineRevisionId)
                .build();
        rejected.setRejectedSnapshotHash(computeSnapshotHashForDraft(rejected));
        // Mentor reorders modules (learning flow change), this must be treated as a meaningful change.
        rejected.setContentSnapshotJson(buildLargeSnapshot(true, true));

        when(courseRevisionFeatureProperties.isWriteEnabled()).thenReturn(true);
        when(courseRevisionRepository.findById(revisionId)).thenReturn(Optional.of(rejected));
        when(courseRevisionRepository.findById(baselineRevisionId)).thenReturn(Optional.of(baseline));
        when(clock.instant()).thenReturn(now);
        when(courseRevisionRepository.save(any(CourseRevision.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CourseRevisionDTO result = courseRevisionService.submitRevision(revisionId, authorId);

        assertEquals(CourseRevisionStatus.PENDING, result.getStatus());
        verify(courseRevisionRepository).save(argThat(saved -> saved != null
                && saved.getId().equals(revisionId)
                && saved.getStatus() == CourseRevisionStatus.PENDING));
    }

    private String computeSnapshotHashForDraft(CourseRevision revision) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("snapshotVersion", 1);
        putNullableText(root, "title", revision.getTitle());
        putNullableText(root, "description", revision.getDescription());
        putNullableText(root, "level", revision.getLevel());
        putNullableText(root, "category", revision.getCategory());
        putNullableText(root, "shortDescription", revision.getShortDescription());
        if (revision.getEstimatedDurationHours() == null) root.putNull("estimatedDurationHours");
        else root.put("estimatedDurationHours", revision.getEstimatedDurationHours());
        putNullableText(root, "language", revision.getLanguage());
        putNullableText(root, "currency", revision.getCurrency());
        putNullableText(root, "price", normalizeMoney(revision.getPrice()));
        root.set("learningObjectives", canonicalizeStringArray(revision.getLearningObjectivesJson()));
        root.set("requirements", canonicalizeStringArray(revision.getRequirementsJson()));
                root.set("courseSkillTags", canonicalizeStringArray(revision.getCourseSkillTagsJson()));
                root.set("contentSnapshot", canonicalizeContentSnapshotForHash(revision.getContentSnapshotJson()));

        return sha256Hex(root.toString());
    }

    private JsonNode buildLargeSnapshot(boolean reorderModules, boolean includeExtraLesson) throws Exception {
        String firstModule = """
                {
                                                                        "id": 501,
                  "orderIndex": 0,
                  "title": "Module A",
                  "description": "Core foundations",
                  "lessons": [
                    {
                                                                                        "id": 701,
                      "orderIndex": 0,
                      "title": "Intro",
                      "type": "reading",
                      "contentText": "Lesson intro"
                    },
                    {
                                                                                        "id": 702,
                      "orderIndex": 1,
                      "title": "Quiz A",
                      "type": "quiz",
                      "passScore": 70,
                      "questions": [
                        {
                          "orderIndex": 0,
                          "text": "Q1",
                          "type": "MULTIPLE_CHOICE",
                          "options": [
                            {"orderIndex": 0, "text": "A", "correct": true},
                            {"orderIndex": 1, "text": "B", "correct": false}
                          ]
                        }
                      ]
                    }
                  ]
                }
                """;
        String secondModule = includeExtraLesson
                ? """
                {
                                                                        "id": 502,
                  "orderIndex": 1,
                  "title": "Module B",
                  "description": "Advanced practice",
                  "lessons": [
                    {
                                                                                        "id": 703,
                      "orderIndex": 0,
                      "title": "Assignment B",
                      "type": "assignment",
                      "assignmentMaxScore": 100,
                      "assignmentPassingScore": 75
                    },
                    {
                                                                                        "id": 704,
                      "orderIndex": 1,
                      "title": "Wrap up",
                      "type": "reading",
                      "contentText": "Summary"
                    }
                  ]
                }
                """
                : """
                {
                                                                        "id": 502,
                  "orderIndex": 1,
                  "title": "Module B",
                  "description": "Advanced practice",
                  "lessons": [
                    {
                                                                                        "id": 703,
                      "orderIndex": 0,
                      "title": "Assignment B",
                      "type": "assignment",
                      "assignmentMaxScore": 100,
                      "assignmentPassingScore": 75
                    }
                  ]
                }
                """;

        String snapshotJson = reorderModules
                ? "{ \"snapshotVersion\": 1, \"modules\": [" + secondModule + "," + firstModule + "] }"
                : "{ \"snapshotVersion\": 1, \"modules\": [" + firstModule + "," + secondModule + "] }";
        return objectMapper.readTree(snapshotJson);
    }

    private String normalizeText(String value) {
        if (value == null) return null;
        String collapsed = value.replaceAll("\\s+", " ").trim();
        return collapsed.isEmpty() ? null : collapsed;
    }

    private String normalizeMoney(BigDecimal money) {
        if (money == null) return null;
        return money.stripTrailingZeros().toPlainString();
    }

    private void putNullableText(ObjectNode node, String field, String value) {
        String normalized = normalizeText(value);
        if (normalized == null) {
            node.putNull(field);
        } else {
            node.put(field, normalized);
        }
    }

    private ArrayNode canonicalizeStringArray(JsonNode rawArray) {
        JsonNode source = rawArray == null || rawArray.isNull() ? objectMapper.createArrayNode() : rawArray;
        ArrayNode canonical = objectMapper.createArrayNode();
        for (JsonNode item : source) {
            if (item == null || item.isNull()) {
                canonical.addNull();
                continue;
            }
            if (item.isTextual()) {
                String normalized = normalizeText(item.asText());
                if (normalized == null) {
                    canonical.addNull();
                } else {
                    canonical.add(normalized);
                }
                continue;
            }
            canonical.add(canonicalizeJsonNode(item));
        }
        return canonical;
    }

    private JsonNode defaultJsonObject(JsonNode value) {
        if (value == null || value.isNull()) {
            return objectMapper.createObjectNode();
        }
        return value;
    }

    private JsonNode defaultContentSnapshot(JsonNode value) {
        JsonNode objectNode = defaultJsonObject(value);
        if (!(objectNode instanceof ObjectNode snapshotObject)) {
            return objectNode;
        }
        if (!snapshotObject.has("snapshotVersion") || snapshotObject.path("snapshotVersion").isNull()) {
            snapshotObject.put("snapshotVersion", 1);
        }
        return snapshotObject;
    }

        private JsonNode canonicalizeContentSnapshotForHash(JsonNode contentSnapshot) {
                JsonNode normalized = defaultContentSnapshot(contentSnapshot);
                if (!(normalized instanceof ObjectNode normalizedObject)) {
                        return canonicalizeJsonNode(normalized);
                }
                ObjectNode hashSafeSnapshot = normalizedObject.deepCopy();
                hashSafeSnapshot.remove("compatibility");
                return canonicalizeJsonNode(hashSafeSnapshot);
        }

    private JsonNode canonicalizeJsonNode(JsonNode source) {
        if (source == null || source.isNull()) {
            return objectMapper.nullNode();
        }
        if (source.isObject()) {
            ObjectNode canonical = objectMapper.createObjectNode();
            List<String> fieldNames = new ArrayList<>();
            source.fieldNames().forEachRemaining(fieldNames::add);
            Collections.sort(fieldNames);
            for (String fieldName : fieldNames) {
                canonical.set(fieldName, canonicalizeJsonNode(source.get(fieldName)));
            }
            return canonical;
        }
        if (source.isArray()) {
            ArrayNode canonical = objectMapper.createArrayNode();
            for (JsonNode child : source) {
                canonical.add(canonicalizeJsonNode(child));
            }
            return canonical;
        }
        return source;
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Test
    void approveRevision_preservesCourseThumbnailWhenRevisionDoesNotCarryThumbnail() {
        Long revisionId = 401L;
        Long adminId = 3L;
        Instant now = Instant.parse("2026-03-16T04:30:00Z");
        Media existingCourseThumbnail = Media.builder()
                .id(702L)
                .url("https://cdn.example.com/existing-course-thumb.png")
                .type("image/png")
                .build();

        Course course = Course.builder()
                .id(91L)
                .status(CourseStatus.PUBLIC)
                .activeRevisionId(111L)
                .latestRevisionId(111L)
                .author(User.builder().id(9L).build())
                .title("Course B")
                .thumbnail(existingCourseThumbnail)
                .build();

        CourseRevision pendingRevision = CourseRevision.builder()
                .id(revisionId)
                .course(course)
                .revisionNumber(2)
                .status(CourseRevisionStatus.PENDING)
                .title("Rev 2 without thumbnail change")
                .thumbnail(null)
                .build();

        when(courseRevisionFeatureProperties.isApprovalEnabled()).thenReturn(true);
        when(courseRevisionRepository.findByIdForApproval(revisionId)).thenReturn(Optional.of(pendingRevision));
        when(courseRepository.findByIdForRevisionApproval(course.getId())).thenReturn(Optional.of(course));
        when(clock.instant()).thenReturn(now);
        when(courseRevisionRepository.save(any(CourseRevision.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(courseRepository.save(course)).thenReturn(course);
        lenient().when(courseSkillRepository.deleteByCourseId(course.getId())).thenReturn(0);

        CourseRevisionDTO result = courseRevisionService.approveRevision(revisionId, adminId);

        assertEquals(existingCourseThumbnail, course.getThumbnail());
        assertEquals(existingCourseThumbnail, pendingRevision.getThumbnail());
        assertEquals(702L, result.getThumbnailMediaId());
        assertEquals("https://cdn.example.com/existing-course-thumb.png", result.getThumbnailUrl());
    }

    @Test
        void approveRevision_returnsManualOnlyAutoUpgradeMetadata() {
        Long revisionId = 400L;
        Long adminId = 3L;
        Instant now = Instant.parse("2026-03-16T04:00:00Z");

        Course course = Course.builder()
                .id(90L)
                .status(CourseStatus.PUBLIC)
                .activeRevisionId(111L)
                .latestRevisionId(111L)
                .author(User.builder().id(9L).build())
                .title("Course A")
                .build();
        Media revisionThumbnail = Media.builder()
                .id(701L)
                .url("https://cdn.example.com/revision-thumb.png")
                .type("image/png")
                .build();

        CourseRevision pendingRevision = CourseRevision.builder()
                .id(revisionId)
                .course(course)
                .revisionNumber(2)
                .status(CourseRevisionStatus.PENDING)
                .title("Rev 2")
                .thumbnail(revisionThumbnail)
                .build();

        when(courseRevisionFeatureProperties.isApprovalEnabled()).thenReturn(true);
        when(courseRevisionRepository.findByIdForApproval(revisionId)).thenReturn(Optional.of(pendingRevision));
        when(courseRepository.findByIdForRevisionApproval(course.getId())).thenReturn(Optional.of(course));
        when(clock.instant()).thenReturn(now);
        when(courseRevisionRepository.save(any(CourseRevision.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(courseRepository.save(course)).thenReturn(course);
        // No skills in this test revision — deleteByCourseId called, loop skipped (no findByName)
        lenient().when(courseSkillRepository.deleteByCourseId(course.getId())).thenReturn(0);
        lenient().when(skillRepository.findByNameIgnoreCase(anyString())).thenReturn(Optional.empty());

        CourseRevisionDTO result = courseRevisionService.approveRevision(revisionId, adminId);

        assertEquals(CourseRevisionStatus.APPROVED, result.getStatus());
        assertEquals(revisionId, course.getActiveRevisionId());
        assertEquals(revisionId, course.getLatestRevisionId());
        assertEquals(revisionThumbnail, course.getThumbnail());
        assertEquals(701L, result.getThumbnailMediaId());
        assertEquals("https://cdn.example.com/revision-thumb.png", result.getThumbnailUrl());
        assertEquals("SKIPPED", result.getAutoUpgradeOutcome());
        assertEquals(0, result.getAutoUpgradeAffectedEnrollments());
        assertEquals("POLICY_MANUAL_ONLY", result.getAutoUpgradeReasonCode());
        verify(courseRepository).findByIdForRevisionApproval(course.getId());
        verify(courseRevisionRepository, never()).findById(revisionId);
    }
}
