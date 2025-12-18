package com.exe.skillverse_backend.study_service.service;

import com.exe.skillverse_backend.study_service.dto.request.CreateStudySessionRequest;
import com.exe.skillverse_backend.study_service.dto.response.StudySessionResponse;
import com.exe.skillverse_backend.study_service.entity.StudySessionStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface StudyPlannerService {
    StudySessionResponse createSession(Long userId, CreateStudySessionRequest request);
    List<StudySessionResponse> createSessions(Long userId, List<CreateStudySessionRequest> requests);
    List<StudySessionResponse> getSessions(Long userId);
    List<StudySessionResponse> getSessionsInRange(Long userId, LocalDateTime start, LocalDateTime end);
    StudySessionResponse updateStatus(UUID sessionId, StudySessionStatus status);
    void deleteSession(UUID sessionId);
}
