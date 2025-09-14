package com.exe.skillverse_backend.shared.entity;

import com.exe.skillverse_backend.auth_service.entity.User;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_history", indexes = {
        @Index(name = "idx_user_history_user_id", columnList = "user_id"),
        @Index(name = "idx_user_history_timestamp", columnList = "timestamp")
})
@Data
public class UserHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @Column(name = "event_type", nullable = false)
    private String eventType; // PROFILE_UPDATE, COURSE_ENROLLED, COURSE_COMPLETED, etc.

    @Column(name = "object_type", nullable = false)
    private String objectType; // COURSE, MENTOR, CERTIFICATE, etc.

    @Column(name = "object_id", nullable = false)
    private Long objectId;

    @Column(nullable = false)
    private LocalDateTime timestamp = LocalDateTime.now();
}