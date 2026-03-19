package com.exe.skillverse_backend.business_service.repository;

import com.exe.skillverse_backend.business_service.entity.ShortTermJobMilestone;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ShortTermJobMilestoneRepository extends JpaRepository<ShortTermJobMilestone, Long> {

    List<ShortTermJobMilestone> findByShortTermJobIdOrderByOrderIndexAsc(Long jobId);

    long countByShortTermJobId(Long jobId);

    long countByShortTermJobIdAndStatus(Long jobId, ShortTermJobMilestone.MilestoneStatus status);

    void deleteByShortTermJobId(Long jobId);
}
