package com.exe.skillverse_backend.shared.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.List;
import com.exe.skillverse_backend.course_service.entity.CourseSkill;
@Entity
@Table(name = "skills")
@Data
public class Skill {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String category;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "parent_skill_id")
    private Long parentSkillId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_skill_id", insertable = false, updatable = false)
    private Skill parentSkill;

    @OneToMany(mappedBy = "parentSkill", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Skill> subSkills;
    @OneToMany(mappedBy = "skill", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude @EqualsAndHashCode.Exclude
    private List<CourseSkill> courseSkills;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}