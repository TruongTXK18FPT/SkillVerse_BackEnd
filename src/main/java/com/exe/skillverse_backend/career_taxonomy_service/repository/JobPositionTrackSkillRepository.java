package com.exe.skillverse_backend.career_taxonomy_service.repository;

import com.exe.skillverse_backend.career_taxonomy_service.entity.JobPositionTrackSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JobPositionTrackSkillRepository extends JpaRepository<JobPositionTrackSkill, Long> {
    List<JobPositionTrackSkill> findByTrackIdOrderBySortOrderAsc(Long trackId);
    Optional<JobPositionTrackSkill> findByTrackIdAndSkillId(Long trackId, Long skillId);
    void deleteByTrackIdAndSkillId(Long trackId, Long skillId);
    void deleteByTrackId(Long trackId);
    boolean existsBySkillId(Long skillId);

    /** Returns only mappings where the linked skill is ACTIVE — eliminates post-fetch filter. */
    @org.springframework.data.jpa.repository.Query("""
        select ts from JobPositionTrackSkill ts
        join Skill s on s.id = ts.skillId
        where ts.trackId = :trackId
          and s.status = :skillStatus
        order by ts.sortOrder asc
    """)
    List<JobPositionTrackSkill> findActiveSkillsByTrackId(
            @org.springframework.data.repository.query.Param("trackId") Long trackId,
            @org.springframework.data.repository.query.Param("skillStatus") com.exe.skillverse_backend.shared.enums.SkillStatus skillStatus);
}
