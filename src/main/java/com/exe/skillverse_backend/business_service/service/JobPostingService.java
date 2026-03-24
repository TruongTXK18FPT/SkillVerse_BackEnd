package com.exe.skillverse_backend.business_service.service;

import com.exe.skillverse_backend.business_service.dto.request.CreateJobRequest;
import com.exe.skillverse_backend.business_service.dto.request.ReopenJobRequest;
import com.exe.skillverse_backend.business_service.dto.request.UpdateJobRequest;
import com.exe.skillverse_backend.business_service.dto.response.JobPostingResponse;
import com.exe.skillverse_backend.business_service.entity.enums.JobStatus;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface JobPostingService {

    JobPostingResponse createJob(Long userId, CreateJobRequest request);

    JobPostingResponse updateJob(Long userId, Long jobId, UpdateJobRequest request);

    JobPostingResponse changeStatus(Long userId, Long jobId, JobStatus status);

    List<JobPostingResponse> getMyJobs(Long userId);

    List<JobPostingResponse> getPublicJobs();

    Page<JobPostingResponse> getPublicJobsPaged(Pageable pageable);

    JobPostingResponse getJobDetails(Long jobId);

    void deleteJob(Long userId, Long jobId);

    JobPostingResponse reopenJob(Long userId, Long jobId, ReopenJobRequest request);
}
