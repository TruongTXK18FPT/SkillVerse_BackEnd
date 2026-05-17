package com.exe.skillverse_backend.journey_service.node_mentoring.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Snapshot of the current assignment attached to a roadmap node within a journey.
 * Either system-generated from the roadmap or refined by the assigned mentor.
 */
@Entity
@Table(name = "roadmap_node_assignments", indexes = {
        @Index(columnList = "journey_id, node_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoadmapNodeAssignment {

    public enum AssignmentSource {
        SYSTEM_GENERATED,
        MENTOR_REFINED,
        TEMPLATE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "journey_id", nullable = false)
    private Long journeyId;

    @Column(name = "roadmap_session_id")
    private Long roadmapSessionId;

    @Column(name = "node_id", nullable = false, length = 100)
    private String nodeId;

    @Column(name = "node_skill_id")
    private Long nodeSkillId;

    @Column(name = "roadmap_template_node_id")
    private Long roadmapTemplateNodeId;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_source", nullable = false, length = 30)
    private AssignmentSource assignmentSource = AssignmentSource.SYSTEM_GENERATED;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "expected_output", columnDefinition = "TEXT")
    private String expectedOutput;

    @Column(name = "rubric", columnDefinition = "TEXT")
    private String rubric;

    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
