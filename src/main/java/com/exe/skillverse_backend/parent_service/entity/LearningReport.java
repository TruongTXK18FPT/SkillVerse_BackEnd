package com.exe.skillverse_backend.parent_service.entity;

import com.exe.skillverse_backend.auth_service.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "learning_reports")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LearningReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", nullable = false)
    private User parent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(nullable = false)
    private String studentName;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String reportContent;

    // Parsed sections stored as JSON or separate columns
    @Lob
    @Column(columnDefinition = "TEXT")
    private String goalsSection;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String resultsSection;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String behaviorSection;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String strengthsSection;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String concernsSection;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String recommendationsSection;

    @CreationTimestamp
    @Column(name = "generated_at", nullable = false, updatable = false)
    private LocalDateTime generatedAt;

    @Column(name = "is_ai_generated")
    private Boolean isAiGenerated = true;
}
