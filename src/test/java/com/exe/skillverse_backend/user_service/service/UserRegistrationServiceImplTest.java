package com.exe.skillverse_backend.user_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.service.UserCreationService;
import com.exe.skillverse_backend.shared.exception.ConflictException;
import com.exe.skillverse_backend.user_service.dto.request.UserRegistrationRequest;
import com.exe.skillverse_backend.user_service.entity.UserProfile;
import com.exe.skillverse_backend.user_service.repository.UserProfileRepository;
import com.exe.skillverse_backend.user_service.service.impl.UserRegistrationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRegistrationServiceImplTest {

    @Mock
    private UserCreationService userCreationService;

    @Mock
    private UserProfileRepository userProfileRepository;

    @InjectMocks
    private UserRegistrationServiceImpl userRegistrationService;

    private UserRegistrationRequest request;

    @BeforeEach
    void setUp() {
        request = new UserRegistrationRequest();
        request.setEmail("duplicate@gmail.com");
        request.setPassword("Password123!");
        request.setConfirmPassword("Password123!");
        request.setFullName("Test User");
        request.setPhone("0901234567");
        request.setBio("Bio");
        request.setAddress("Address");
        request.setRegion("Vietnam");
    }

    @Test
    void register_whenEmailAlreadyExists_throwsConflictException() {
        when(userCreationService.emailExists(request.getEmail())).thenReturn(true);

        ConflictException exception = assertThrows(
                ConflictException.class,
                () -> userRegistrationService.register(request)
        );

        assertEquals("Email đã được đăng ký", exception.getMessage());
        verify(userCreationService, never()).createUserForUser(anyString(), anyString(), anyString());
        verify(userProfileRepository, never()).save(any(UserProfile.class));
    }

    @Test
    void register_whenEmailDoesNotExist_createsUserProfile() {
        when(userCreationService.emailExists(request.getEmail())).thenReturn(false);
        when(userCreationService.createUserForUser(anyString(), anyString(), anyString()))
                .thenReturn(User.builder().id(10L).email(request.getEmail()).build());
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userCreationService.getOtpExpiryTime(request.getEmail())).thenReturn(java.time.LocalDateTime.now().plusMinutes(5));

        var response = userRegistrationService.register(request);

        verify(userCreationService).createUserForUser(anyString(), anyString(), anyString());
        verify(userProfileRepository).save(any(UserProfile.class));
        assertEquals(true, response.isSuccess());
        assertEquals(request.getEmail(), response.getEmail());
    }
}