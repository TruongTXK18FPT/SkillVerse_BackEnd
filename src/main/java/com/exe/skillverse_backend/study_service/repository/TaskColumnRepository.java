package com.exe.skillverse_backend.study_service.repository;

import com.exe.skillverse_backend.study_service.entity.TaskColumn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TaskColumnRepository extends JpaRepository<TaskColumn, UUID> {
    List<TaskColumn> findByUserIdOrderByOrderIndexAsc(Long userId);
    java.util.Optional<TaskColumn> findByNameAndUserId(String name, Long userId);
}
