package com.exe.skillverse_backend.auth_service.service;

import com.exe.skillverse_backend.auth_service.dto.request.LoginRequest;
import com.exe.skillverse_backend.auth_service.dto.response.AuthResponse;
import com.exe.skillverse_backend.auth_service.entity.AuthProvider;
import com.exe.skillverse_backend.auth_service.entity.InvalidatedToken;
import com.exe.skillverse_backend.auth_service.entity.PrimaryRole;
import com.exe.skillverse_backend.auth_service.entity.RefreshToken;
import com.exe.skillverse_backend.auth_service.entity.Role;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.entity.UserStatus;
import com.exe.skillverse_backend.auth_service.repository.InvalidatedTokenRepository;
import com.exe.skillverse_backend.auth_service.repository.RefreshTokenRepository;
import com.exe.skillverse_backend.auth_service.repository.RoleRepository;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.auth_service.service.impl.AuthServiceImpl;
import com.exe.skillverse_backend.business_service.repository.RecruiterProfileRepository;
import com.exe.skillverse_backend.mentor_service.repository.MentorProfileRepository;
import com.exe.skillverse_backend.premium_service.service.PremiumService;
import com.exe.skillverse_backend.shared.service.EmailService;
import com.exe.skillverse_backend.user_service.service.UserProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private InvalidatedTokenRepository invalidatedTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserProfileService userProfileService;

    @Mock
    private MentorProfileRepository mentorProfileRepository;

    @Mock
    private RecruiterProfileRepository recruiterProfileRepository;

    @Mock
    private EmailVerificationService emailVerificationService;

    @Mock
    private EmailService emailService;

    @Mock
    private PremiumService premiumService;

    @Mock
    private GoogleTokenVerificationService googleTokenVerificationService;

    @InjectMocks
    private AuthServiceImpl authService;

    private User activeUser;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "jwtSecret", "0123456789012345678901234567890101234567890123456789012345678901");
        ReflectionTestUtils.setField(authService, "accessTokenExpiration", 3600L);
        ReflectionTestUtils.setField(authService, "refreshTokenExpiration", 86400L);
        ReflectionTestUtils.setField(authService, "refreshPepper", "test-refresh-pepper");

        Role userRole = new Role();
        userRole.setId(1L);
        userRole.setName("USER");

        activeUser = User.builder()
                .id(7L)
                .email("learner@skillverse.vn")
                .password("encoded-password")
                .firstName("Skill")
                .lastName("Learner")
                .primaryRole(PrimaryRole.USER)
                .authProvider(AuthProvider.LOCAL)
                .status(UserStatus.ACTIVE)
                .isEmailVerified(true)
                .roles(Set.of(userRole))
                .build();

    }

    @Test
    @DisplayName("login should issue a valid access token and store only a hashed refresh token")
    void login_ShouldIssueValidAccessTokenAndHashedRefreshToken() {
        LoginRequest request = new LoginRequest();
        request.setEmail(activeUser.getEmail());
        request.setPassword("plain-password");

        when(userRepository.findByEmailWithRoles(activeUser.getEmail())).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("plain-password", "encoded-password")).thenReturn(true);
        when(userProfileService.hasProfile(anyLong())).thenReturn(false);
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AuthResponse response = authService.login(request);

        assertNotNull(response.getAccessToken());
        assertNotNull(response.getRefreshToken());
        assertTrue(authService.verifyToken(response.getAccessToken()));
        assertEquals(3600L, response.getExpiresIn());
        assertEquals(activeUser.getEmail(), response.getUser().getEmail());
        assertTrue(response.getUser().getRoles().contains("USER"));

        ArgumentCaptor<RefreshToken> refreshTokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(refreshTokenCaptor.capture());
        verify(refreshTokenRepository).deleteByUserId(activeUser.getId());

        RefreshToken storedToken = refreshTokenCaptor.getValue();
        assertEquals(activeUser.getId(), storedToken.getUserId());
        assertEquals(64, storedToken.getToken().length());
        assertNotEquals(response.getRefreshToken(), storedToken.getToken());
        assertEquals(hashRefreshToken(response.getRefreshToken()), storedToken.getToken());
        assertTrue(storedToken.getExpiryDate().isAfter(LocalDateTime.now().plusHours(23)));
    }

    @Test
    @DisplayName("refreshToken should rotate tokens and keep refresh expiry capped to the original absolute lifetime")
    void refreshToken_ShouldRotateTokensAndKeepAbsoluteExpiry() {
        String oldPlainRefreshToken = "existing-refresh-token";
        LocalDateTime originalExpiry = LocalDateTime.now().plusHours(6);

        RefreshToken existingToken = new RefreshToken();
        existingToken.setId(10L);
        existingToken.setUserId(activeUser.getId());
        existingToken.setToken(hashRefreshToken(oldPlainRefreshToken));
        existingToken.setExpiryDate(originalExpiry);

        when(refreshTokenRepository.findByToken(hashRefreshToken(oldPlainRefreshToken)))
                .thenReturn(Optional.of(existingToken));
        when(userRepository.findByIdWithRoles(activeUser.getId())).thenReturn(Optional.of(activeUser));
        when(userProfileService.hasProfile(anyLong())).thenReturn(false);
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AuthResponse response = authService.refreshToken(oldPlainRefreshToken);

        assertNotNull(response.getAccessToken());
        assertNotNull(response.getRefreshToken());
        assertNotEquals(oldPlainRefreshToken, response.getRefreshToken());
        assertTrue(authService.verifyToken(response.getAccessToken()));
        assertEquals(activeUser.getEmail(), response.getUser().getEmail());

        ArgumentCaptor<RefreshToken> refreshTokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(refreshTokenCaptor.capture());
        verify(refreshTokenRepository).delete(existingToken);

        RefreshToken rotatedToken = refreshTokenCaptor.getValue();
        assertEquals(hashRefreshToken(response.getRefreshToken()), rotatedToken.getToken());
        assertEquals(originalExpiry, rotatedToken.getExpiryDate());
    }

    @Test
    @DisplayName("refreshToken should reject expired refresh tokens and remove them from storage")
    void refreshToken_ShouldRejectExpiredRefreshToken() {
        String expiredPlainRefreshToken = "expired-refresh-token";

        RefreshToken expiredToken = new RefreshToken();
        expiredToken.setId(11L);
        expiredToken.setUserId(activeUser.getId());
        expiredToken.setToken(hashRefreshToken(expiredPlainRefreshToken));
        expiredToken.setExpiryDate(LocalDateTime.now().minusMinutes(1));

        when(refreshTokenRepository.findByToken(hashRefreshToken(expiredPlainRefreshToken)))
                .thenReturn(Optional.of(expiredToken));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> authService.refreshToken(expiredPlainRefreshToken));

        assertEquals("Refresh token expired", exception.getMessage());
        verify(refreshTokenRepository).delete(expiredToken);
        verify(userRepository, never()).findByIdWithRoles(anyLong());
    }

    private String hashRefreshToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(("test-refresh-pepper:" + token).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
