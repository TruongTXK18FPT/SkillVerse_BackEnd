package com.exe.skillverse_backend.roadmap_package_service.repository;

import com.exe.skillverse_backend.roadmap_package_service.entity.RoadmapTemplateNodeGroupSkill;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoadmapTemplateNodeGroupSkillRepository extends JpaRepository<RoadmapTemplateNodeGroupSkill, Long> {
    List<RoadmapTemplateNodeGroupSkill> findByNodeGroupIdOrderByOrderIndexAscIdAsc(Long nodeGroupId);

    @Modifying
    @Query("DELETE FROM RoadmapTemplateNodeGroupSkill s WHERE s.nodeGroup.template.id = :templateId")
    void deleteByTemplateId(@Param("templateId") Long templateId);
}

