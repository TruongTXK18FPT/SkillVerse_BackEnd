package com.exe.skillverse_backend.study_service.repository;

import com.exe.skillverse_backend.study_service.entity.TaskColumn;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TaskColumnRepository extends JpaRepository<TaskColumn, UUID> {
    List<TaskColumn> findByUserIdOrderByOrderIndexAsc(Long userId);
    Optional<TaskColumn> findByNameAndUserId(String name, Long userId);
}
