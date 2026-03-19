package com.exe.skillverse_backend.study_service.repository;

import com.exe.skillverse_backend.study_service.entity.Task;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {
    List<Task> findByUserId(Long userId);
    List<Task> findByColumnId(UUID columnId);
    List<Task> findByLinkedSessions_Id(UUID sessionId);
}
