package com.exe.skillverse_backend.business_service.repository;

import com.exe.skillverse_backend.business_service.entity.JobDeliverable;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JobDeliverableRepository extends JpaRepository<JobDeliverable, Long> {

    List<JobDeliverable> findByApplicationId(Long applicationId);

    List<JobDeliverable> findByMilestoneId(Long milestoneId);

    List<JobDeliverable> findByApplicationIdAndMilestoneId(Long applicationId, Long milestoneId);

    long countByApplicationId(Long applicationId);

    void deleteByApplicationId(Long applicationId);
}
