package com.exe.skillverse_backend.course_service.entity;

import com.exe.skillverse_backend.shared.entity.Skill;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
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

import java.io.Serializable;

@Embeddable
@Data @NoArgsConstructor @AllArgsConstructor @Builder
class CourseSkillId implements Serializable {
  @Column(name = "course_id") private Long courseId;
  @Column(name = "skill_id")  private Long skillId;
}

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
