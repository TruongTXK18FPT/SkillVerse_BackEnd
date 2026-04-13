package com.exe.skillverse_backend.admin_service.service;

import com.exe.skillverse_backend.admin_service.dto.response.AdminFullTimeJobStatsResponse;
import com.exe.skillverse_backend.business_service.dto.response.JobPostingResponse;
import com.exe.skillverse_backend.business_service.entity.enums.JobStatus;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminJobService {
    List<JobPostingResponse> getPendingJobs();
    Page<JobPostingResponse> getAllJobs(JobStatus status, Pageable pageable);
    AdminFullTimeJobStatsResponse getJobStats();
    JobPostingResponse getJobDetail(Long jobId);
    JobPostingResponse closeJob(Long adminId, Long jobId, String reason);
    JobPostingResponse approveJob(Long jobId);
    JobPostingResponse rejectJob(Long jobId, String reason);
}
