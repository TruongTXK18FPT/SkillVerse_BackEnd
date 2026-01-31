package com.exe.skillverse_backend.course_service.repository;

import com.exe.skillverse_backend.course_service.entity.AssignmentCriteria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AssignmentCriteriaRepository extends JpaRepository<AssignmentCriteria, Long> {
    List<AssignmentCriteria> findByAssignmentIdOrderByOrderIndexAsc(Long assignmentId);
    void deleteByAssignmentId(Long assignmentId);
}
