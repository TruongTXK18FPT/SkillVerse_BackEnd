package com.exe.skillverse_backend.study_service.repository;

import com.exe.skillverse_backend.study_service.entity.DashboardNote;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DashboardNoteRepository extends JpaRepository<DashboardNote, UUID> {
    List<DashboardNote> findByUser_Id(Long userId);
}
