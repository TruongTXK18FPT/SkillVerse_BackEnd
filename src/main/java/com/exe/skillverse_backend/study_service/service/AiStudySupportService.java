package com.exe.skillverse_backend.study_service.service;

import com.exe.skillverse_backend.study_service.dto.request.GenerateScheduleRequest;
import com.exe.skillverse_backend.study_service.dto.request.RefineScheduleRequest;
import com.exe.skillverse_backend.study_service.dto.response.StudySessionResponse;
import com.exe.skillverse_backend.study_service.dto.request.CheckScheduleHealthRequest;
import com.exe.skillverse_backend.study_service.dto.response.ScheduleHealthReport;

import java.util.List;

public interface AiStudySupportService {
    List<StudySessionResponse> generateSchedule(Long userId, GenerateScheduleRequest request);
    List<StudySessionResponse> generateProposedSchedule(Long userId, GenerateScheduleRequest request);
    List<StudySessionResponse> refineSchedule(Long userId, RefineScheduleRequest request);
    ScheduleHealthReport checkScheduleHealth(CheckScheduleHealthRequest request);
    ScheduleHealthReport suggestHealthyAdjustments(CheckScheduleHealthRequest request);
}
