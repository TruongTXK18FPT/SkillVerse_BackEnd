package com.exe.skillverse_backend.business_service.repository;

import com.exe.skillverse_backend.business_service.entity.DisputeEvidence;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DisputeEvidenceRepository extends JpaRepository<DisputeEvidence, Long> {
    List<DisputeEvidence> findByDisputeIdOrderByCreatedAtDesc(Long disputeId);
}
