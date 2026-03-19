package com.exe.skillverse_backend.shared.entity;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "audit_logs")
@Data
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password", "verificationOtp", "otpExpiryTime", "otpAttempts"})
    private User user;

    @Column(nullable = false)
    private String action; // CREATE, UPDATE, DELETE, LOGIN, LOGOUT, etc.

    @Column(name = "object_type", nullable = false)
    private String objectType; // USER, COURSE, MENTOR, etc.

    @Column(name = "object_id")
    private Long objectId;

    @Column(columnDefinition = "TEXT")
    private String details; // JSON or text describing the changes

    @Column(nullable = false)
    private LocalDateTime timestamp = LocalDateTime.now();
}
