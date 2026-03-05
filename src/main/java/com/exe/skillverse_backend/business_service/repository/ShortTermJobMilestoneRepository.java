package com.exe.skillverse_backend.business_service.repository;

import com.exe.skillverse_backend.business_service.entity.ShortTermJobMilestone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShortTermJobMilestoneRepository extends JpaRepository<ShortTermJobMilestone, Long> {

    List<ShortTermJobMilestone> findByShortTermJobIdOrderByOrderIndexAsc(Long jobId);

    long countByShortTermJobId(Long jobId);

    long countByShortTermJobIdAndStatus(Long jobId, ShortTermJobMilestone.MilestoneStatus status);

    void deleteByShortTermJobId(Long jobId);
}
