package com.exe.skillverse_backend.course_service.entity;

import com.exe.skillverse_backend.shared.entity.Skill;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * N:N join table between Course and Skill.
 *
 * <p>Populated by CourseServiceImpl.syncCourseSkillLinks() whenever a course is created
 * or updated with skill tag names. The links are kept in sync with course_skill_tags
 * (ElementCollection) — both are updated together so BM25 index (plain String) and
 * taxonomy queries (Skill entity) both work.
 *
 * <p>Entity field vs embedded ID:
 * - Direct entity references (course, skill) are LAZY to avoid N+1 on collection loads.
 * - The embedded ID (CourseSkillId) holds the bare FK values for efficient queries.
 */
@Entity @Table(name = "course_skill")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class CourseSkill {
  @EmbeddedId
  private CourseSkillId id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false) @MapsId("courseId")
  @JoinColumn(name = "course_id", nullable = false)
  private Course course;

  @ManyToOne(fetch = FetchType.LAZY, optional = false) @MapsId("skillId")
  @JoinColumn(name = "skill_id", nullable = false)
  private Skill skill;
}
