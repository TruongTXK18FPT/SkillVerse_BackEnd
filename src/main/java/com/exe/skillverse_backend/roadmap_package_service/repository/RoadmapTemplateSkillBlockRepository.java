package com.exe.skillverse_backend.roadmap_package_service.repository;

import com.exe.skillverse_backend.roadmap_package_service.entity.RoadmapTemplateSkillBlock;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoadmapTemplateSkillBlockRepository extends JpaRepository<RoadmapTemplateSkillBlock, Long> {
    List<RoadmapTemplateSkillBlock> findByTemplateIdOrderByIdAsc(Long templateId);

    @Modifying
    @Query("DELETE FROM RoadmapTemplateSkillBlock b WHERE b.template.id = :templateId")
    void deleteByTemplateId(@Param("templateId") Long templateId);
}
