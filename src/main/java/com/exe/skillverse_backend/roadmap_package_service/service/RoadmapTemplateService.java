package com.exe.skillverse_backend.roadmap_package_service.service;

import com.exe.skillverse_backend.roadmap_package_service.dto.request.RoadmapTemplateRequest;
import com.exe.skillverse_backend.roadmap_package_service.dto.response.RoadmapTemplateAllocationPreviewResponse;
import com.exe.skillverse_backend.roadmap_package_service.dto.response.RoadmapTemplateCourseCandidateResponse;
import com.exe.skillverse_backend.roadmap_package_service.dto.response.RoadmapTemplateResponse;
import com.exe.skillverse_backend.roadmap_package_service.dto.response.RoadmapTemplateValidationResponse;
import com.exe.skillverse_backend.roadmap_package_service.entity.RoadmapTemplateCourseLinkPolicy;
import com.exe.skillverse_backend.roadmap_package_service.entity.RoadmapTemplateStatus;
import com.exe.skillverse_backend.journey_service.entity.Journey;
import com.exe.skillverse_backend.journey_service.entity.TestResult;
import java.util.List;
import java.util.Map;

public interface RoadmapTemplateService {
    RoadmapTemplateResponse createTemplate(Long actorId, RoadmapTemplateRequest request);
    RoadmapTemplateResponse updateTemplate(Long actorId, Long templateId, RoadmapTemplateRequest request);
    List<RoadmapTemplateResponse> listAdminTemplates(Long actorId, Long domainId, Long jobPositionId,
                                                     Long jobPositionTrackId, RoadmapTemplateStatus status);
    List<RoadmapTemplateResponse> getMyTemplates(Long actorId);
    RoadmapTemplateResponse getTemplate(Long actorId, Long templateId);
    RoadmapTemplateResponse submitTemplate(Long actorId, Long templateId);
    List<RoadmapTemplateResponse> getSubmittedTemplates();
    RoadmapTemplateResponse approveTemplate(Long adminId, Long templateId);
    RoadmapTemplateResponse rejectTemplate(Long adminId, Long templateId);
    RoadmapTemplateResponse publishTemplate(Long adminId, Long templateId);
    RoadmapTemplateResponse archiveTemplate(Long adminId, Long templateId);
    RoadmapTemplateAllocationPreviewResponse previewAllocation(Long actorId, RoadmapTemplateRequest request);
    RoadmapTemplateValidationResponse validateTemplate(Long actorId, RoadmapTemplateRequest request);
    List<RoadmapTemplateCourseCandidateResponse> getCourseCandidates(
            Long actorId, Long skillId, RoadmapTemplateCourseLinkPolicy policy, Integer limit);
    Long createRoadmapSessionFromPublishedTemplate(Journey journey, TestResult testResult,
                                                   List<Map<String, Object>> skillGaps,
                                                   List<Map<String, Object>> strengths);
    void deleteTemplate(Long adminId, Long templateId);
}
