package com.exe.skillverse_backend.user_service.repository;

import com.exe.skillverse_backend.user_service.entity.UserSkill;
import com.exe.skillverse_backend.user_service.entity.UserSkillId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserSkillRepository extends JpaRepository<UserSkill, UserSkillId> {

    List<UserSkill> findByIdUserId(Long userId);

    List<UserSkill> findByIdSkillId(Long skillId);

    Optional<UserSkill> findByIdUserIdAndIdSkillId(Long userId, Long skillId);

    boolean existsByIdUserIdAndIdSkillId(Long userId, Long skillId);

    void deleteByIdUserIdAndIdSkillId(Long userId, Long skillId);

    void deleteByIdUserId(Long userId);

    @Query("SELECT us FROM UserSkill us WHERE us.id.userId = :userId AND us.proficiency >= :minProficiency")
    List<UserSkill> findByUserIdAndMinProficiency(@Param("userId") Long userId,
            @Param("minProficiency") Integer minProficiency);

    @Query("SELECT us FROM UserSkill us WHERE us.id.skillId = :skillId AND us.proficiency >= :minProficiency")
    List<UserSkill> findBySkillIdAndMinProficiency(@Param("skillId") Long skillId,
            @Param("minProficiency") Integer minProficiency);

    @Query("SELECT us FROM UserSkill us JOIN us.skill s WHERE us.id.userId = :userId AND s.category = :category")
    List<UserSkill> findByUserIdAndSkillCategory(@Param("userId") Long userId,
            @Param("category") String category);

    @Query("SELECT COUNT(us) FROM UserSkill us WHERE us.id.userId = :userId")
    Long countByUserId(@Param("userId") Long userId);
}