package com.exe.skillverse_backend.report_service.entity;

import com.exe.skillverse_backend.auth_service.entity.User;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Entity representing a violation report submitted by users against other users/content.
 * Users can report inappropriate content, harassment, spam, fraud, copyright violations, etc.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "violation_reports")
public class ViolationReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique report code for tracking purposes
     */
    @Column(name = "report_code", unique = true, nullable = false, length = 20)
    private String reportCode;

    /**
     * Title/summary of the report
     */
    @Column(nullable = false, length = 255)
    private String title;

    /**
     * The user who submitted this report (reporter)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    /**
     * The user being reported (reported user) - nullable for anonymous reports
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reported_user_id", nullable = true)
    private User reportedUser;

    /**
     * Name of the reported user (captured at submission time)
     */
    @Column(name = "reported_user_name", length = 100)
    private String reportedUserName;

    /**
     * Type/Category of the violation
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false)
    private ReportType reportType;

    /**
     * Severity level of the report
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ReportSeverity severity = ReportSeverity.MEDIUM;

    /**
     * Detailed description of the violation
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    /**
     * Current status of the report
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ReportStatus status = ReportStatus.PENDING;

    /**
     * Admin's response/decision notes
     */
    @Column(name = "admin_notes", columnDefinition = "TEXT")
    private String adminNotes;

    /**
     * Admin who is handling this report
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_admin_id")
    private User assignedAdmin;

    /**
     * Resolution/Action taken by admin
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "resolution_action")
    private ResolutionAction resolutionAction;

    /**
     * Timestamp when the report was resolved
     */
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    /**
     * Evidence files attached to this report
     */
    @Builder.Default
    @OneToMany(mappedBy = "violationReport", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<ReportEvidence> evidences = new HashSet<>();

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Report type/category
     */
    public enum ReportType {
        INAPPROPRIATE_CONTENT,  // Nội dung không phù hợp
        HARASSMENT,             // Quấy rối
        SPAM,                   // Spam và quảng cáo trái phép
        FRAUD,                  // Lừa đảo tài chính
        COPYRIGHT_VIOLATION,    // Vi phạm bản quyền
        HATE_SPEECH,            // Phát ngôn thù hận
        IMPERSONATION,          // Mạo danh
        MISINFORMATION,         // Thông tin sai lệch
        PRIVACY_VIOLATION,      // Vi phạm quyền riêng tư
        OTHER                   // Khác
    }

    /**
     * Severity level
     */
    public enum ReportSeverity {
        LOW,
        MEDIUM,
        HIGH
    }

    /**
     * Report processing status
     */
    public enum ReportStatus {
        PENDING,        // Chờ xử lý
        INVESTIGATING,  // Đang điều tra
        RESOLVED,       // Đã giải quyết
        DISMISSED       // Đã bỏ qua
    }

    /**
     * Actions that can be taken when resolving a report
     */
    public enum ResolutionAction {
        NO_ACTION,          // Không có hành động
        WARNING_ISSUED,     // Đã gửi cảnh báo
        CONTENT_REMOVED,    // Đã xóa nội dung
        ACCOUNT_SUSPENDED,  // Tạm khóa tài khoản
        ACCOUNT_BANNED,     // Cấm tài khoản vĩnh viễn
        ESCALATED           // Leo thang lên cấp cao hơn
    }
}
