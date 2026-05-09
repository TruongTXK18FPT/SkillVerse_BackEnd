package com.exe.skillverse_backend.career_taxonomy_service.repository;

import com.exe.skillverse_backend.career_taxonomy_service.entity.JobPositionTrack;
import com.exe.skillverse_backend.career_taxonomy_service.enums.TaxonomyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JobPositionTrackRepository extends JpaRepository<JobPositionTrack, Long> {
    Optional<JobPositionTrack> findByCode(String code);
    boolean existsByCode(String code);
    List<JobPositionTrack> findByJobPositionIdAndStatus(Long jobPositionId, TaxonomyStatus status);
    List<JobPositionTrack> findByJobPositionId(Long jobPositionId);

    /** Returns all ACTIVE tracks whose parent job position AND domain are also ACTIVE — single query, no N+1. */
    @Query("""
        select t from JobPositionTrack t
        join JobPosition jp on jp.id = t.jobPositionId
        join Domain d on d.id = jp.domainId
        where t.status = :status
          and jp.status = :status
          and d.status = :status
    """)
    List<JobPositionTrack> findAllActiveWithActiveParentChain(@org.springframework.data.repository.query.Param("status") TaxonomyStatus status);
}

