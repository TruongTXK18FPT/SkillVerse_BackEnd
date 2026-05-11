package com.exe.skillverse_backend.admin_service.service;

import com.exe.skillverse_backend.admin_service.dto.request.AddRoleRequest;
import com.exe.skillverse_backend.admin_service.dto.request.ResetPasswordRequest;
import com.exe.skillverse_backend.admin_service.dto.request.UpdateUserProfileRequest;
import com.exe.skillverse_backend.admin_service.dto.request.UpdateUserRoleRequest;
import com.exe.skillverse_backend.admin_service.dto.request.UpdateUserStatusRequest;
import com.exe.skillverse_backend.admin_service.dto.response.AdminUserResponse;
import com.exe.skillverse_backend.admin_service.service.impl.AdminUserServiceImpl;
import com.exe.skillverse_backend.auth_service.entity.PrimaryRole;
import com.exe.skillverse_backend.auth_service.entity.Role;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.entity.UserStatus;
import com.exe.skillverse_backend.auth_service.repository.RefreshTokenRepository;
import com.exe.skillverse_backend.auth_service.repository.RoleRepository;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.course_service.repository.CourseEnrollmentRepository;
import com.exe.skillverse_backend.course_service.repository.CourseRepository;
import com.exe.skillverse_backend.course_service.service.CertificateService;
import com.exe.skillverse_backend.course_service.service.CourseService;
import com.exe.skillverse_backend.mentor_service.repository.MentorProfileRepository;
import com.exe.skillverse_backend.business_service.repository.RecruiterProfileRepository;
import com.exe.skillverse_backend.notification_service.service.NotificationService;
import com.exe.skillverse_backend.shared.exception.BadRequestException;
import com.exe.skillverse_backend.shared.exception.ForbiddenException;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import com.exe.skillverse_backend.user_service.service.UserProfileService;
import com.exe.skillverse_backend.wallet_service.service.WalletService;
import com.exe.skillverse_backend.wallet_service.service.WithdrawalService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * =========================================================================================
 * AdminUserServiceImpl Tests - Focused on Role Assignment Behavior
 * =========================================================================================
 *
 * Test Cases:
 * - setSubAdminRoles accepts valid sub-admin roles
 * - setSubAdminRoles with empty list removes existing sub-admin roles
 * - setSubAdminRoles rejects main roles (USER, MENTOR, ADMIN, etc.)
 * - setSubAdminRoles prevents self-edit
 * - updateUserRole rejects sub-admin roles
 * - updateUserRole accepts main roles
 *
 * Run: mvn test -Dtest=AdminUserServiceImplTest
 * =========================================================================================
 */
