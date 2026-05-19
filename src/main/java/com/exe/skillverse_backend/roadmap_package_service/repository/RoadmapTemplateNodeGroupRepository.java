package com.exe.skillverse_backend.roadmap_package_service.repository;

import com.exe.skillverse_backend.roadmap_package_service.entity.RoadmapTemplateNodeGroup;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoadmapTemplateNodeGroupRepository extends JpaRepository<RoadmapTemplateNodeGroup, Long> {
    List<RoadmapTemplateNodeGroup> findByTemplateIdOrderByOrderIndexAscIdAsc(Long templateId);

    @Modifying
    @Query("DELETE FROM RoadmapTemplateNodeGroup g WHERE g.template.id = :templateId")
    void deleteByTemplateId(@Param("templateId") Long templateId);
}
