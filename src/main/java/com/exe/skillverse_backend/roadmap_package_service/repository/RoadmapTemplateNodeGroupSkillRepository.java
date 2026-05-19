package com.exe.skillverse_backend.roadmap_package_service.repository;

import com.exe.skillverse_backend.roadmap_package_service.entity.RoadmapTemplateNodeGroupSkill;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoadmapTemplateNodeGroupSkillRepository extends JpaRepository<RoadmapTemplateNodeGroupSkill, Long> {
    List<RoadmapTemplateNodeGroupSkill> findByNodeGroupIdOrderByOrderIndexAscIdAsc(Long nodeGroupId);
}
