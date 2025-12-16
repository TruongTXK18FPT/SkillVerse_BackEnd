package com.exe.skillverse_backend.business_service.service;

import com.exe.skillverse_backend.business_service.dto.request.ApplyJobRequest;
import com.exe.skillverse_backend.business_service.dto.request.UpdateApplicationStatusRequest;
import com.exe.skillverse_backend.business_service.dto.response.JobApplicationResponse;

import java.util.List;

public interface JobApplicationService {

    JobApplicationResponse applyToJob(Long userId, Long jobId, ApplyJobRequest request);

    List<JobApplicationResponse> getMyApplications(Long userId);

    List<JobApplicationResponse> getJobApplicants(Long userId, Long jobId);

    JobApplicationResponse updateApplicationStatus(Long userId, Long applicationId,
            UpdateApplicationStatusRequest request);

    JobApplicationResponse getApplicationById(Long userId, Long applicationId);
}
