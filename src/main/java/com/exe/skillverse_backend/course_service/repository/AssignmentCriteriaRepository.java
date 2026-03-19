package com.exe.skillverse_backend.course_service.repository;

import com.exe.skillverse_backend.course_service.entity.AssignmentCriteria;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AssignmentCriteriaRepository extends JpaRepository<AssignmentCriteria, Long> {
    List<AssignmentCriteria> findByAssignmentIdOrderByOrderIndexAsc(Long assignmentId);
    void deleteByAssignmentId(Long assignmentId);
}
