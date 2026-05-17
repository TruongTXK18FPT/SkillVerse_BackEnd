package com.exe.skillverse_backend.roadmap_package_service.repository;

import com.exe.skillverse_backend.roadmap_package_service.entity.RoadmapTemplate;
import com.exe.skillverse_backend.roadmap_package_service.entity.RoadmapTemplateStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoadmapTemplateRepository extends JpaRepository<RoadmapTemplate, Long> {
    List<RoadmapTemplate> findByStatusOrderByCreatedAtDesc(RoadmapTemplateStatus status);

    @Query("""
            select t from RoadmapTemplate t
            where (:domainId is null or t.domainId = :domainId)
              and (:jobPositionId is null or t.jobPositionId = :jobPositionId)
              and (:jobPositionTrackId is null or t.jobPositionTrackId = :jobPositionTrackId)
              and (:status is null or t.status = :status)
            order by t.updatedAt desc, t.createdAt desc
            """)
    List<RoadmapTemplate> searchAdminTemplates(
            @Param("domainId") Long domainId,
            @Param("jobPositionId") Long jobPositionId,
            @Param("jobPositionTrackId") Long jobPositionTrackId,
            @Param("status") RoadmapTemplateStatus status);

    Optional<RoadmapTemplate> findFirstByJobPositionTrackIdAndStatusOrderByUpdatedAtDescCreatedAtDesc(
            Long jobPositionTrackId,
            RoadmapTemplateStatus status);
}
