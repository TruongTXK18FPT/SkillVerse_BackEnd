package com.exe.skillverse_backend.parent_service.service;

import com.exe.skillverse_backend.ai_service.dto.ChatMessageResponse;
import com.exe.skillverse_backend.ai_service.dto.ChatSessionSummary;
import com.exe.skillverse_backend.ai_service.dto.response.RoadmapSessionSummary;
import com.exe.skillverse_backend.parent_service.dto.request.LinkStudentRequest;
import com.exe.skillverse_backend.parent_service.dto.request.UpdateLinkStatusRequest;
import com.exe.skillverse_backend.parent_service.dto.response.LearningReportResponse;
import com.exe.skillverse_backend.parent_service.dto.response.ParentDashboardResponse;
import com.exe.skillverse_backend.parent_service.dto.response.ParentStudentLinkResponse;
import java.util.List;

public interface ParentService {
    ParentStudentLinkResponse sendLinkRequest(Long parentId, LinkStudentRequest request);
    ParentStudentLinkResponse updateLinkStatus(Long userId, Long linkId, UpdateLinkStatusRequest request);
    ParentDashboardResponse getParentDashboard(Long parentId);
    List<ParentStudentLinkResponse> getStudentLinks(Long studentId);
    List<ParentStudentLinkResponse> getSentLinkRequests(Long parentId);
    void unlink(Long userId, Long linkId);
    List<RoadmapSessionSummary> getStudentRoadmaps(Long parentId, Long studentId);
    List<ChatSessionSummary> getStudentChatSessions(Long parentId, Long studentId);
    List<ChatMessageResponse> getStudentChatSessionDetails(Long parentId, Long studentId, Long sessionId);
    LearningReportResponse generateLearningReport(Long parentId, Long studentId);
    List<LearningReportResponse> getLearningReportHistory(Long parentId, Long studentId);
    LearningReportResponse getLatestLearningReport(Long parentId, Long studentId);
}
