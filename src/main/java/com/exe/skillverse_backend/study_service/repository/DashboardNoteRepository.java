package com.exe.skillverse_backend.study_service.repository;

import com.exe.skillverse_backend.study_service.entity.DashboardNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DashboardNoteRepository extends JpaRepository<DashboardNote, UUID> {
    List<DashboardNote> findByUser_Id(Long userId);
}
