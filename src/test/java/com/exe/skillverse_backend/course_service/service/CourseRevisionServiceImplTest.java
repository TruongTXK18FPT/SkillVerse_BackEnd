package com.exe.skillverse_backend.course_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.course_service.dto.coursedto.CourseRevisionDTO;
import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.course_service.entity.CourseRevision;
import com.exe.skillverse_backend.course_service.entity.enums.CourseRevisionStatus;
import com.exe.skillverse_backend.course_service.entity.enums.CourseStatus;
import com.exe.skillverse_backend.course_service.policy.CourseRevisionFeatureProperties;
import com.exe.skillverse_backend.course_service.repository.CourseRepository;
import com.exe.skillverse_backend.course_service.repository.CourseRevisionRepository;
import com.exe.skillverse_backend.course_service.service.impl.CourseAutoCompatibleUpgradeExecutor;
import com.exe.skillverse_backend.course_service.service.impl.CourseRevisionServiceImpl;
import com.exe.skillverse_backend.shared.exception.BadRequestException;
import com.exe.skillverse_backend.shared.exception.ConflictException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseRevisionServiceImplTest {

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CourseRevisionRepository courseRevisionRepository;

    @Mock
    private CourseRevisionFeatureProperties courseRevisionFeatureProperties;

    @Mock
    private CourseAutoCompatibleUpgradeExecutor autoCompatibleUpgradeExecutor;

    @Mock
    private Clock clock;

    @Mock
    private MeterRegistry meterRegistry;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private CourseRevisionServiceImpl courseRevisionService;

    @Test
    void createRevision_throwsWhenWriteFeatureDisabled() {
        when(courseRevisionFeatureProperties.isWriteEnabled()).thenReturn(false);

        assertThrows(ConflictException.class, () -> courseRevisionService.createRevision(200L, 11L));
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

        when(courseRevisionFeatureProperties.isWriteEnabled()).thenReturn(true);
        when(courseRevisionRepository.findById(revisionId)).thenReturn(Optional.of(draft));
        when(courseRevisionRepository.findById(baselineRevisionId)).thenReturn(Optional.of(baseline));

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

        when(courseRevisionFeatureProperties.isWriteEnabled()).thenReturn(true);
        when(courseRevisionFeatureProperties.getSubmitChangeCheckMode())
                .thenReturn(CourseRevisionFeatureProperties.SubmitChangeCheckMode.WARN);
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

        when(courseRevisionFeatureProperties.isWriteEnabled()).thenReturn(true);
        when(courseRevisionFeatureProperties.getSubmitChangeCheckMode())
                .thenReturn(CourseRevisionFeatureProperties.SubmitChangeCheckMode.OFF);
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
        when(courseRevisionRepository.save(any(CourseRevision.class))).thenAnswer(invocation -> invocation.getArgument(0));
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
        changedSnapshot.putArray("modules")
                .add(objectMapper.createObjectNode().put("title", "Module A"));

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

        com.exe.skillverse_backend.course_service.dto.coursedto.CourseRevisionUpdateDTO dto =
                new com.exe.skillverse_backend.course_service.dto.coursedto.CourseRevisionUpdateDTO();
        dto.setContentSnapshotJson("{\"modules\":[{\"id\":1,\"title\":\"very-large-content\"}]}");

        when(courseRevisionFeatureProperties.isWriteEnabled()).thenReturn(true);
        when(courseRevisionFeatureProperties.getMaxContentSnapshotBytes()).thenReturn(8);
        when(courseRevisionRepository.findById(revisionId)).thenReturn(Optional.of(draft));

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> courseRevisionService.updateRevision(revisionId, dto, authorId)
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

        com.exe.skillverse_backend.course_service.dto.coursedto.CourseRevisionUpdateDTO dto =
                new com.exe.skillverse_backend.course_service.dto.coursedto.CourseRevisionUpdateDTO();
        dto.setContentSnapshotJson("{\"snapshotVersion\":2,\"modules\":[]}");

        when(courseRevisionFeatureProperties.isWriteEnabled()).thenReturn(true);
        when(courseRevisionRepository.findById(revisionId)).thenReturn(Optional.of(draft));

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> courseRevisionService.updateRevision(revisionId, dto, authorId)
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

        com.exe.skillverse_backend.course_service.dto.coursedto.CourseRevisionUpdateDTO dto =
                new com.exe.skillverse_backend.course_service.dto.coursedto.CourseRevisionUpdateDTO();
        dto.setContentSnapshotJson("{\"modules\":[]}");

        when(courseRevisionFeatureProperties.isWriteEnabled()).thenReturn(true);
        when(courseRevisionFeatureProperties.getMaxContentSnapshotBytes()).thenReturn(1_048_576);
        when(courseRevisionRepository.findById(revisionId)).thenReturn(Optional.of(draft));
        when(courseRevisionRepository.save(any(CourseRevision.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CourseRevisionDTO result = courseRevisionService.updateRevision(revisionId, dto, authorId);

        assertEquals(revisionId, result.getId());
        assertTrue(result.getContentSnapshotJson().contains("\"snapshotVersion\":1"));
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
        root.set("contentSnapshot", canonicalizeJsonNode(defaultJsonObject(revision.getContentSnapshotJson())));

        return sha256Hex(root.toString());
    }

    private JsonNode buildLargeSnapshot(boolean reorderModules, boolean includeExtraLesson) throws Exception {
        String firstModule = """
                {
                  "orderIndex": 0,
                  "title": "Module A",
                  "description": "Core foundations",
                  "lessons": [
                    {
                      "orderIndex": 0,
                      "title": "Intro",
                      "type": "reading",
                      "contentText": "Lesson intro"
                    },
                    {
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
                  "orderIndex": 1,
                  "title": "Module B",
                  "description": "Advanced practice",
                  "lessons": [
                    {
                      "orderIndex": 0,
                      "title": "Assignment B",
                      "type": "assignment",
                      "assignmentMaxScore": 100,
                      "assignmentPassingScore": 75
                    },
                    {
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
                  "orderIndex": 1,
                  "title": "Module B",
                  "description": "Advanced practice",
                  "lessons": [
                    {
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

    private String normalizeMoney(java.math.BigDecimal money) {
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
    void approveRevision_callsAutoCompatibleUpgradeExecutorWithPreviousActiveRevision() {
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

        CourseRevision pendingRevision = CourseRevision.builder()
                .id(revisionId)
                .course(course)
                .revisionNumber(2)
                .status(CourseRevisionStatus.PENDING)
                .title("Rev 2")
                .build();

        when(courseRevisionFeatureProperties.isApprovalEnabled()).thenReturn(true);
        when(courseRevisionRepository.findByIdForApproval(revisionId)).thenReturn(Optional.of(pendingRevision));
        when(courseRepository.findByIdForRevisionApproval(course.getId())).thenReturn(Optional.of(course));
        when(clock.instant()).thenReturn(now);
        when(courseRevisionRepository.save(any(CourseRevision.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(courseRepository.save(course)).thenReturn(course);
        when(autoCompatibleUpgradeExecutor.executeAfterRevisionApproval(eq(course), eq(111L), eq(pendingRevision)))
                .thenReturn(CourseAutoCompatibleUpgradeExecutor.AutoUpgradeExecutionResult.upgraded(4));

        CourseRevisionDTO result = courseRevisionService.approveRevision(revisionId, adminId);

        assertEquals(CourseRevisionStatus.APPROVED, result.getStatus());
        assertEquals(revisionId, course.getActiveRevisionId());
        assertEquals(revisionId, course.getLatestRevisionId());
        assertEquals("UPGRADED", result.getAutoUpgradeOutcome());
        assertEquals(4, result.getAutoUpgradeAffectedEnrollments());
        verify(autoCompatibleUpgradeExecutor).executeAfterRevisionApproval(eq(course), eq(111L), eq(pendingRevision));
        verify(courseRepository).findByIdForRevisionApproval(course.getId());
        verify(courseRevisionRepository, never()).findById(revisionId);
    }
}
