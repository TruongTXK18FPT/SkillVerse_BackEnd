package com.exe.skillverse_backend.portfolio_service.service;

import com.exe.skillverse_backend.portfolio_service.dto.CandidateSummaryDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface RecruiterCandidateService {
    Page<CandidateSummaryDTO> getOpenCandidates(Pageable pageable);
}
