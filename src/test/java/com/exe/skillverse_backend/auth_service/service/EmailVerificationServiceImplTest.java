package com.exe.skillverse_backend.auth_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.entity.UserStatus;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.auth_service.service.impl.EmailVerificationServiceImpl;
import com.exe.skillverse_backend.shared.service.EmailService;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceImplTest {

    private static final Pattern OTP_PATTERN = Pattern.compile("\\d{6}");

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailService emailService;

    private EmailVerificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EmailVerificationServiceImpl(userRepository, emailService);
        lenient().when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("generateOtpForUser should persist a 6-digit OTP and send the verification email")
    void generateOtpForUser_ShouldPersistOtpAndSendVerificationEmail() {
        User user = buildUser();
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        String otp = service.generateOtpForUser(user.getEmail());

        assertTrue(OTP_PATTERN.matcher(otp).matches());
        assertEquals(otp, user.getVerificationOtp());
        assertEquals(0, user.getOtpAttempts());
        assertNotNull(user.getLastOtpSentTime());
        assertNotNull(user.getOtpExpiryTime());
        assertTrue(user.getOtpExpiryTime().isAfter(LocalDateTime.now().plusMinutes(4)));
        verify(userRepository).save(user);
        verify(emailService).sendOtpEmail(user.getEmail(), otp);
    }

    @Test
    @DisplayName("generateOtpForUser should reject requests inside the cooldown window")
    void generateOtpForUser_ShouldRejectRequestsInsideCooldownWindow() {
        User user = buildUser();
        user.setLastOtpSentTime(LocalDateTime.now().minusSeconds(15));
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> service.generateOtpForUser(user.getEmail()));

        assertTrue(exception.getMessage().contains("Please wait"));
        verify(userRepository, never()).save(any(User.class));
        verify(emailService, never()).sendOtpEmail(any(), any());
    }

    @Test
    @DisplayName("generateOtpForUser should reject already verified emails")
    void generateOtpForUser_ShouldRejectAlreadyVerifiedEmails() {
        User user = buildUser();
        user.setEmailVerified(true);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> service.generateOtpForUser(user.getEmail()));

        assertEquals("Email is already verified", exception.getMessage());
        verify(emailService, never()).sendOtpEmail(any(), any());
    }

    @Test
    @DisplayName("generateOtpForPasswordReset should send the reset email and reset attempt counters")
    void generateOtpForPasswordReset_ShouldSendResetEmailAndResetAttempts() {
        User user = buildUser();
        user.setOtpAttempts(2);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        String otp = service.generateOtpForPasswordReset(user.getEmail());

        assertTrue(OTP_PATTERN.matcher(otp).matches());
        assertEquals(otp, user.getVerificationOtp());
        assertEquals(0, user.getOtpAttempts());
        verify(emailService).sendPasswordResetOtpEmail(user.getEmail(), otp);
    }

    @Test
    @DisplayName("verifyOtp should mark the email verified and clear OTP state on success")
    void verifyOtp_ShouldMarkEmailVerifiedAndClearOtpState() {
        User user = buildUser();
        user.setVerificationOtp("123456");
        user.setOtpExpiryTime(LocalDateTime.now().plusMinutes(5));
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        boolean verified = service.verifyOtp(user.getEmail(), "123456");

        assertTrue(verified);
        assertTrue(user.isEmailVerified());
        assertNull(user.getVerificationOtp());
        assertNull(user.getOtpExpiryTime());
        assertEquals(0, user.getOtpAttempts());
    }

    @Test
    @DisplayName("verifyOtp should clear expired OTP data and throw")
    void verifyOtp_ShouldClearExpiredOtpDataAndThrow() {
        User user = buildUser();
        user.setVerificationOtp("123456");
        user.setOtpExpiryTime(LocalDateTime.now().minusMinutes(1));
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> service.verifyOtp(user.getEmail(), "123456"));

        assertEquals("OTP has expired. Please request a new OTP", exception.getMessage());
        assertNull(user.getVerificationOtp());
        assertNull(user.getOtpExpiryTime());
    }

    @Test
    @DisplayName("verifyOtp should clear OTP data after the max attempt threshold is exceeded")
    void verifyOtp_ShouldClearOtpDataAfterMaxAttemptsExceeded() {
        User user = buildUser();
        user.setVerificationOtp("123456");
        user.setOtpExpiryTime(LocalDateTime.now().plusMinutes(5));
        user.setOtpAttempts(3);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> service.verifyOtp(user.getEmail(), "123456"));

        assertEquals("Maximum OTP attempts exceeded. Please request a new OTP", exception.getMessage());
        assertNull(user.getVerificationOtp());
        assertNull(user.getOtpExpiryTime());
        assertEquals(0, user.getOtpAttempts());
    }

    @Test
    @DisplayName("verifyOtp should increment attempts and report remaining tries for a wrong code")
    void verifyOtp_ShouldIncrementAttemptsForWrongCode() {
        User user = buildUser();
        user.setVerificationOtp("123456");
        user.setOtpExpiryTime(LocalDateTime.now().plusMinutes(5));
        user.setOtpAttempts(1);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> service.verifyOtp(user.getEmail(), "000000"));

        assertEquals("Invalid OTP. Attempts remaining: 1", exception.getMessage());
        assertEquals(2, user.getOtpAttempts());
        assertFalse(user.isEmailVerified());
        assertEquals("123456", user.getVerificationOtp());
    }

    @Test
    @DisplayName("helper methods should expose verification status, expiry, and remaining attempts")
    void helperMethods_ShouldExposeVerificationStatusExpiryAndAttempts() {
        User user = buildUser();
        LocalDateTime expiry = LocalDateTime.now().plusMinutes(5);
        user.setEmailVerified(true);
        user.setOtpExpiryTime(expiry);
        user.setOtpAttempts(1);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        assertTrue(service.isEmailVerified(user.getEmail()));
        assertEquals(expiry, service.getOtpExpiryTime(user.getEmail()));
        assertEquals(2, service.getRemainingOtpAttempts(user.getEmail()));
    }

    @Test
    @DisplayName("resendOtp should delegate to OTP generation and send a fresh email")
    void resendOtp_ShouldDelegateToOtpGeneration() {
        User user = buildUser();
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        String otp = service.resendOtp(user.getEmail());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        verify(emailService).sendOtpEmail(eq(user.getEmail()), eq(otp));
        assertTrue(OTP_PATTERN.matcher(userCaptor.getValue().getVerificationOtp()).matches());
    }

    private User buildUser() {
        return User.builder()
                .id(1L)
                .email("learner@skillverse.vn")
                .status(UserStatus.ACTIVE)
                .isEmailVerified(false)
                .otpAttempts(0)
                .build();
    }
}
