package com.exe.skillverse_backend.study_service.repository;

import com.exe.skillverse_backend.study_service.entity.Task;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {
    List<Task> findByUserId(Long userId);
    List<Task> findByColumnId(UUID columnId);
    List<Task> findByColumnIdOrderByOrderIndexAsc(UUID columnId);
    List<Task> findByLinkedSessions_Id(UUID sessionId);

    /**
     * Find tasks whose userNotes contain a specific keyword.
     * GAP-9: replaces loadTasksByNodeId() which loaded ALL user tasks then filtered in-memory.
     * Uses LIKE for efficient DB-level filtering.
     */
    @Query("SELECT t FROM Task t WHERE t.user.id = :userId AND t.userNotes LIKE %:keyword%")
    List<Task> findByUserIdAndUserNotesContaining(@Param("userId") Long userId, @Param("keyword") String keyword);

    /**
     * Bulk-archive all tasks matching a roadmap session.
     * Soft-delete: sets archived=true, preserving task data for audit/debug.
     * Requires the archived column to exist (run migration.sql first).
     * If the column doesn't exist, this throws InvalidDataAccessResourceUsageException.
     */
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true)
    @org.springframework.transaction.annotation.Transactional
    @Query("UPDATE Task t SET t.archived = true WHERE t.user.id = :userId AND t.userNotes IS NOT NULL AND t.userNotes LIKE %:sessionId% AND (t.archived IS NULL OR t.archived = false)")
    int archiveByUserNotesContaining(@Param("userId") Long userId, @Param("sessionId") String sessionId);

    /**
     * Bulk-unarchive all archived tasks matching a roadmap session.
     * Called when a journey is resumed so tasks reappear on the board.
     */
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true)
    @org.springframework.transaction.annotation.Transactional
    @Query("UPDATE Task t SET t.archived = false WHERE t.user.id = :userId AND t.userNotes IS NOT NULL AND t.userNotes LIKE %:sessionId% AND t.archived = true")
    int unarchiveByUserNotesContaining(@Param("userId") Long userId, @Param("sessionId") String sessionId);
}
