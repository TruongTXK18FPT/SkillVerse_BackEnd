package com.exe.skillverse_backend.business_service.entity;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.business_service.entity.enums.MessageType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity lưu trữ tin nhắn trong phiên chat tuyển dụng
 */
@Entity
@Table(name = "recruitment_messages",
        indexes = {
                @Index(name = "idx_recruitment_message_session", columnList = "session_id"),
                @Index(name = "idx_recruitment_message_sender", columnList = "sender_id"),
                @Index(name = "idx_recruitment_message_created", columnList = "created_at")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecruitmentMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private RecruitmentSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    /**
     * Loại người gửi: RECRUITER hoặc CANDIDATE
     */
    @Column(name = "sender_role", nullable = false, length = 20)
    private String senderRole;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 20)
    @Builder.Default
    private MessageType messageType = MessageType.TEXT;

    /**
     * Loại tin nhắn đặc biệt cho recruitment
     */
    @Column(name = "action_type")
    private String actionType; // INVITE_JOB, VIEW_PROFILE, ACCEPT_INVITE, REJECT_INVITE, etc.

    @Column(name = "action_data", columnDefinition = "TEXT")
    private String actionData; // JSON data for action (e.g., job info)

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private Boolean isRead = false;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
