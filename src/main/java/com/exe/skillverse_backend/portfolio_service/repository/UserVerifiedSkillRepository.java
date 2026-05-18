package com.exe.skillverse_backend.portfolio_service.repository;

import com.exe.skillverse_backend.portfolio_service.entity.UserVerifiedSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserVerifiedSkillRepository extends JpaRepository<UserVerifiedSkill, Long> {

    List<UserVerifiedSkill> findByUserIdOrderByVerifiedAtDesc(Long userId);

    @Query("""
            SELECT s FROM UserVerifiedSkill s
            WHERE s.userId = :userId
            ORDER BY
                CASE WHEN s.featuredOrder IS NULL THEN 1 ELSE 0 END,
                s.featuredOrder ASC,
                s.verifiedAt DESC
            """)
    List<UserVerifiedSkill> findByUserIdOrderByFeaturedThenVerifiedAtDesc(@Param("userId") Long userId);

    Optional<UserVerifiedSkill> findByUserIdAndSkillName(Long userId, String skillName);

    boolean existsByUserIdAndSkillName(Long userId, String skillName);

    List<UserVerifiedSkill> findByVerifiedByMentorIdOrderByVerifiedAtDesc(Long mentorId);
}
