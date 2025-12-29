package com.exe.skillverse_backend.portfolio_service.controller;

import com.exe.skillverse_backend.portfolio_service.dto.CandidateSummaryDTO;
import com.exe.skillverse_backend.portfolio_service.service.RecruiterCandidateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/portfolio/recruiter")
@RequiredArgsConstructor
@Slf4j
public class RecruiterCandidateController {

    private final RecruiterCandidateService recruiterCandidateService;

    @GetMapping("/candidates")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<?> getCandidates(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "8") int size) {
        log.info("Recruiter requesting candidate list (page: {}, size: {})", page, size);
        Pageable pageable = PageRequest.of(page, size);
        Page<CandidateSummaryDTO> candidates = recruiterCandidateService.getOpenCandidates(pageable);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Candidates fetched successfully",
                "data", candidates));
    }
}
