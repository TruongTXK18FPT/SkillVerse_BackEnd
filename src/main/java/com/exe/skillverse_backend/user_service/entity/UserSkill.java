package com.exe.skillverse_backend.user_service.entity;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.shared.entity.Skill;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_skills")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserSkill {

    @EmbeddedId
    private UserSkillId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("skillId")
    @JoinColumn(name = "skill_id")
    private Skill skill;

    @Column(nullable = false)
    private Integer proficiency; // 1-5 scale (1=Beginner, 2=Novice, 3=Intermediate, 4=Advanced, 5=Expert)

    public UserSkill(User user, Skill skill, Integer proficiency) {
        this.user = user;
        this.skill = skill;
        this.proficiency = proficiency;
        this.id = new UserSkillId(user.getId(), skill.getId());
    }

    public UserSkill(Long userId, Long skillId, Integer proficiency) {
        this.proficiency = proficiency;
        this.id = new UserSkillId(userId, skillId);
    }
}