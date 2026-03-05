package com.exe.skillverse_backend.mentor_service.service;

import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.course_service.repository.CourseEnrollmentRepository;
import com.exe.skillverse_backend.course_service.repository.CoursePurchaseRepository;
import com.exe.skillverse_backend.course_service.repository.CertificateRepository;
import com.exe.skillverse_backend.mentor_booking_service.repository.BookingRepository;
import com.exe.skillverse_backend.mentor_booking_service.repository.BookingReviewRepository;
import com.exe.skillverse_backend.mentor_service.dto.request.MentorProfileUpdateRequest;
import com.exe.skillverse_backend.mentor_service.dto.request.MentorSignatureDrawRequest;
import com.exe.skillverse_backend.mentor_service.entity.MentorProfile;
import com.exe.skillverse_backend.mentor_service.repository.MentorProfileRepository;
import com.exe.skillverse_backend.mentor_service.service.impl.MentorProfileServiceImpl;
import com.exe.skillverse_backend.portfolio_service.repository.PortfolioExtendedProfileRepository;
import com.exe.skillverse_backend.shared.entity.Media;
import com.exe.skillverse_backend.shared.repository.MediaRepository;
import com.exe.skillverse_backend.shared.dto.MediaDTO;
import com.exe.skillverse_backend.shared.exception.BadRequestException;
import com.exe.skillverse_backend.shared.service.MediaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MentorProfileServiceImplTest {

    @Mock
    private MentorProfileRepository mentorProfileRepository;
    @Mock
    private PortfolioExtendedProfileRepository portfolioExtendedProfileRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private BookingReviewRepository bookingReviewRepository;
    @Mock
    private CertificateRepository certificateRepository;
    @Mock
    private CoursePurchaseRepository coursePurchaseRepository;
    @Mock
    private CourseEnrollmentRepository courseEnrollmentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private MediaRepository mediaRepository;
    @Mock
    private MediaService mediaService;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private MentorProfileServiceImpl mentorProfileService;

    @Test
    void uploadMentorSignature_rejectedBecauseSystemOnly() {
        BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> mentorProfileService.uploadMentorSignature(
                        11L,
                        new byte[]{1, 2, 3},
                        "signature.png",
                        "image/png")
        );

        assertEquals("SIGNATURE_FILE_UPLOAD_DISABLED_USE_SYSTEM_SIGNING", ex.getMessage());
    }

    @Test
    void createMentorSignatureFromDrawing_rejectsOutOfBoundsPoint() {
        MentorSignatureDrawRequest request = new MentorSignatureDrawRequest();
        request.setCanvasWidth(720);
        request.setCanvasHeight(220);

        MentorSignatureDrawRequest.Stroke stroke = new MentorSignatureDrawRequest.Stroke();
        stroke.setLineWidth(3.0d);
        MentorSignatureDrawRequest.Point start = new MentorSignatureDrawRequest.Point();
        start.setX(10.0d);
        start.setY(10.0d);
        MentorSignatureDrawRequest.Point outOfBounds = new MentorSignatureDrawRequest.Point();
        outOfBounds.setX(9999.0d);
        outOfBounds.setY(20.0d);
        stroke.setPoints(List.of(start, outOfBounds));
        request.setStrokes(List.of(stroke));

        BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> mentorProfileService.createMentorSignatureFromDrawing(11L, request)
        );

        assertEquals("SIGNATURE_DRAW_POINT_OUT_OF_BOUNDS", ex.getMessage());
    }

    @Test
    void createMentorSignatureFromDrawing_persistsSignature() throws Exception {
        MentorSignatureDrawRequest request = buildValidDrawRequest();
        MentorProfile mentorProfile = MentorProfile.builder()
                .userId(11L)
                .fullName("Mentor One")
                .email("mentor@example.com")
                .mainExpertiseAreas("Java")
                .yearsOfExperience(5)
                .personalProfile("Mentor profile")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(mentorProfileRepository.findByUserId(11L)).thenReturn(Optional.of(mentorProfile));
        when(mediaService.upload(anyLong(), anyString(), anyString(), anyLong(), any(InputStream.class)))
                .thenReturn(MediaDTO.builder().url("https://cdn.skillverse.test/signature.png").build());
        when(mentorProfileRepository.save(any(MentorProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, MentorProfile.class));

        String uploadedUrl = mentorProfileService.createMentorSignatureFromDrawing(11L, request);

        assertEquals("https://cdn.skillverse.test/signature.png", uploadedUrl);

        ArgumentCaptor<String> fileNameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> contentTypeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> fileSizeCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<InputStream> streamCaptor = ArgumentCaptor.forClass(InputStream.class);
        verify(mediaService).upload(
                anyLong(),
                fileNameCaptor.capture(),
                contentTypeCaptor.capture(),
                fileSizeCaptor.capture(),
                streamCaptor.capture()
        );

        assertTrue(fileNameCaptor.getValue().endsWith(".png"));
        assertEquals("image/png", contentTypeCaptor.getValue());
        assertTrue(fileSizeCaptor.getValue() > 0);

        byte[] uploadedBytes = streamCaptor.getValue().readAllBytes();
        assertTrue(uploadedBytes.length > 8);
        assertEquals((byte) 0x89, uploadedBytes[0]);
        assertEquals((byte) 0x50, uploadedBytes[1]);
        assertEquals((byte) 0x4E, uploadedBytes[2]);
        assertEquals((byte) 0x47, uploadedBytes[3]);

        verify(mentorProfileRepository).save(mentorProfile);
        assertEquals("https://cdn.skillverse.test/signature.png", mentorProfile.getSignatureUrl());
    }

    @Test
    void updateMentorProfile_keepsExistingSignatureUrl() {
        MentorProfile mentorProfile = MentorProfile.builder()
                .userId(12L)
                .fullName("Old Name")
                .email("mentor@example.com")
                .mainExpertiseAreas("Backend")
                .yearsOfExperience(4)
                .personalProfile("Old bio")
                .signatureUrl("https://cdn.skillverse.test/original-signature.png")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        MentorProfileUpdateRequest request = new MentorProfileUpdateRequest();
        request.setFirstName("New");

        when(mentorProfileRepository.findByUserId(12L)).thenReturn(Optional.of(mentorProfile));
        when(mentorProfileRepository.save(any(MentorProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, MentorProfile.class));
        when(portfolioExtendedProfileRepository.findByUserId(12L)).thenReturn(Optional.empty());

        mentorProfileService.updateMentorProfile(12L, request);

        assertEquals("https://cdn.skillverse.test/original-signature.png", mentorProfile.getSignatureUrl());
    }

    @Test
    void removeMentorSignature_deletesMediaWhenNoCertificateSnapshotReference() {
        MentorProfile mentorProfile = MentorProfile.builder()
                .userId(15L)
                .fullName("Mentor A")
                .email("mentor-a@example.com")
                .mainExpertiseAreas("Backend")
                .yearsOfExperience(3)
                .personalProfile("Bio")
                .signatureUrl("https://cdn.skillverse.test/signature-a.png")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Media media = new Media();
        media.setId(501L);
        media.setUrl("https://cdn.skillverse.test/signature-a.png");
        media.setUploadedBy(15L);

        when(mentorProfileRepository.findByUserId(15L)).thenReturn(Optional.of(mentorProfile));
        when(certificateRepository.countByInstructorSignatureUrlSnapshot("https://cdn.skillverse.test/signature-a.png"))
                .thenReturn(0L);
        when(mediaRepository.findFirstByUrl("https://cdn.skillverse.test/signature-a.png"))
                .thenReturn(Optional.of(media));
        when(mentorProfileRepository.save(any(MentorProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, MentorProfile.class));

        mentorProfileService.removeMentorSignature(15L);

        verify(mediaService).delete(501L, 15L);
        verify(mentorProfileRepository).save(mentorProfile);
        assertNull(mentorProfile.getSignatureUrl());
    }

    @Test
    void removeMentorSignature_keepsMediaWhenCertificateSnapshotExists() {
        MentorProfile mentorProfile = MentorProfile.builder()
                .userId(16L)
                .fullName("Mentor B")
                .email("mentor-b@example.com")
                .mainExpertiseAreas("Frontend")
                .yearsOfExperience(4)
                .personalProfile("Bio")
                .signatureUrl("https://cdn.skillverse.test/signature-b.png")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(mentorProfileRepository.findByUserId(16L)).thenReturn(Optional.of(mentorProfile));
        when(certificateRepository.countByInstructorSignatureUrlSnapshot("https://cdn.skillverse.test/signature-b.png"))
                .thenReturn(2L);
        when(mentorProfileRepository.save(any(MentorProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, MentorProfile.class));

        mentorProfileService.removeMentorSignature(16L);

        verify(mediaService, never()).delete(anyLong(), anyLong());
        verify(mediaRepository, never()).findFirstByUrl(anyString());
        verify(mentorProfileRepository).save(mentorProfile);
        assertNull(mentorProfile.getSignatureUrl());
    }

    private MentorSignatureDrawRequest buildValidDrawRequest() {
        MentorSignatureDrawRequest request = new MentorSignatureDrawRequest();
        request.setCanvasWidth(720);
        request.setCanvasHeight(220);

        MentorSignatureDrawRequest.Stroke stroke1 = new MentorSignatureDrawRequest.Stroke();
        stroke1.setLineWidth(3.0d);
        stroke1.setPoints(List.of(
                point(70, 140),
                point(95, 132),
                point(120, 120),
                point(145, 108),
                point(180, 95),
                point(230, 120),
                point(280, 150)
        ));

        MentorSignatureDrawRequest.Stroke stroke2 = new MentorSignatureDrawRequest.Stroke();
        stroke2.setLineWidth(3.0d);
        stroke2.setPoints(List.of(
                point(280, 150),
                point(315, 140),
                point(350, 122),
                point(390, 100),
                point(450, 115),
                point(520, 140)
        ));

        request.setStrokes(List.of(stroke1, stroke2));
        return request;
    }

    private MentorSignatureDrawRequest.Point point(double x, double y) {
        MentorSignatureDrawRequest.Point point = new MentorSignatureDrawRequest.Point();
        point.setX(x);
        point.setY(y);
        return point;
    }
}
