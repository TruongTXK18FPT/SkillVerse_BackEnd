package com.exe.skillverse_backend.business_service.repository;

import com.exe.skillverse_backend.business_service.entity.DisputeResponseEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DisputeResponseRepository extends JpaRepository<DisputeResponseEntity, Long> {
    List<DisputeResponseEntity> findByEvidenceIdOrderByCreatedAtDesc(Long evidenceId);
}
