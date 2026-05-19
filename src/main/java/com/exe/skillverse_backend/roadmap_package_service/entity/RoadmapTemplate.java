package com.exe.skillverse_backend.roadmap_package_service.entity;


import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "roadmap_templates", indexes = {
        @Index(columnList = "status"),
        @Index(columnList = "job_position_track_id"),
        @Index(columnList = "created_by_admin_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoadmapTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;



    @Column(name = "created_by_admin_id")
    private Long createdByAdminId;

    @Column(name = "updated_by_admin_id")
    private Long updatedByAdminId;

    @Column(name = "domain_id", nullable = false)
    private Long domainId;

    @Column(name = "job_position_id", nullable = false)
    private Long jobPositionId;

    @Column(name = "job_position_track_id", nullable = false)
    private Long jobPositionTrackId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "target_role", length = 255)
    private String targetRole;

    @Column(name = "target_level", length = 50)
    private String targetLevel;

    @Column(name = "target_role_snapshot", length = 255)
    private String targetRoleSnapshot;

    @Column(name = "target_level_snapshot", length = 50)
    private String targetLevelSnapshot;

    @Column(name = "total_node_count")
    private Integer totalNodeCount;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "generation_mode", nullable = false, length = 30)
    private RoadmapTemplateGenerationMode generationMode = RoadmapTemplateGenerationMode.LEGACY_STATIC;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "knowledge_policy", nullable = false, length = 40)
    private RoadmapTemplateKnowledgePolicy knowledgePolicy = RoadmapTemplateKnowledgePolicy.TEMPLATE_ONLY;

    @Column(name = "global_learning_goal", columnDefinition = "TEXT")
    private String globalLearningGoal;

    @Column(name = "audience_level", length = 80)
    private String audienceLevel;

    @Column(name = "output_standard", columnDefinition = "TEXT")
    private String outputStandard;

    @Column(name = "assessment_policy", columnDefinition = "TEXT")
    private String assessmentPolicy;

    @Column(name = "template_instructions", columnDefinition = "TEXT")
    private String templateInstructions;

    @Column(name = "constraints_json", columnDefinition = "TEXT")
    private String constraintsJson;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RoadmapTemplateStatus status = RoadmapTemplateStatus.DRAFT;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Builder.Default
    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<RoadmapTemplateNode> nodes = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<RoadmapTemplateCourse> courses = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<RoadmapTemplateSkillBlock> skillBlocks = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<RoadmapTemplateNodeGroup> nodeGroups = new ArrayList<>();
}
