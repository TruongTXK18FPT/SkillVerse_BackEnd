package com.exe.skillverse_backend.course_service.entity;

import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;

import com.exe.skillverse_backend.shared.entity.Skill;

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