@ExtendWith(MockitoExtension.class)
public class AdminUserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserProfileService userProfileService;

    @Mock
    private EntityManager entityManager;

    @Mock
    private WalletService walletService;

    @Mock
    private WithdrawalService withdrawalService;

    @Mock
    private CourseService courseService;

    @Mock
    private CertificateService certificateService;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CourseEnrollmentRepository enrollmentRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private MentorProfileRepository mentorProfileRepository;

    @Mock
    private RecruiterProfileRepository recruiterProfileRepository;

    @InjectMocks
    private AdminUserServiceImpl adminUserService;

    private User adminUser;
    private User targetUser;
    private Role userAdminRole;
    private Role contentAdminRole;
    private Role userRole;

    @BeforeEach
    void setUp() {
        // Setup admin user (the one performing actions)
        adminUser = new User();
        adminUser.setId(1L);
        adminUser.setEmail("admin@test.com");
        adminUser.setPrimaryRole(PrimaryRole.ADMIN);
        adminUser.setStatus(UserStatus.ACTIVE);
        adminUser.setRoles(new HashSet<>());

        // Setup target user (the one being modified)
        targetUser = new User();
        targetUser.setId(2L);
        targetUser.setEmail("user@test.com");
        targetUser.setPrimaryRole(PrimaryRole.USER);
        targetUser.setStatus(UserStatus.ACTIVE);
        targetUser.setRoles(new HashSet<>());

        // Setup roles
        userAdminRole = new Role();
        userAdminRole.setId(1L);
        userAdminRole.setName("USER_ADMIN");

        contentAdminRole = new Role();
        contentAdminRole.setId(2L);
        contentAdminRole.setName("CONTENT_ADMIN");

        userRole = new Role();
        userRole.setId(3L);
        userRole.setName("USER");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void mockSecurityContext(Long userId) {
        // Create a mock Jwt with the userId claim
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("userId")).thenReturn(userId.toString());
        when(jwt.getSubject()).thenReturn(userId.toString());

        // Create a JwtAuthenticationToken (authenticated by default)
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt);
        auth.setAuthenticated(true);

        // Create a real SecurityContext and set the authentication
        SecurityContext ctx = new SecurityContextImpl();
        ctx.setAuthentication(auth);
        
        SecurityContextHolder.setContext(ctx);
    }

    // ============================================================================
    // setSubAdminRoles Tests
    // ============================================================================

    @Test
    void setSubAdminRoles_AcceptsValidSubAdminRoles() {
        // Arrange
        mockSecurityContext(1L); // Admin user (not self-edit)

        AddRoleRequest request = AddRoleRequest.builder()
                .userId(2L)
                .roles(List.of("USER_ADMIN", "CONTENT_ADMIN"))
                .build();

        when(userRepository.findByIdWithRoles(2L)).thenReturn(Optional.of(targetUser));
        when(roleRepository.findByName("USER_ADMIN")).thenReturn(Optional.of(userAdminRole));
        when(roleRepository.findByName("CONTENT_ADMIN")).thenReturn(Optional.of(contentAdminRole));
        when(userRepository.save(any(User.class))).thenReturn(targetUser);

        // Act
        AdminUserResponse response = adminUserService.setSubAdminRoles(request);

        // Assert
        assertNotNull(response);
        assertTrue(targetUser.getRoles().contains(userAdminRole));
        assertTrue(targetUser.getRoles().contains(contentAdminRole));
        verify(userRepository).save(targetUser);
    }

    @Test
    void setSubAdminRoles_EmptyListRemovesExistingSubAdminRoles() {
        // Arrange
        mockSecurityContext(1L); // Admin user (not self-edit)

        // Target user already has sub-admin roles
        targetUser.getRoles().add(userAdminRole);
        targetUser.getRoles().add(contentAdminRole);

        AddRoleRequest request = AddRoleRequest.builder()
                .userId(2L)
                .roles(List.of()) // Empty list
                .build();

        when(userRepository.findByIdWithRoles(2L)).thenReturn(Optional.of(targetUser));
        when(userRepository.save(any(User.class))).thenReturn(targetUser);

        // Act
        AdminUserResponse response = adminUserService.setSubAdminRoles(request);

        // Assert
        assertNotNull(response);
        assertFalse(targetUser.getRoles().contains(userAdminRole));
        assertFalse(targetUser.getRoles().contains(contentAdminRole));
        assertTrue(targetUser.getRoles().isEmpty());
        verify(userRepository).save(targetUser);
    }

    @Test
    void setSubAdminRoles_RejectsMainRoles() {
        // Arrange
        mockSecurityContext(1L); // Admin user (not self-edit)

        AddRoleRequest request = AddRoleRequest.builder()
                .userId(2L)
                .roles(List.of("USER", "MENTOR")) // Main roles, not sub-admin
                .build();

        when(userRepository.findByIdWithRoles(2L)).thenReturn(Optional.of(targetUser));

        // Act & Assert
        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> adminUserService.setSubAdminRoles(request));
        assertTrue(exception.getMessage().contains("Chỉ các vai trò phụ trợ"));
    }

    @Test
    void setSubAdminRoles_RejectsAdminRole() {
        // Arrange
        mockSecurityContext(1L); // Admin user (not self-edit)

        AddRoleRequest request = AddRoleRequest.builder()
                .userId(2L)
                .roles(List.of("ADMIN")) // ADMIN is a main role
                .build();

        when(userRepository.findByIdWithRoles(2L)).thenReturn(Optional.of(targetUser));

        // Act & Assert
        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> adminUserService.setSubAdminRoles(request));
        assertTrue(exception.getMessage().contains("Chỉ các vai trò phụ trợ"));
    }

    @Test
    void setSubAdminRoles_PreventsSelfEdit() {
        // Arrange
        mockSecurityContext(2L); // Same as target user (self-edit attempt)

        AddRoleRequest request = AddRoleRequest.builder()
                .userId(2L) // Same user as authenticated
                .roles(List.of("USER_ADMIN"))
                .build();

        // Act & Assert
        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> adminUserService.setSubAdminRoles(request));
        assertTrue(exception.getMessage().contains("Không thể tự thay đổi quyền của chính mình"));
    }

    @Test
    void setSubAdminRoles_ThrowsNotFoundWhenUserNotExists() {
        // Arrange
        mockSecurityContext(1L);

        AddRoleRequest request = AddRoleRequest.builder()
                .userId(999L) // Non-existent user
                .roles(List.of("USER_ADMIN"))
                .build();

        when(userRepository.findByIdWithRoles(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(NotFoundException.class,
                () -> adminUserService.setSubAdminRoles(request));
    }

    @Test
    void setSubAdminRoles_AdminCannotModifyAnotherAdminSubAdminRoles() {
        // Arrange: Actor trying to modify an ADMIN's sub-admin roles
        mockSecurityContext(1L); // Different user

        // Target is an ADMIN
        User targetAdmin = new User();
        targetAdmin.setId(2L);
        targetAdmin.setEmail("admin@test.com");
        targetAdmin.setPrimaryRole(PrimaryRole.ADMIN);
        targetAdmin.setStatus(UserStatus.ACTIVE);
        targetAdmin.setRoles(new HashSet<>());

        when(userRepository.findByIdWithRoles(2L)).thenReturn(Optional.of(targetAdmin));

        AddRoleRequest request = AddRoleRequest.builder()
                .userId(2L)
                .roles(List.of("USER_ADMIN"))
                .build();

        // Act & Assert
        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> adminUserService.setSubAdminRoles(request));
        assertTrue(exception.getMessage().contains("Không thể thay đổi quyền của quản trị viên cấp cao"));

        // Verify roleRepository methods are not called after guard
        verify(roleRepository, never()).findByName(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void setSubAdminRoles_NonAdminTargetBehaviorUnchanged() {
        // Arrange: Admin modifies USER target's sub-admin roles
        mockSecurityContext(1L); // Admin performing the change

        // Target is a regular USER (not admin)
        when(userRepository.findByIdWithRoles(2L)).thenReturn(Optional.of(targetUser));
        when(roleRepository.findByName("USER_ADMIN")).thenReturn(Optional.of(userAdminRole));
        when(userRepository.save(any(User.class))).thenReturn(targetUser);

        AddRoleRequest request = AddRoleRequest.builder()
                .userId(2L)
                .roles(List.of("USER_ADMIN"))
                .build();

        // Act
        AdminUserResponse response = adminUserService.setSubAdminRoles(request);

        // Assert - Should succeed for non-admin targets
        assertNotNull(response);
        verify(roleRepository).findByName("USER_ADMIN");
        verify(userRepository).save(any(User.class));
    }

    // ============================================================================
    // updateUserRole Tests
    // ============================================================================

    @Test
    void updateUserRole_RejectsSubAdminRoles() {
        // Arrange
        mockSecurityContext(1L); // Different user (admin) performing the change
        UpdateUserRoleRequest request = UpdateUserRoleRequest.builder()
                .userId(2L)
                .primaryRole(PrimaryRole.USER_ADMIN) // Sub-admin role
                .build();

        // Act & Assert - Validation rejects before any repository call
        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> adminUserService.updateUserRole(request));
        assertTrue(exception.getMessage().contains("Chỉ các vai trò chính"));
        assertTrue(exception.getMessage().contains("Các vai trò phụ trợ phải được gán"));
    }

    @Test
    void updateUserRole_AcceptsMainRole_User() {
        // Arrange
        mockSecurityContext(1L); // Different user (admin) performing the change
        UpdateUserRoleRequest request = UpdateUserRoleRequest.builder()
                .userId(2L)
                .primaryRole(PrimaryRole.USER)
                .build();

        when(userRepository.findByIdWithRoles(2L)).thenReturn(Optional.of(targetUser));
        when(roleRepository.findByName("USER")).thenReturn(Optional.of(userRole));
        when(userRepository.save(any(User.class))).thenReturn(targetUser);

        // Act
        AdminUserResponse response = adminUserService.updateUserRole(request);

        // Assert
        assertNotNull(response);
        assertEquals(PrimaryRole.USER, targetUser.getPrimaryRole());
    }

    @Test
    void updateUserRole_AcceptsMainRole_Mentor() {
        // Arrange
        mockSecurityContext(1L); // Different user (admin) performing the change
        UpdateUserRoleRequest request = UpdateUserRoleRequest.builder()
                .userId(2L)
                .primaryRole(PrimaryRole.MENTOR)
                .build();

        Role mentorRole = new Role();
        mentorRole.setId(4L);
        mentorRole.setName("MENTOR");

        when(userRepository.findByIdWithRoles(2L)).thenReturn(Optional.of(targetUser));
        when(roleRepository.findByName("MENTOR")).thenReturn(Optional.of(mentorRole));
        when(userRepository.save(any(User.class))).thenReturn(targetUser);

        // Act
        AdminUserResponse response = adminUserService.updateUserRole(request);

        // Assert
        assertNotNull(response);
        assertEquals(PrimaryRole.MENTOR, targetUser.getPrimaryRole());
    }

    @Test
    void updateUserRole_AcceptsMainRole_Admin() {
        // Arrange
        mockSecurityContext(1L); // Different user (admin) performing the change
        UpdateUserRoleRequest request = UpdateUserRoleRequest.builder()
                .userId(2L)
                .primaryRole(PrimaryRole.ADMIN)
                .build();

        Role adminRole = new Role();
        adminRole.setId(5L);
        adminRole.setName("ADMIN");

        when(userRepository.findByIdWithRoles(2L)).thenReturn(Optional.of(targetUser));
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(adminRole));
        when(userRepository.save(any(User.class))).thenReturn(targetUser);

        // Act
        AdminUserResponse response = adminUserService.updateUserRole(request);

        // Assert
        assertNotNull(response);
        assertEquals(PrimaryRole.ADMIN, targetUser.getPrimaryRole());
    }

    @Test
    void updateUserRole_AcceptsMainRole_Recruiter() {
        // Arrange
        mockSecurityContext(1L); // Different user (admin) performing the change
        UpdateUserRoleRequest request = UpdateUserRoleRequest.builder()
                .userId(2L)
                .primaryRole(PrimaryRole.RECRUITER)
                .build();

        Role recruiterRole = new Role();
        recruiterRole.setId(6L);
        recruiterRole.setName("RECRUITER");

        when(userRepository.findByIdWithRoles(2L)).thenReturn(Optional.of(targetUser));
        when(roleRepository.findByName("RECRUITER")).thenReturn(Optional.of(recruiterRole));
        when(userRepository.save(any(User.class))).thenReturn(targetUser);

        // Act
        AdminUserResponse response = adminUserService.updateUserRole(request);

        // Assert
        assertNotNull(response);
        assertEquals(PrimaryRole.RECRUITER, targetUser.getPrimaryRole());
    }



    @Test
    void updateUserRole_ThrowsNotFoundWhenUserNotExists() {
        // Arrange
        mockSecurityContext(1L); // Different user (admin) performing the change
        UpdateUserRoleRequest request = UpdateUserRoleRequest.builder()
                .userId(999L)
                .primaryRole(PrimaryRole.MENTOR)
                .build();

        when(userRepository.findByIdWithRoles(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(NotFoundException.class,
                () -> adminUserService.updateUserRole(request));
    }

    // ============================================================================
    // USER_ADMIN Visibility Restriction Tests
    // ============================================================================

    private void mockSecurityContextWithAuthorities(Long userId, List<GrantedAuthority> authorities) {
        // For USER_ADMIN visibility checks and getCurrentUserId()
        // isUserAdminOnly() checks authorities, getCurrentUserId() needs JWT claims
        Jwt jwt = mock(Jwt.class, withSettings().lenient());
        when(jwt.getClaimAsString("userId")).thenReturn(userId.toString());
        when(jwt.getSubject()).thenReturn(userId.toString());

        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, authorities);
        auth.setAuthenticated(true);

        SecurityContext ctx = new SecurityContextImpl();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
    }

    @Test
    void getAllUsers_UserAdminWithRoleAdminFilter_ReturnsNoAdminUsers() {
        // Arrange: Mock USER_ADMIN only (no full ADMIN role)
        List<GrantedAuthority> userAdminOnly = List.of(
                new SimpleGrantedAuthority("ROLE_USER_ADMIN")
        );
        mockSecurityContextWithAuthorities(1L, userAdminOnly);

        // Create some users including ADMIN
        User normalUser = new User();
        normalUser.setId(2L);
        normalUser.setEmail("user@test.com");
        normalUser.setPrimaryRole(PrimaryRole.USER);
        normalUser.setStatus(UserStatus.ACTIVE);

        User adminUser = new User();
        adminUser.setId(3L);
        adminUser.setEmail("admin@test.com");
        adminUser.setPrimaryRole(PrimaryRole.ADMIN);
        adminUser.setStatus(UserStatus.ACTIVE);

        // Even with role=ADMIN filter, USER_ADMIN should see no results
        when(userRepository.findByPrimaryRole(PrimaryRole.ADMIN))
                .thenReturn(List.of(adminUser));

        // Act
        var response = adminUserService.getAllUsers(PrimaryRole.ADMIN, null, null);

        // Assert: USER_ADMIN should not see ADMIN accounts even with filter
        assertNotNull(response);
        assertEquals(0, response.getUsers().size());
    }

    @Test
    void getUserById_UserAdminViewingAdminTarget_ThrowsForbiddenException() {
        // Arrange: Mock USER_ADMIN only
        List<GrantedAuthority> userAdminOnly = List.of(
                new SimpleGrantedAuthority("ROLE_USER_ADMIN")
        );
        mockSecurityContextWithAuthorities(1L, userAdminOnly);

        // Target user is an ADMIN
        User targetAdmin = new User();
        targetAdmin.setId(2L);
        targetAdmin.setEmail("admin@test.com");
        targetAdmin.setPrimaryRole(PrimaryRole.ADMIN);
        targetAdmin.setStatus(UserStatus.ACTIVE);

        when(userRepository.findByIdWithRoles(2L)).thenReturn(Optional.of(targetAdmin));

        // Act & Assert: USER_ADMIN cannot view ADMIN accounts
        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> adminUserService.getUserById(2L));
        assertTrue(exception.getMessage().contains("Không thể xem tài khoản quản trị viên cấp cao"));
    }

    @Test
    void getUserDetailById_UserAdminViewingAdminTarget_ThrowsForbiddenException() {
        // Arrange: Mock USER_ADMIN only
        List<GrantedAuthority> userAdminOnly = List.of(
                new SimpleGrantedAuthority("ROLE_USER_ADMIN")
        );
        mockSecurityContextWithAuthorities(1L, userAdminOnly);

        // Target user is an ADMIN
        User targetAdmin = new User();
        targetAdmin.setId(2L);
        targetAdmin.setEmail("admin@test.com");
        targetAdmin.setPrimaryRole(PrimaryRole.ADMIN);
        targetAdmin.setStatus(UserStatus.ACTIVE);
        targetAdmin.setRoles(new HashSet<>());
        targetAdmin.setEnrollments(new HashSet<>());

        when(userRepository.findByIdWithRoles(2L)).thenReturn(Optional.of(targetAdmin));

        // Act & Assert: USER_ADMIN cannot view ADMIN accounts
        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> adminUserService.getUserDetailById(2L));
        assertTrue(exception.getMessage().contains("Không thể xem tài khoản quản trị viên cấp cao"));
    }

    @Test
    void getUserById_AdminViewingAdminTarget_Success() {
        // Arrange: Mock full ADMIN
        List<GrantedAuthority> fullAdmin = List.of(
                new SimpleGrantedAuthority("ROLE_ADMIN"),
                new SimpleGrantedAuthority("ROLE_USER_ADMIN")
        );
        mockSecurityContextWithAuthorities(1L, fullAdmin);

        // Target user is an ADMIN
        User targetAdmin = new User();
        targetAdmin.setId(2L);
        targetAdmin.setEmail("admin@test.com");
        targetAdmin.setPrimaryRole(PrimaryRole.ADMIN);
        targetAdmin.setStatus(UserStatus.ACTIVE);

        when(userRepository.findByIdWithRoles(2L)).thenReturn(Optional.of(targetAdmin));

        // Act: Full ADMIN can view other ADMIN accounts
        AdminUserResponse response = adminUserService.getUserById(2L);

        // Assert
        assertNotNull(response);
        assertEquals(2L, response.getId());
    }

    @Test
    void getUserDetailById_AdminViewingAdminTarget_Success() {
        // Arrange: Mock full ADMIN
        List<GrantedAuthority> fullAdmin = List.of(
                new SimpleGrantedAuthority("ROLE_ADMIN"),
                new SimpleGrantedAuthority("ROLE_USER_ADMIN")
        );
        mockSecurityContextWithAuthorities(1L, fullAdmin);

        // Target user is an ADMIN
        User targetAdmin = new User();
        targetAdmin.setId(2L);
        targetAdmin.setEmail("admin@test.com");
        targetAdmin.setPrimaryRole(PrimaryRole.ADMIN);
        targetAdmin.setStatus(UserStatus.ACTIVE);
        targetAdmin.setRoles(new HashSet<>());
        targetAdmin.setEnrollments(new HashSet<>());

        when(userRepository.findByIdWithRoles(2L)).thenReturn(Optional.of(targetAdmin));

        // Act: Full ADMIN can view other ADMIN accounts
        var response = adminUserService.getUserDetailById(2L);

        // Assert
        assertNotNull(response);
        assertEquals(2L, response.getId());
    }

    // ============================================================================
    // Role Consistency Hardening Tests
    // ============================================================================

    @Test
    void updateUserRole_PreventsSelfRoleChange() {
        // Arrange: Try to change own role
        mockSecurityContext(2L); // Same as target user
        UpdateUserRoleRequest request = UpdateUserRoleRequest.builder()
                .userId(2L)
                .primaryRole(PrimaryRole.MENTOR)
                .build();

        // Act & Assert
        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> adminUserService.updateUserRole(request));
        assertTrue(exception.getMessage().contains("Không thể tự thay đổi vai trò"));
    }

    @Test
    void updateUserRole_RemovesAllMainRolesBeforeAddingNew() {
        // Arrange: User has multiple main roles (edge case cleanup)
        mockSecurityContext(1L); // Admin performing change

        // Setup user with problematic state: has both USER and MENTOR in roles
        Role mentorRole = new Role();
        mentorRole.setId(4L);
        mentorRole.setName("MENTOR");
        targetUser.getRoles().add(mentorRole); // Now has [USER, MENTOR]

        UpdateUserRoleRequest request = UpdateUserRoleRequest.builder()
                .userId(2L)
                .primaryRole(PrimaryRole.RECRUITER)
                .build();

        Role recruiterRole = new Role();
        recruiterRole.setId(6L);
        recruiterRole.setName("RECRUITER");

        when(userRepository.findByIdWithRoles(2L)).thenReturn(Optional.of(targetUser));
        when(roleRepository.findByName("RECRUITER")).thenReturn(Optional.of(recruiterRole));
        when(userRepository.save(any(User.class))).thenReturn(targetUser);

        // Act
        adminUserService.updateUserRole(request);

        // Assert: Should only have RECRUITER, no USER or MENTOR
        Set<String> remainingRoles = targetUser.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());
        assertEquals(Set.of("RECRUITER"), remainingRoles);
        assertEquals(PrimaryRole.RECRUITER, targetUser.getPrimaryRole());
    }

    @Test
    void updateUserRole_DemotingAdminTarget_ThrowsForbiddenException() {
        // Arrange: ADMIN target cannot be demoted by another admin (strict super-admin protection)
        mockSecurityContext(1L); // Admin performing change

        // Setup user as ADMIN with sub-admin roles
        targetUser.setPrimaryRole(PrimaryRole.ADMIN);
        Role adminRole = new Role();
        adminRole.setId(5L);
        adminRole.setName("ADMIN");
        targetUser.getRoles().add(adminRole);

        // Add sub-admin roles
        Role userAdminRole = new Role();
        userAdminRole.setId(8L);
        userAdminRole.setName("USER_ADMIN");
        Role contentAdminRole = new Role();
        contentAdminRole.setId(9L);
        contentAdminRole.setName("CONTENT_ADMIN");
        targetUser.getRoles().add(userAdminRole);
        targetUser.getRoles().add(contentAdminRole);

        UpdateUserRoleRequest request = UpdateUserRoleRequest.builder()
                .userId(2L)
                .primaryRole(PrimaryRole.USER)
                .build();

        when(userRepository.findByIdWithRoles(2L)).thenReturn(Optional.of(targetUser));

        // Act & Assert: Strict super-admin protection forbids modifying another ADMIN
        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> adminUserService.updateUserRole(request));
        assertTrue(exception.getMessage().contains("Không thể thay đổi vai trò của quản trị viên cấp cao"));

        // Verify no refresh token deletion since guard blocked before save
        verify(refreshTokenRepository, never()).deleteByUserId(any());
    }

    @Test
    void updateUserRole_NonAdminTransitionKeepsSubAdminRoles() {
        // Arrange: USER with sub-admin roles promoted to MENTOR
        mockSecurityContext(1L); // Admin performing change

        // Setup user as USER with sub-admin roles
        Role userAdminRole = new Role();
        userAdminRole.setId(8L);
        userAdminRole.setName("USER_ADMIN");
        targetUser.getRoles().add(userAdminRole);

        UpdateUserRoleRequest request = UpdateUserRoleRequest.builder()
                .userId(2L)
                .primaryRole(PrimaryRole.MENTOR)
                .build();

        Role mentorRole = new Role();
        mentorRole.setId(4L);
        mentorRole.setName("MENTOR");

        when(userRepository.findByIdWithRoles(2L)).thenReturn(Optional.of(targetUser));
        when(roleRepository.findByName("MENTOR")).thenReturn(Optional.of(mentorRole));
        when(userRepository.save(any(User.class))).thenReturn(targetUser);

        // Act
        adminUserService.updateUserRole(request);

        // Assert: Should have MENTOR and keep USER_ADMIN
        Set<String> remainingRoles = targetUser.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());
        assertTrue(remainingRoles.contains("MENTOR"));
        assertTrue(remainingRoles.contains("USER_ADMIN"));
        assertFalse(remainingRoles.contains("USER"));
        assertEquals(PrimaryRole.MENTOR, targetUser.getPrimaryRole());
    }

    @Test
    void updateUserStatus_PreventsSelfStatusChange() {
        // Arrange: Try to change own status
        mockSecurityContext(2L); // Same as target user
        UpdateUserStatusRequest request = UpdateUserStatusRequest.builder()
                .userId(2L)
                .status(UserStatus.INACTIVE)
                .build();

        // Act & Assert - Self-check guard throws before repository call
        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> adminUserService.updateUserStatus(request));
        assertTrue(exception.getMessage().contains("Không thể tự thay đổi trạng thái"));
    }

    @Test
    void deleteUser_PreventsSelfSoftDelete() {
        // Arrange: Try to delete self
        mockSecurityContext(2L); // Same as target user

        // Act & Assert - Self-check guard throws before repository call
        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> adminUserService.deleteUser(2L));
        assertTrue(exception.getMessage().contains("Không thể tự xóa tài khoản"));
    }

    @Test
    void permanentlyDeleteUser_PreventsSelfPermanentDelete() {
        // Arrange: Try to permanently delete self
        mockSecurityContext(2L); // Same as target user
        // Note: Self-check throws before status validation or repository call

        // Act & Assert - Self-check guard throws before repository call
        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> adminUserService.permanentlyDeleteUser(2L));
        assertTrue(exception.getMessage().contains("Không thể tự xóa vĩnh viễn"));
    }

    // ============================================================================
    // Super-Admin Protection Tests
    // ============================================================================

    @Test
    void updateUserProfile_UserAdminOnAdminTarget_ThrowsForbiddenException() {
        // Arrange: USER_ADMIN trying to update ADMIN profile
        List<GrantedAuthority> userAdminOnly = List.of(
                new SimpleGrantedAuthority("ROLE_USER_ADMIN")
        );
        mockSecurityContextWithAuthorities(1L, userAdminOnly);

        // Target is an ADMIN
        User targetAdmin = new User();
        targetAdmin.setId(2L);
        targetAdmin.setEmail("admin@test.com");
        targetAdmin.setPrimaryRole(PrimaryRole.ADMIN);
        targetAdmin.setStatus(UserStatus.ACTIVE);
        when(userRepository.findById(2L)).thenReturn(Optional.of(targetAdmin));

        UpdateUserProfileRequest request = UpdateUserProfileRequest.builder()
                .userId(2L)
                .firstName("NewName")
                .build();

        // Act & Assert
        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> adminUserService.updateUserProfile(request));
        assertTrue(exception.getMessage().contains("Không thể cập nhật tài khoản quản trị viên cấp cao"));
    }

    @Test
    void updateUserProfile_FullAdminOnAdminTarget_ThrowsForbiddenException() {
        // Arrange: Full ADMIN cannot update another ADMIN (strict protection)
        List<GrantedAuthority> fullAdmin = List.of(
                new SimpleGrantedAuthority("ROLE_ADMIN"),
                new SimpleGrantedAuthority("ROLE_USER_ADMIN")
        );
        mockSecurityContextWithAuthorities(1L, fullAdmin);

        // Target is an ADMIN
        User targetAdmin = new User();
        targetAdmin.setId(2L);
        targetAdmin.setEmail("admin@test.com");
        targetAdmin.setPrimaryRole(PrimaryRole.ADMIN);
        targetAdmin.setStatus(UserStatus.ACTIVE);
        when(userRepository.findById(2L)).thenReturn(Optional.of(targetAdmin));

        UpdateUserProfileRequest request = UpdateUserProfileRequest.builder()
                .userId(2L)
                .firstName("NewName")
                .build();

        // Act & Assert
        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> adminUserService.updateUserProfile(request));
        assertTrue(exception.getMessage().contains("Không thể cập nhật tài khoản quản trị viên cấp cao"));
    }

    @Test
    void updateUserProfile_NonAdminTargetBehaviorUnchanged() {
        // Arrange: Admin updates non-ADMIN target profile
        List<GrantedAuthority> fullAdmin = List.of(
                new SimpleGrantedAuthority("ROLE_ADMIN"),
                new SimpleGrantedAuthority("ROLE_USER_ADMIN")
        );
        mockSecurityContextWithAuthorities(1L, fullAdmin);

        // Target is a regular USER (not admin)
        when(userRepository.findById(2L)).thenReturn(Optional.of(targetUser));
        when(userRepository.save(any(User.class))).thenReturn(targetUser);

        UpdateUserProfileRequest request = UpdateUserProfileRequest.builder()
                .userId(2L)
                .firstName("NewName")
                .build();

        // Act
        AdminUserResponse response = adminUserService.updateUserProfile(request);

        // Assert - Should succeed for non-admin targets
        assertNotNull(response);
        verify(userRepository).save(any(User.class));
    }

    @Test
    void updateUserRole_AdminCannotDemoteAnotherAdmin() {
        // Arrange: One admin trying to demote another admin
        mockSecurityContext(1L); // Different admin

        // Target is an ADMIN
        User targetAdmin = new User();
        targetAdmin.setId(2L);
        targetAdmin.setEmail("admin@test.com");
        targetAdmin.setPrimaryRole(PrimaryRole.ADMIN);
        targetAdmin.setStatus(UserStatus.ACTIVE);
        targetAdmin.setRoles(new HashSet<>());
        when(userRepository.findByIdWithRoles(2L)).thenReturn(Optional.of(targetAdmin));

        UpdateUserRoleRequest request = UpdateUserRoleRequest.builder()
                .userId(2L)
                .primaryRole(PrimaryRole.USER)
                .build();

        // Act & Assert
        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> adminUserService.updateUserRole(request));
        assertTrue(exception.getMessage().contains("Không thể thay đổi vai trò của quản trị viên cấp cao"));
    }

    @Test
    void updateUserStatus_AdminCannotBanAnotherAdmin() {
        // Arrange: One admin trying to ban another admin
        mockSecurityContext(1L); // Different admin

        // Target is an ADMIN
        User targetAdmin = new User();
        targetAdmin.setId(2L);
        targetAdmin.setEmail("admin@test.com");
        targetAdmin.setPrimaryRole(PrimaryRole.ADMIN);
        targetAdmin.setStatus(UserStatus.ACTIVE);
        when(userRepository.findById(2L)).thenReturn(Optional.of(targetAdmin));

        UpdateUserStatusRequest request = UpdateUserStatusRequest.builder()
                .userId(2L)
                .status(UserStatus.INACTIVE)
                .build();

        // Act & Assert
        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> adminUserService.updateUserStatus(request));
        assertTrue(exception.getMessage().contains("Không thể thay đổi trạng thái của quản trị viên cấp cao"));
    }

    @Test
    void deleteUser_AdminCannotDeleteAnotherAdmin() {
        // Arrange: One admin trying to delete another admin
        mockSecurityContext(1L); // Different admin

        // Target is an ADMIN
        User targetAdmin = new User();
        targetAdmin.setId(2L);
        targetAdmin.setEmail("admin@test.com");
        targetAdmin.setPrimaryRole(PrimaryRole.ADMIN);
        targetAdmin.setStatus(UserStatus.ACTIVE);
        when(userRepository.findById(2L)).thenReturn(Optional.of(targetAdmin));

        // Act & Assert
        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> adminUserService.deleteUser(2L));
        assertTrue(exception.getMessage().contains("Không thể xóa tài khoản quản trị viên cấp cao"));
    }

    @Test
    void permanentlyDeleteUser_AdminCannotPermanentlyDeleteAnotherAdmin() {
        // Arrange: One admin trying to permanently delete another admin
        mockSecurityContext(1L); // Different admin

        // Target is an inactive ADMIN
        User targetAdmin = new User();
        targetAdmin.setId(2L);
        targetAdmin.setEmail("admin@test.com");
        targetAdmin.setPrimaryRole(PrimaryRole.ADMIN);
        targetAdmin.setStatus(UserStatus.INACTIVE);
        when(userRepository.findById(2L)).thenReturn(Optional.of(targetAdmin));

        // Act & Assert
        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> adminUserService.permanentlyDeleteUser(2L));
        assertTrue(exception.getMessage().contains("Không thể xóa vĩnh viễn tài khoản quản trị viên cấp cao"));
    }

    @Test
    void resetUserPassword_AdminCannotResetAnotherAdminPassword() {
        // Arrange: One admin trying to reset another admin's password
        mockSecurityContext(1L); // Different admin

        // Target is an ADMIN
        User targetAdmin = new User();
        targetAdmin.setId(2L);
        targetAdmin.setEmail("admin@test.com");
        targetAdmin.setPrimaryRole(PrimaryRole.ADMIN);
        targetAdmin.setStatus(UserStatus.ACTIVE);
        when(userRepository.findById(2L)).thenReturn(Optional.of(targetAdmin));

        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .userId(2L)
                .newPassword("newPassword123")
                .build();

        // Act & Assert
        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> adminUserService.resetUserPassword(request));
        assertTrue(exception.getMessage().contains("Không thể đặt lại mật khẩu của quản trị viên cấp cao"));
    }

    @Test
    void updateUserRole_AdminProtectionTriggersBeforeLastActiveCheck() {
        // Arrange: Trying to demote an admin - admin protection triggers first
        mockSecurityContext(1L); // Different user

        // Target is an admin
        User targetAdmin = new User();
        targetAdmin.setId(2L);
        targetAdmin.setEmail("admin@test.com");
        targetAdmin.setPrimaryRole(PrimaryRole.ADMIN);
        targetAdmin.setStatus(UserStatus.ACTIVE);
        targetAdmin.setRoles(new HashSet<>());
        when(userRepository.findByIdWithRoles(2L)).thenReturn(Optional.of(targetAdmin));

        UpdateUserRoleRequest request = UpdateUserRoleRequest.builder()
                .userId(2L)
                .primaryRole(PrimaryRole.USER)
                .build();

        // Act & Assert - Admin protection triggers before last active check
        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> adminUserService.updateUserRole(request));
        assertTrue(exception.getMessage().contains("Không thể thay đổi vai trò của quản trị viên cấp cao"));
    }

    @Test
    void updateUserStatus_AdminProtectionTriggersBeforeLastActiveCheck() {
        // Arrange: Trying to ban an admin - admin protection triggers first
        mockSecurityContext(1L); // Different user

        // Target is an admin
        User targetAdmin = new User();
        targetAdmin.setId(2L);
        targetAdmin.setEmail("admin@test.com");
        targetAdmin.setPrimaryRole(PrimaryRole.ADMIN);
        targetAdmin.setStatus(UserStatus.ACTIVE);
        when(userRepository.findById(2L)).thenReturn(Optional.of(targetAdmin));

        UpdateUserStatusRequest request = UpdateUserStatusRequest.builder()
                .userId(2L)
                .status(UserStatus.INACTIVE)
                .build();

        // Act & Assert - Admin protection triggers before last active check
        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> adminUserService.updateUserStatus(request));
        assertTrue(exception.getMessage().contains("Không thể thay đổi trạng thái của quản trị viên cấp cao"));
    }

    @Test
    void updateUserRole_NonAdminTargetBehaviorUnchanged() {
        // Arrange: Promoting a regular user (should work fine)
        mockSecurityContext(1L); // Admin performing the change

        // Target is a regular USER (not admin)
        when(userRepository.findByIdWithRoles(2L)).thenReturn(Optional.of(targetUser));

        Role adminRole = new Role();
        adminRole.setId(5L);
        adminRole.setName("ADMIN");

        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(adminRole));
        when(userRepository.save(any(User.class))).thenReturn(targetUser);

        UpdateUserRoleRequest request = UpdateUserRoleRequest.builder()
                .userId(2L)
                .primaryRole(PrimaryRole.ADMIN)
                .build();

        // Act
        AdminUserResponse response = adminUserService.updateUserRole(request);

        // Assert - Should succeed for non-admin targets
        assertNotNull(response);
        assertEquals(PrimaryRole.ADMIN, targetUser.getPrimaryRole());
    }

    // ============================================================================
    // Refresh Token Invalidation Tests
    // ============================================================================

    @Test
    void updateUserRole_Success_DeletesRefreshToken() {
        // Arrange: Successful role change should invalidate refresh token
        mockSecurityContext(1L); // Admin performing the change

        Role mentorRole = new Role();
        mentorRole.setId(4L);
        mentorRole.setName("MENTOR");

        when(userRepository.findByIdWithRoles(2L)).thenReturn(Optional.of(targetUser));
        when(roleRepository.findByName("MENTOR")).thenReturn(Optional.of(mentorRole));
        when(userRepository.save(any(User.class))).thenReturn(targetUser);

        UpdateUserRoleRequest request = UpdateUserRoleRequest.builder()
                .userId(2L)
                .primaryRole(PrimaryRole.MENTOR)
                .build();

        // Act
        AdminUserResponse response = adminUserService.updateUserRole(request);

        // Assert
        assertNotNull(response);
        verify(refreshTokenRepository).deleteByUserId(2L);
    }

    @Test
    void updateUserRole_SelfChangeForbidden_DoesNotDeleteRefreshToken() {
        // Arrange: Self-change guard triggers before save, refresh token should NOT be deleted
        mockSecurityContext(2L); // Same as target user

        UpdateUserRoleRequest request = UpdateUserRoleRequest.builder()
                .userId(2L)
                .primaryRole(PrimaryRole.MENTOR)
                .build();

        // Act & Assert
        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> adminUserService.updateUserRole(request));
        assertTrue(exception.getMessage().contains("Không thể tự thay đổi vai trò"));

        // Verify refresh token was NOT deleted (guard threw before save)
        verify(refreshTokenRepository, never()).deleteByUserId(any());
    }

    @Test
    void updateUserRole_AdminTargetForbidden_DoesNotDeleteRefreshToken() {
        // Arrange: ADMIN protection guard triggers before save
        mockSecurityContext(1L); // Different user

        // Target is an ADMIN
        User targetAdmin = new User();
        targetAdmin.setId(2L);
        targetAdmin.setEmail("admin@test.com");
        targetAdmin.setPrimaryRole(PrimaryRole.ADMIN);
        targetAdmin.setStatus(UserStatus.ACTIVE);
        targetAdmin.setRoles(new HashSet<>());
        when(userRepository.findByIdWithRoles(2L)).thenReturn(Optional.of(targetAdmin));

        UpdateUserRoleRequest request = UpdateUserRoleRequest.builder()
                .userId(2L)
                .primaryRole(PrimaryRole.USER)
                .build();

        // Act & Assert
        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> adminUserService.updateUserRole(request));
        assertTrue(exception.getMessage().contains("Không thể thay đổi vai trò của quản trị viên cấp cao"));

        // Verify refresh token was NOT deleted (guard threw before save)
        verify(refreshTokenRepository, never()).deleteByUserId(any());
    }

    @Test
    void setSubAdminRoles_Success_DeletesRefreshToken() {
        // Arrange: Successful sub-admin role change should invalidate refresh token
        mockSecurityContext(1L); // Admin performing the change

        when(userRepository.findByIdWithRoles(2L)).thenReturn(Optional.of(targetUser));
        when(roleRepository.findByName("USER_ADMIN")).thenReturn(Optional.of(userAdminRole));
        when(userRepository.save(any(User.class))).thenReturn(targetUser);

        AddRoleRequest request = AddRoleRequest.builder()
                .userId(2L)
                .roles(List.of("USER_ADMIN"))
                .build();

        // Act
        AdminUserResponse response = adminUserService.setSubAdminRoles(request);

        // Assert
        assertNotNull(response);
        verify(refreshTokenRepository).deleteByUserId(2L);
    }

    @Test
    void setSubAdminRoles_InvalidRole_DoesNotDeleteRefreshToken() {
        // Arrange: Invalid role validation fails before save
        mockSecurityContext(1L); // Admin performing the change

        when(userRepository.findByIdWithRoles(2L)).thenReturn(Optional.of(targetUser));

        // Request with invalid main role (not sub-admin)
        AddRoleRequest request = AddRoleRequest.builder()
                .userId(2L)
                .roles(List.of("USER")) // Main role, not sub-admin
                .build();

        // Act & Assert
        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> adminUserService.setSubAdminRoles(request));
        assertTrue(exception.getMessage().contains("Vai trò không hợp lệ"));

        // Verify refresh token was NOT deleted (validation failed before save)
        verify(refreshTokenRepository, never()).deleteByUserId(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void setSubAdminRoles_AdminTargetForbidden_DoesNotDeleteRefreshToken() {
        // Arrange: ADMIN protection guard triggers before save
        mockSecurityContext(1L); // Different user

        // Target is an ADMIN
        User targetAdmin = new User();
        targetAdmin.setId(2L);
        targetAdmin.setEmail("admin@test.com");
        targetAdmin.setPrimaryRole(PrimaryRole.ADMIN);
        targetAdmin.setStatus(UserStatus.ACTIVE);
        targetAdmin.setRoles(new HashSet<>());
        when(userRepository.findByIdWithRoles(2L)).thenReturn(Optional.of(targetAdmin));

        AddRoleRequest request = AddRoleRequest.builder()
                .userId(2L)
                .roles(List.of("USER_ADMIN"))
                .build();

        // Act & Assert
        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> adminUserService.setSubAdminRoles(request));
        assertTrue(exception.getMessage().contains("Không thể thay đổi quyền của quản trị viên cấp cao"));

        // Verify refresh token was NOT deleted (guard threw before save)
        verify(refreshTokenRepository, never()).deleteByUserId(any());
        verify(roleRepository, never()).findByName(any());
        verify(userRepository, never()).save(any());
    }
}
