package com.exe.skillverse_backend.admin_service.service;

import com.exe.skillverse_backend.business_service.dto.response.JobPostingResponse;
import java.util.List;

public interface AdminJobService {
    List<JobPostingResponse> getPendingJobs();
    JobPostingResponse approveJob(Long jobId);
    JobPostingResponse rejectJob(Long jobId, String reason);
}
