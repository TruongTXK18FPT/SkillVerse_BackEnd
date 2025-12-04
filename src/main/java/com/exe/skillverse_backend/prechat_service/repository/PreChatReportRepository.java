package com.exe.skillverse_backend.prechat_service.repository;

import com.exe.skillverse_backend.prechat_service.entity.PreChatReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PreChatReportRepository extends JpaRepository<PreChatReport, Long> {
    Page<PreChatReport> findByStatus(PreChatReport.Status status, Pageable pageable);
}

