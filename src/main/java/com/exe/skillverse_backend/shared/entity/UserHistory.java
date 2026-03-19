package com.exe.skillverse_backend.shared.entity;

import com.exe.skillverse_backend.auth_service.entity.User;
import lombok.Data;
import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

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
