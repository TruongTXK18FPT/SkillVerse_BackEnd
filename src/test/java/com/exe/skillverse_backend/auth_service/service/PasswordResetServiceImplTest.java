package com.exe.skillverse_backend.auth_service.service;

import com.exe.skillverse_backend.auth_service.dto.request.ChangePasswordRequest;
import com.exe.skillverse_backend.auth_service.dto.request.ResetPasswordRequest;
import com.exe.skillverse_backend.auth_service.dto.request.SetPasswordRequest;
import com.exe.skillverse_backend.auth_service.dto.response.ForgotPasswordResponse;
import com.exe.skillverse_backend.auth_service.dto.response.RegistrationResponse;
import com.exe.skillverse_backend.auth_service.entity.AuthProvider;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.entity.UserStatus;
import com.exe.skillverse_backend.auth_service.repository.RefreshTokenRepository;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.auth_service.service.impl.PasswordResetServiceImpl;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailVerificationService emailVerificationService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private PasswordResetServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PasswordResetServiceImpl(
                userRepository,
                emailVerificationService,
                passwordEncoder,
                refreshTokenRepository);
        lenient().when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("initiateForgotPassword should send a reset OTP for an active verified account")
    void initiateForgotPassword_ShouldSendResetOtpForActiveVerifiedAccount() {
        User user = buildLocalUser();
        LocalDateTime expiry = LocalDateTime.now().plusMinutes(5);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(emailVerificationService.getOtpExpiryTime(user.getEmail())).thenReturn(expiry);

        ForgotPasswordResponse response = service.initiateForgotPassword(user.getEmail());

        assertTrue(response.isSuccess());
        assertEquals(user.getEmail(), response.getEmail());
        assertEquals(5, response.getOtpExpiryMinutes());
        assertEquals(expiry, response.getOtpExpiryTime());
        verify(emailVerificationService).generateOtpForPasswordReset(user.getEmail());
    }

    @Test
    @DisplayName("initiateForgotPassword should reject unverified accounts")
    void initiateForgotPassword_ShouldRejectUnverifiedAccounts() {
        User user = buildLocalUser();
        user.setEmailVerified(false);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> service.initiateForgotPassword(user.getEmail()));

        assertEquals("Email is not verified. Please verify your email first.", exception.getMessage());
        verify(emailVerificationService, never()).generateOtpForPasswordReset(any());
    }

    @Test
    @DisplayName("initiateForgotPassword should reject inactive accounts")
    void initiateForgotPassword_ShouldRejectInactiveAccounts() {
        User user = buildLocalUser();
        user.setStatus(UserStatus.INACTIVE);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> service.initiateForgotPassword(user.getEmail()));

        assertEquals("Account is not active. Please contact support.", exception.getMessage());
        verify(emailVerificationService, never()).generateOtpForPasswordReset(any());
    }

    @Test
    @DisplayName("resetPassword should update credentials, clear OTP data, and invalidate refresh tokens")
    void resetPassword_ShouldUpdateCredentialsClearOtpAndInvalidateRefreshTokens() {
        User user = buildGoogleOnlyUser();
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setEmail(user.getEmail());
        request.setOtp("123456");
        request.setNewPassword("NewPassword123");
        request.setConfirmPassword("NewPassword123");

        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NewPassword123")).thenReturn("encoded-new-password");

        RegistrationResponse response = service.resetPassword(request);

        assertEquals("encoded-new-password", user.getPassword());
        assertEquals(AuthProvider.LOCAL, user.getAuthProvider());
        assertTrue(user.isGoogleLinked());
        assertTrue(user.isEmailVerified());
        assertNull(user.getVerificationOtp());
        assertNull(user.getOtpExpiryTime());
        assertEquals(0, user.getOtpAttempts());
        assertNotNull(user.getPasswordChangedAt());
        assertEquals(user.getEmail(), response.getEmail());
        verify(emailVerificationService).verifyOtp(user.getEmail(), "123456");
        verify(refreshTokenRepository).deleteByUserId(user.getId());
    }

    @Test
    @DisplayName("resetPassword should reject mismatched passwords before touching persistence")
    void resetPassword_ShouldRejectMismatchedPasswords() {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setEmail("learner@skillverse.vn");
        request.setNewPassword("NewPassword123");
        request.setConfirmPassword("Different123");

        RuntimeException exception = assertThrows(RuntimeException.class, () -> service.resetPassword(request));

        assertEquals("Passwords do not match", exception.getMessage());
        verify(userRepository, never()).findByEmail(any());
    }

    @Test
    @DisplayName("resetPassword should restore the original verification flag when OTP validation fails")
    void resetPassword_ShouldRestoreVerificationFlagWhenOtpValidationFails() {
        User user = buildLocalUser();
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setEmail(user.getEmail());
        request.setOtp("999999");
        request.setNewPassword("NewPassword123");
        request.setConfirmPassword("NewPassword123");

        List<Boolean> savedVerificationStates = new ArrayList<>();
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User savedUser = invocation.getArgument(0);
            savedVerificationStates.add(savedUser.isEmailVerified());
            return savedUser;
        });
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        doThrow(new RuntimeException("OTP invalid")).when(emailVerificationService).verifyOtp(user.getEmail(), "999999");

        RuntimeException exception = assertThrows(RuntimeException.class, () -> service.resetPassword(request));

        assertEquals("OTP invalid", exception.getMessage());
        assertEquals(List.of(false, true), savedVerificationStates);
        assertTrue(user.isEmailVerified());
        verify(refreshTokenRepository, never()).deleteByUserId(anyLong());
    }

    @Test
    @DisplayName("setPasswordForGoogleUser should convert Google-only users to dual-auth users")
    void setPasswordForGoogleUser_ShouldConvertGoogleOnlyUsersToDualAuthUsers() {
        User user = buildGoogleOnlyUser();
        SetPasswordRequest request = new SetPasswordRequest();
        request.setNewPassword("BackupPassword123");
        request.setConfirmPassword("BackupPassword123");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("BackupPassword123")).thenReturn("encoded-backup-password");

        RegistrationResponse response = service.setPasswordForGoogleUser(user.getId(), request);

        assertEquals(AuthProvider.LOCAL, user.getAuthProvider());
        assertTrue(user.isGoogleLinked());
        assertEquals("encoded-backup-password", user.getPassword());
        assertNotNull(user.getPasswordChangedAt());
        assertEquals(user.getEmail(), response.getEmail());
        verify(refreshTokenRepository).deleteByUserId(user.getId());
    }

    @Test
    @DisplayName("setPasswordForGoogleUser should reject non-Google accounts")
    void setPasswordForGoogleUser_ShouldRejectNonGoogleAccounts() {
        User user = buildLocalUser();
        SetPasswordRequest request = new SetPasswordRequest();
        request.setNewPassword("BackupPassword123");
        request.setConfirmPassword("BackupPassword123");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> service.setPasswordForGoogleUser(user.getId(), request));

        assertEquals("This feature is only available for Google OAuth users", exception.getMessage());
        verify(refreshTokenRepository, never()).deleteByUserId(anyLong());
    }

    @Test
    @DisplayName("changePassword should update the password and invalidate refresh tokens")
    void changePassword_ShouldUpdatePasswordAndInvalidateRefreshTokens() {
        User user = buildLocalUser();
        user.setPassword("encoded-current");
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("CurrentPassword123");
        request.setNewPassword("NewPassword123");
        request.setConfirmPassword("NewPassword123");

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("CurrentPassword123", "encoded-current")).thenReturn(true);
        when(passwordEncoder.matches("NewPassword123", "encoded-current")).thenReturn(false);
        when(passwordEncoder.encode("NewPassword123")).thenReturn("encoded-new-password");

        RegistrationResponse response = service.changePassword(user.getId(), request);

        assertEquals("encoded-new-password", user.getPassword());
        assertNotNull(user.getPasswordChangedAt());
        assertEquals(user.getEmail(), response.getEmail());
        verify(refreshTokenRepository).deleteByUserId(user.getId());
    }

    @Test
    @DisplayName("changePassword should reject an incorrect current password")
    void changePassword_ShouldRejectIncorrectCurrentPassword() {
        User user = buildLocalUser();
        user.setPassword("encoded-current");
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("WrongPassword123");
        request.setNewPassword("NewPassword123");
        request.setConfirmPassword("NewPassword123");

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPassword123", "encoded-current")).thenReturn(false);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> service.changePassword(user.getId(), request));

        assertEquals("Current password is incorrect", exception.getMessage());
        verify(refreshTokenRepository, never()).deleteByUserId(anyLong());
    }

    @Test
    @DisplayName("changePassword should reject reusing the current password")
    void changePassword_ShouldRejectReusingCurrentPassword() {
        User user = buildLocalUser();
        user.setPassword("encoded-current");
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("CurrentPassword123");
        request.setNewPassword("CurrentPassword123");
        request.setConfirmPassword("CurrentPassword123");

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("CurrentPassword123", "encoded-current")).thenReturn(true);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> service.changePassword(user.getId(), request));

        assertEquals("New password must be different from current password", exception.getMessage());
        verify(refreshTokenRepository, never()).deleteByUserId(anyLong());
    }

    private User buildLocalUser() {
        return User.builder()
                .id(7L)
                .email("learner@skillverse.vn")
                .authProvider(AuthProvider.LOCAL)
                .googleLinked(false)
                .status(UserStatus.ACTIVE)
                .isEmailVerified(true)
                .build();
    }

    private User buildGoogleOnlyUser() {
        return User.builder()
                .id(9L)
                .email("google-user@skillverse.vn")
                .authProvider(AuthProvider.GOOGLE)
                .googleLinked(false)
                .status(UserStatus.ACTIVE)
                .isEmailVerified(true)
                .build();
    }
}
