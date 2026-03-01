package com.exe.skillverse_backend.course_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.course_service.dto.certificatedto.CertificateDTO;
import com.exe.skillverse_backend.course_service.dto.certificatedto.CertificateVerificationDTO;
import com.exe.skillverse_backend.course_service.dto.progressdto.CourseLearningStatusDTO;
import com.exe.skillverse_backend.course_service.entity.Certificate;
import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.course_service.mapper.CertificateMapper;
import com.exe.skillverse_backend.course_service.repository.CertificateRepository;
import com.exe.skillverse_backend.course_service.repository.CourseRepository;
import com.exe.skillverse_backend.course_service.service.impl.CertificateServiceImpl;
import com.exe.skillverse_backend.mentor_service.repository.MentorProfileRepository;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.user_service.repository.UserProfileRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CertificateServiceImplTest {

    @Mock
    private CertificateRepository certificateRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MentorProfileRepository mentorProfileRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private CertificateMapper certificateMapper;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private Clock clock;

    @InjectMocks
    private CertificateServiceImpl certificateService;

    @Test
    void issueCourseCertificate_returnsExistingCertificateWhenAlreadyIssued() {
        Certificate existingCertificate = Certificate.builder()
                .id(99L)
                .serial("SV-C-1-U-2-EXISTING")
                .build();
        CourseLearningStatusDTO completionStatus = CourseLearningStatusDTO.builder().percent(100).build();

        when(
                certificateRepository
                        .findFirstByUser_IdAndCourse_IdAndRevokedAtIsNullOrderByIssuedAtDesc(2L, 1L)
        )
                .thenReturn(Optional.of(existingCertificate));
        CertificateDTO actualDto = certificateService.issueCourseCertificate(1L, 2L, completionStatus);

        assertEquals(99L, actualDto.getId());
        assertEquals("SV-C-1-U-2-EXISTING", actualDto.getSerial());
        assertEquals("Skillverse", actualDto.getIssuerName());
        verify(courseRepository, never()).findById(1L);
        verify(userRepository, never()).findById(2L);
        verify(certificateRepository, never()).save(existingCertificate);
    }

    @Test
    void issueCourseCertificate_returnsExistingCertificateAfterUniquenessConflict() throws Exception {
        CourseLearningStatusDTO completionStatus = CourseLearningStatusDTO.builder()
                .percent(100)
                .completedLessonCount(2)
                .totalLessonCount(2)
                .completedQuizCount(1)
                .totalQuizCount(1)
                .completedRequiredAssignmentCount(1)
                .totalRequiredAssignmentCount(1)
                .completedItemCount(4)
                .totalItemCount(4)
                .build();
        Course course = Course.builder().id(1L).build();
        User user = User.builder().id(2L).build();
        Certificate pendingCertificate = Certificate.builder()
                .serial("SV-C-1-U-2-NEW")
                .build();
        Certificate existingCertificate = Certificate.builder()
                .id(77L)
                .serial("SV-C-1-U-2-EXISTING")
                .build();

        when(
                certificateRepository
                        .findFirstByUser_IdAndCourse_IdAndRevokedAtIsNullOrderByIssuedAtDesc(2L, 1L)
        )
                .thenReturn(Optional.empty(), Optional.of(existingCertificate));
        when(certificateRepository.findFirstByUser_IdAndCourse_IdOrderByIssuedAtDesc(2L, 1L))
                .thenReturn(Optional.empty());
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(objectMapper.writeValueAsString(anyMap())).thenReturn("{}");
        when(certificateMapper.toEntity(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(pendingCertificate);
        when(certificateRepository.save(pendingCertificate))
                .thenThrow(new DataIntegrityViolationException("duplicate active certificate"));
        when(clock.instant()).thenReturn(
                Instant.parse("2026-02-28T12:00:00Z")
        );

        CertificateDTO actualDto = certificateService.issueCourseCertificate(1L, 2L, completionStatus);

        assertEquals(77L, actualDto.getId());
        assertEquals("SV-C-1-U-2-EXISTING", actualDto.getSerial());
        assertEquals("Skillverse", actualDto.getIssuerName());
        verify(certificateRepository).save(pendingCertificate);
    }

    @Test
    void getCertificateVerification_returnsPublicVerificationRecord() {
        User learner = User.builder()
                .id(2L)
                .firstName("Phung")
                .lastName("Nguyen Hoang")
                .build();
        User mentor = User.builder()
                .id(3L)
                .firstName("Mentor")
                .lastName("Skillverse")
                .build();
        Course course = Course.builder()
                .id(1L)
                .title("Advanced Skillverse Course")
                .author(mentor)
                .build();
        Certificate certificate = Certificate.builder()
                .id(8L)
                .serial("SV-C-1-U-2-ABCDE12345")
                .user(learner)
                .course(course)
                .issuedAt(Instant.parse("2026-03-01T08:00:00Z"))
                .build();

        when(certificateRepository.findBySerial("SV-C-1-U-2-ABCDE12345"))
                .thenReturn(Optional.of(certificate));

        CertificateVerificationDTO verification = certificateService
                .getCertificateVerification("SV-C-1-U-2-ABCDE12345");

        assertEquals("Skillverse", verification.getIssuerName());
        assertEquals("VALID", verification.getVerificationStatus());
        assertEquals("Advanced Skillverse Course", verification.getCourseTitle());
        assertEquals("Phung Nguyen Hoang", verification.getRecipientName());
        assertEquals("Mentor Skillverse", verification.getInstructorName());
    }

    @Test
    void getCertificateVerification_prefersSnapshotNamesOverCurrentProfileValues() {
        User learner = User.builder()
                .id(2L)
                .firstName("Current")
                .lastName("Learner")
                .email("learner@example.com")
                .build();
        User mentor = User.builder()
                .id(3L)
                .firstName("Current")
                .lastName("Mentor")
                .email("mentor@example.com")
                .build();
        Course course = Course.builder()
                .id(1L)
                .title("Current Course Title")
                .author(mentor)
                .build();
        Certificate certificate = Certificate.builder()
                .id(8L)
                .serial("SV-C-1-U-2-SNAPSHOT")
                .user(learner)
                .course(course)
                .recipientNameSnapshot("Learner Name At Issue")
                .courseTitleSnapshot("Course Title At Issue")
                .instructorNameSnapshot("Instructor Name At Issue")
                .issuedAt(Instant.parse("2026-03-01T08:00:00Z"))
                .build();

        when(certificateRepository.findBySerial("SV-C-1-U-2-SNAPSHOT"))
                .thenReturn(Optional.of(certificate));

        CertificateVerificationDTO verification = certificateService
                .getCertificateVerification("SV-C-1-U-2-SNAPSHOT");

        assertEquals("Learner Name At Issue", verification.getRecipientName());
        assertEquals("Course Title At Issue", verification.getCourseTitle());
        assertEquals("Instructor Name At Issue", verification.getInstructorName());
    }

    @Test
    void issueCourseCertificate_generatesOpaquePublicSerial() throws Exception {
        CourseLearningStatusDTO completionStatus = CourseLearningStatusDTO.builder()
                .percent(100)
                .build();
        Course course = Course.builder().id(1L).title("Course").build();
        User user = User.builder()
                .id(2L)
                .firstName("Phung")
                .lastName("Nguyen Hoang")
                .email("learner@example.com")
                .build();

        when(certificateRepository.findFirstByUser_IdAndCourse_IdAndRevokedAtIsNullOrderByIssuedAtDesc(2L, 1L))
                .thenReturn(Optional.empty());
        when(certificateRepository.findFirstByUser_IdAndCourse_IdOrderByIssuedAtDesc(2L, 1L))
                .thenReturn(Optional.empty());
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(objectMapper.writeValueAsString(anyMap())).thenReturn("{}");
        when(certificateMapper.toEntity(any(), any(), any(), anyString(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> Certificate.builder()
                        .serial(invocation.getArgument(3, String.class))
                        .build());
        when(certificateRepository.save(any(Certificate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, Certificate.class));
        when(clock.instant()).thenReturn(Instant.parse("2026-03-01T12:00:00Z"));

        CertificateDTO issuedCertificate = certificateService.issueCourseCertificate(1L, 2L, completionStatus);

        assertTrue(issuedCertificate.getSerial().matches("^SVC-[A-Z2-9]{16}$"));
    }

    @Test
    void issueCourseCertificate_retriesWhenSerialCollisionHappensWithoutActiveCertificate() throws Exception {
        CourseLearningStatusDTO completionStatus = CourseLearningStatusDTO.builder()
                .percent(100)
                .build();
        Course course = Course.builder().id(1L).title("Course").build();
        User user = User.builder()
                .id(2L)
                .firstName("Phung")
                .lastName("Nguyen Hoang")
                .email("learner@example.com")
                .build();

        when(certificateRepository.findFirstByUser_IdAndCourse_IdAndRevokedAtIsNullOrderByIssuedAtDesc(2L, 1L))
                .thenReturn(Optional.empty(), Optional.empty());
        when(certificateRepository.findFirstByUser_IdAndCourse_IdOrderByIssuedAtDesc(2L, 1L))
                .thenReturn(Optional.empty());
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(objectMapper.writeValueAsString(anyMap())).thenReturn("{}");
        when(certificateMapper.toEntity(any(), any(), any(), anyString(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> Certificate.builder()
                        .serial(invocation.getArgument(3, String.class))
                        .build());
        when(certificateRepository.save(any(Certificate.class)))
                .thenThrow(new DataIntegrityViolationException("serial duplicate"))
                .thenAnswer(invocation -> invocation.getArgument(0, Certificate.class));
        when(clock.instant()).thenReturn(Instant.parse("2026-03-01T12:00:00Z"));

        CertificateDTO issuedCertificate = certificateService.issueCourseCertificate(1L, 2L, completionStatus);

        assertTrue(issuedCertificate.getSerial().matches("^SVC-[A-Z2-9]{16}$"));
        verify(certificateRepository, times(2)).save(any(Certificate.class));

        ArgumentCaptor<String> serialCaptor = ArgumentCaptor.forClass(String.class);
        verify(certificateMapper, times(2)).toEntity(
                any(),
                any(),
                any(),
                serialCaptor.capture(),
                any(),
                any(),
                any(),
                any(),
                any()
        );
        assertEquals(2, serialCaptor.getAllValues().size());
    }
}
