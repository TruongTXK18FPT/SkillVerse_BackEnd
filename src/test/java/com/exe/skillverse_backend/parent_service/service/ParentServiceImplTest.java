package com.exe.skillverse_backend.parent_service.service;

import com.exe.skillverse_backend.auth_service.dto.response.UserDto;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.mapper.UserMapper;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.course_service.entity.CourseEnrollment;
import com.exe.skillverse_backend.course_service.entity.enums.EnrollmentStatus;
import com.exe.skillverse_backend.course_service.repository.CourseEnrollmentRepository;
import com.exe.skillverse_backend.parent_service.dto.request.LinkStudentRequest;
import com.exe.skillverse_backend.parent_service.dto.request.UpdateLinkStatusRequest;
import com.exe.skillverse_backend.parent_service.dto.response.ParentDashboardResponse;
import com.exe.skillverse_backend.parent_service.dto.response.ParentStudentLinkResponse;
import com.exe.skillverse_backend.parent_service.entity.ParentStudentLink;
import com.exe.skillverse_backend.parent_service.entity.enums.LinkStatus;
import com.exe.skillverse_backend.parent_service.repository.ParentStudentLinkRepository;
import com.exe.skillverse_backend.parent_service.service.impl.ParentServiceImpl;
import com.exe.skillverse_backend.notification_service.service.NotificationService;
import com.exe.skillverse_backend.user_service.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ParentServiceImplTest {

    @Mock
    private ParentStudentLinkRepository linkRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CourseEnrollmentRepository enrollmentRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private NotificationService notificationService;

    @Mock
    private UserProfileRepository userProfileRepository;

    @InjectMocks
    private ParentServiceImpl parentService;

    private User parent;
    private User student;
    private ParentStudentLink link;
    private LinkStudentRequest linkRequest;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        parent = User.builder()
                .id(1L)
                .email("parent@test.com")
                .firstName("Parent")
                .lastName("User")
                .build();

        student = User.builder()
                .id(2L)
                .email("student@test.com")
                .firstName("Student")
                .lastName("User")
                .build();

        link = ParentStudentLink.builder()
                .id(1L)
                .parent(parent)
                .student(student)
                .status(LinkStatus.PENDING)
                .build();

        linkRequest = new LinkStudentRequest("INVITE123", "student@test.com");
    }

    // --- sendLinkRequest Tests ---

    @Test
    void sendLinkRequest_Success() {
        when(userRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(userRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));
        when(linkRepository.existsByParentIdAndStudentId(parent.getId(), student.getId())).thenReturn(false);
        when(linkRepository.findByStudentId(student.getId())).thenReturn(Collections.emptyList());
        when(linkRepository.save(any(ParentStudentLink.class))).thenReturn(link);
        when(userMapper.toDto(any(User.class))).thenReturn(new UserDto());

        ParentStudentLinkResponse response = parentService.sendLinkRequest(parent.getId(), linkRequest);

        assertNotNull(response);
        assertEquals(LinkStatus.PENDING, response.getStatus());
        verify(linkRepository).save(any(ParentStudentLink.class));
    }

    @Test
    void sendLinkRequest_ParentNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> 
            parentService.sendLinkRequest(99L, linkRequest)
        );
    }

    @Test
    void sendLinkRequest_StudentNotFound() {
        when(userRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> 
            parentService.sendLinkRequest(parent.getId(), linkRequest)
        );
    }

    @Test
    void sendLinkRequest_LinkAlreadyExists() {
        when(userRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(userRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));
        when(linkRepository.existsByParentIdAndStudentId(parent.getId(), student.getId())).thenReturn(true);

        assertThrows(RuntimeException.class, () -> 
            parentService.sendLinkRequest(parent.getId(), linkRequest)
        );
    }

    @Test
    void sendLinkRequest_MaxParentsReached() {
        when(userRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(userRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));
        when(linkRepository.existsByParentIdAndStudentId(parent.getId(), student.getId())).thenReturn(false);

        List<ParentStudentLink> existingLinks = new ArrayList<>();
        existingLinks.add(ParentStudentLink.builder().status(LinkStatus.ACTIVE).build());
        existingLinks.add(ParentStudentLink.builder().status(LinkStatus.ACTIVE).build());
        
        when(linkRepository.findByStudentId(student.getId())).thenReturn(existingLinks);

        assertThrows(RuntimeException.class, () -> 
            parentService.sendLinkRequest(parent.getId(), linkRequest)
        );
    }

    // --- updateLinkStatus Tests ---

    @Test
    void updateLinkStatus_Success() {
        UpdateLinkStatusRequest request = new UpdateLinkStatusRequest(LinkStatus.ACTIVE);
        
        when(linkRepository.findById(link.getId())).thenReturn(Optional.of(link));
        when(linkRepository.save(any(ParentStudentLink.class))).thenReturn(link);
        when(userMapper.toDto(any(User.class))).thenReturn(new UserDto());

        // Assuming student calls this
        ParentStudentLinkResponse response = parentService.updateLinkStatus(student.getId(), link.getId(), request);

        assertNotNull(response);
        verify(linkRepository).save(link);
        assertEquals(LinkStatus.ACTIVE, link.getStatus());
    }

    @Test
    void updateLinkStatus_Unauthorized() {
        UpdateLinkStatusRequest request = new UpdateLinkStatusRequest(LinkStatus.ACTIVE);
        when(linkRepository.findById(link.getId())).thenReturn(Optional.of(link));

        assertThrows(RuntimeException.class, () -> 
            parentService.updateLinkStatus(99L, link.getId(), request)
        );
    }

    // --- getParentDashboard Tests ---

    @Test
    void getParentDashboard_Success() {
        link.setStatus(LinkStatus.ACTIVE);
        when(linkRepository.findByParentIdAndStatus(parent.getId(), LinkStatus.ACTIVE))
                .thenReturn(Collections.singletonList(link));
        
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setStatus(EnrollmentStatus.ENROLLED);
        enrollment.setProgressPercent(50);
        
        when(enrollmentRepository.findByUserId(eq(student.getId()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.singletonList(enrollment)));
        
        when(userMapper.toDto(student)).thenReturn(new UserDto());

        ParentDashboardResponse response = parentService.getParentDashboard(parent.getId());

        assertNotNull(response);
        assertEquals(1, response.getTotalStudents());
        assertEquals(1, response.getStudents().size());
        assertEquals("Good", response.getStudents().get(0).getLearningStatus());
    }
    
    @Test
    void getParentDashboard_BehindStatus() {
        link.setStatus(LinkStatus.ACTIVE);
        when(linkRepository.findByParentIdAndStatus(parent.getId(), LinkStatus.ACTIVE))
                .thenReturn(Collections.singletonList(link));
        
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setStatus(EnrollmentStatus.ENROLLED);
        enrollment.setProgressPercent(20); // Low progress
        
        when(enrollmentRepository.findByUserId(eq(student.getId()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.singletonList(enrollment)));
        
        when(userMapper.toDto(student)).thenReturn(new UserDto());

        ParentDashboardResponse response = parentService.getParentDashboard(parent.getId());

        assertNotNull(response);
        assertEquals("Behind", response.getStudents().get(0).getLearningStatus());
    }

    // --- getStudentLinks Tests ---

    @Test
    void getStudentLinks_Success() {
        when(linkRepository.findByStudentId(student.getId())).thenReturn(Collections.singletonList(link));
        when(userMapper.toDto(any(User.class))).thenReturn(new UserDto());

        List<ParentStudentLinkResponse> responses = parentService.getStudentLinks(student.getId());

        assertNotNull(responses);
        assertEquals(1, responses.size());
    }

    // --- unlink Tests ---

    @Test
    void unlink_Success() {
        when(linkRepository.findById(link.getId())).thenReturn(Optional.of(link));

        parentService.unlink(parent.getId(), link.getId());

        verify(linkRepository).delete(link);
    }

    @Test
    void unlink_Unauthorized() {
        when(linkRepository.findById(link.getId())).thenReturn(Optional.of(link));

        assertThrows(RuntimeException.class, () -> 
            parentService.unlink(99L, link.getId())
        );
    }
}
