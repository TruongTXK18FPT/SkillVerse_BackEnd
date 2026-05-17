package com.exe.skillverse_backend.roadmap_package_service.repository;

import com.exe.skillverse_backend.roadmap_package_service.entity.RoadmapTemplateNode;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoadmapTemplateNodeRepository extends JpaRepository<RoadmapTemplateNode, Long> {
    List<RoadmapTemplateNode> findByTemplateIdOrderByOrderIndexAscIdAsc(Long templateId);
    void deleteByTemplateId(Long templateId);
}
