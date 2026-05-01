package com.exe.skillverse_backend.ai_usage_service.entity;

import com.exe.skillverse_backend.ai_usage_service.entity.enums.AiFlowType;
import com.exe.skillverse_backend.ai_usage_service.entity.enums.AiProviderType;
import com.exe.skillverse_backend.ai_usage_service.entity.enums.AiUsageStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_token_usage_logs",
    indexes = {
        @Index(name = "idx_created_at", columnList = "createdAt"),
        @Index(name = "idx_flow_type_created", columnList = "flowType, createdAt"),
        @Index(name = "idx_provider_type_created", columnList = "providerType, createdAt"),
        @Index(name = "idx_user_id_created", columnList = "userId, createdAt"),
        @Index(name = "idx_status_created", columnList = "status, createdAt")
    })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiTokenUsageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AiFlowType flowType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiProviderType providerType;

    @Column(length = 50)
    private String modelName;

    private Long userId;

    @Column(length = 30)
    private String relatedEntityType;

    private Long relatedEntityId;

    private Long promptTokens;

    private Long completionTokens;

    private Long totalTokens;

    @Column(nullable = false)
    private boolean estimated;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AiUsageStatus status;

    private Long latencyMs;

    @Column(length = 50)
    private String errorCode;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
