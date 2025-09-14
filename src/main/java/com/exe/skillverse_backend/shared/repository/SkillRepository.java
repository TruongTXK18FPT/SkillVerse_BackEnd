package com.exe.skillverse_backend.shared.repository;

import com.exe.skillverse_backend.shared.entity.Skill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SkillRepository extends JpaRepository<Skill, Long> {

    Optional<Skill> findByName(String name);

    List<Skill> findByCategory(String category);

    List<Skill> findByParentSkillId(Long parentSkillId);

    List<Skill> findByParentSkillIdIsNull(); // Root skills

    @Query("SELECT s FROM Skill s WHERE s.name LIKE %:name%")
    List<Skill> findByNameContaining(@Param("name") String name);

    @Query("SELECT s FROM Skill s WHERE s.description LIKE %:keyword%")
    List<Skill> findByDescriptionContaining(@Param("keyword") String keyword);

    boolean existsByName(String name);
}