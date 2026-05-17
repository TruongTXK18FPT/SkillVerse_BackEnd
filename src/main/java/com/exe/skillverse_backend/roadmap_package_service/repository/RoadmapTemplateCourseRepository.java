package com.exe.skillverse_backend.roadmap_package_service.repository;

import com.exe.skillverse_backend.roadmap_package_service.entity.RoadmapTemplateCourse;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoadmapTemplateCourseRepository extends JpaRepository<RoadmapTemplateCourse, Long> {
    List<RoadmapTemplateCourse> findByTemplateIdOrderByDisplayOrderAscIdAsc(Long templateId);
    List<RoadmapTemplateCourse> findByTemplateNodeIdOrderByDisplayOrderAscIdAsc(Long templateNodeId);
    void deleteByTemplateId(Long templateId);
}
