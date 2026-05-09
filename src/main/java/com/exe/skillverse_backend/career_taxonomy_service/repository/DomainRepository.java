package com.exe.skillverse_backend.career_taxonomy_service.repository;

import com.exe.skillverse_backend.career_taxonomy_service.entity.Domain;
import com.exe.skillverse_backend.career_taxonomy_service.enums.TaxonomyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DomainRepository extends JpaRepository<Domain, Long> {
    Optional<Domain> findByCode(String code);
    boolean existsByCode(String code);
    List<Domain> findByStatus(TaxonomyStatus status);
}
