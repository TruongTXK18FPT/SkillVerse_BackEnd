package com.exe.skillverse_backend.career_taxonomy_service.repository;

import com.exe.skillverse_backend.career_taxonomy_service.entity.JobPosition;
import com.exe.skillverse_backend.career_taxonomy_service.enums.TaxonomyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JobPositionRepository extends JpaRepository<JobPosition, Long> {
    Optional<JobPosition> findByCode(String code);
    boolean existsByCode(String code);
    List<JobPosition> findByDomainIdAndStatus(Long domainId, TaxonomyStatus status);
    List<JobPosition> findByDomainId(Long domainId);

    /** Returns all ACTIVE job positions whose parent domain is also ACTIVE — single query, no N+1. */
    @Query("""
        select jp from JobPosition jp
        join Domain d on d.id = jp.domainId
        where jp.status = :status
          and d.status = :status
    """)
    List<JobPosition> findAllActiveWithActiveDomain(@org.springframework.data.repository.query.Param("status") TaxonomyStatus status);
}

