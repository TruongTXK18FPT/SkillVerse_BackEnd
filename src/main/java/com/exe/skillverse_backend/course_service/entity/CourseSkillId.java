package com.exe.skillverse_backend.course_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Composite primary key for {@link CourseSkill}.
 *
 * <p>Holds the bare FK values ({@code courseId}, {@code skillId}) for efficient JPQL
 * queries that bypass lazy-loaded entity paths. Used directly in repository queries
 * (e.g., {@code cs.id.courseId}) instead of entity navigation ({@code cs.course.id}).
 */
@Embeddable
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class CourseSkillId implements Serializable {
    @Column(name = "course_id") private Long courseId;
    @Column(name = "skill_id")  private Long skillId;
}