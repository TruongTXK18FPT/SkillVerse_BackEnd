package com.exe.skillverse_backend.auth_service.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.course_service.entity.AssignmentSubmission;
import com.exe.skillverse_backend.course_service.entity.CodingSubmission;
import com.exe.skillverse_backend.course_service.entity.CourseEnrollment;
import com.exe.skillverse_backend.course_service.entity.ModuleProgress;
import com.exe.skillverse_backend.course_service.entity.CoursePurchase;
import com.exe.skillverse_backend.course_service.entity.Certificate;

import com.exe.skillverse_backend.shared.entity.Media;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users")
@Data
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = true) // Nullable for Google OAuth users
    private String password;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "primary_role", nullable = false)
    private PrimaryRole primaryRole = PrimaryRole.USER;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false)
    private AuthProvider authProvider = AuthProvider.LOCAL;

    /**
     * Indicates if user has linked their Google account.
     * Allows dual authentication: both email+password AND Google login.
     * - LOCAL user with googleLinked=true: Can use both methods
     * - LOCAL user with googleLinked=false: Only password login
     * - GOOGLE user with googleLinked=true: Originally registered via Google
     */
    @Builder.Default
    @Column(name = "google_linked", nullable = false)
    private boolean googleLinked = false;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UserStatus status = UserStatus.INACTIVE;

    @Builder.Default
    @Column(name = "is_email_verified", nullable = false)
    private boolean isEmailVerified = false;

    @Column(name = "verification_otp")
    private String verificationOtp;

    @Column(name = "otp_expiry_time")
    private LocalDateTime otpExpiryTime;

    @Column(name = "otp_attempts", nullable = false)
    @Builder.Default
    private Integer otpAttempts = 0;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
    @Builder.Default
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"), inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    /* ----------------- Course Service relations ----------------- */

    // 1) Courses do user là tác giả/giảng viên chính
    @Builder.Default
    @OneToMany(mappedBy = "author", cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<Course> courses = new HashSet<>();

    // 2) Media do user upload (đã có sẵn trong shared.Media)
    @Builder.Default
    @OneToMany(mappedBy = "uploadedByUser", cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<Media> mediaUploads = new HashSet<>();

    // 3) Assignment submissions do user nộp
    @Builder.Default
    @OneToMany(mappedBy = "user", cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<AssignmentSubmission> assignmentSubmissions = new HashSet<>();

    // 3b) Các bài assignment mà user là người chấm (grader)
    @Builder.Default
    @OneToMany(mappedBy = "gradedBy", cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<AssignmentSubmission> assignmentGradings = new HashSet<>();

    // 4) Coding submissions do user nộp (CODELAB)
    @Builder.Default
    @OneToMany(mappedBy = "user", cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<CodingSubmission> codingSubmissions = new HashSet<>();

    // 5) Enrollment: user ghi danh và học các khóa
    @Builder.Default
    @OneToMany(mappedBy = "user", cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<CourseEnrollment> enrollments = new HashSet<>();

    // 6) Module progress: tiến độ học từng module
    @Builder.Default
    @OneToMany(mappedBy = "user", cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<ModuleProgress> moduleProgresses = new HashSet<>();

    // 7) Course purchases: giao dịch mua khóa
    @Builder.Default
    @OneToMany(mappedBy = "user", cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<CoursePurchase> coursePurchases = new HashSet<>();

    // 8) Certificates: chứng chỉ đã cấp cho user
    @Builder.Default
    @OneToMany(mappedBy = "user", cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<Certificate> certificates = new HashSet<>();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
