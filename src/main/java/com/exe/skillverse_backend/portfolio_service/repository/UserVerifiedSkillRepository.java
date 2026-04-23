package com.exe.skillverse_backend.portfolio_service.repository;

import com.exe.skillverse_backend.portfolio_service.entity.UserVerifiedSkill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserVerifiedSkillRepository extends JpaRepository<UserVerifiedSkill, Long> {

    List<UserVerifiedSkill> findByUserIdOrderByVerifiedAtDesc(Long userId);

    Optional<UserVerifiedSkill> findByUserIdAndSkillName(Long userId, String skillName);

    boolean existsByUserIdAndSkillName(Long userId, String skillName);

    List<UserVerifiedSkill> findByVerifiedByMentorIdOrderByVerifiedAtDesc(Long mentorId);
}
