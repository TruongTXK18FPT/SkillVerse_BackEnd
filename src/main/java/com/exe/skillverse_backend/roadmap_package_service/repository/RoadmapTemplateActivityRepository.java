package com.exe.skillverse_backend.roadmap_package_service.repository;

import com.exe.skillverse_backend.roadmap_package_service.entity.RoadmapTemplateActivity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoadmapTemplateActivityRepository extends JpaRepository<RoadmapTemplateActivity, Long> {
    List<RoadmapTemplateActivity> findByTemplateIdOrderBySkillBlockIdAscOrderIndexAscIdAsc(Long templateId);

    List<RoadmapTemplateActivity> findBySkillBlockIdOrderByOrderIndexAscIdAsc(Long skillBlockId);

    @Modifying
    @Query("DELETE FROM RoadmapTemplateActivity a WHERE a.template.id = :templateId")
    void deleteByTemplateId(@Param("templateId") Long templateId);
}
