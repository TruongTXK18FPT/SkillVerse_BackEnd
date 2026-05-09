package com.exe.skillverse_backend.career_taxonomy_service.entity;

import com.exe.skillverse_backend.career_taxonomy_service.enums.ImportanceLevel;
import com.exe.skillverse_backend.career_taxonomy_service.enums.RequirementType;
import com.exe.skillverse_backend.shared.entity.Skill;
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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "job_position_track_skills", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"track_id", "skill_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobPositionTrackSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "track_id", nullable = false)
    private JobPositionTrack track;

    @Column(name = "track_id", insertable = false, updatable = false)
    private Long trackId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @Column(name = "skill_id", insertable = false, updatable = false)
    private Long skillId;

    @Enumerated(EnumType.STRING)
    @Column(name = "requirement_type", nullable = false)
    @Builder.Default
    private RequirementType requirementType = RequirementType.REQUIRED;

    @Enumerated(EnumType.STRING)
    @Column(name = "importance_level", nullable = false)
    @Builder.Default
    private ImportanceLevel importanceLevel = ImportanceLevel.MEDIUM;

    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 0;
}
