package com.exe.skillverse_backend.roadmap_package_service.entity;

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
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "roadmap_template_courses", indexes = {
        @Index(columnList = "template_id"),
        @Index(columnList = "template_node_id"),
        @Index(columnList = "course_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoadmapTemplateCourse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private RoadmapTemplate template;

    @Column(name = "template_id", insertable = false, updatable = false)
    private Long templateId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_node_id")
    private RoadmapTemplateNode templateNode;

    @Column(name = "template_node_id", insertable = false, updatable = false)
    private Long templateNodeId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "skill_id")
    private Long skillId;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Builder.Default
    @Column(name = "required")
    private Boolean required = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
