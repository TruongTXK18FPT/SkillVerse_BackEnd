package com.exe.skillverse_backend.student_learning_report_service.service;

import com.exe.skillverse_backend.student_learning_report_service.dto.request.GenerateStudentReportRequest;
import com.exe.skillverse_backend.student_learning_report_service.dto.response.LearningReportTimelineResponse;
import com.exe.skillverse_backend.student_learning_report_service.dto.response.StudentLearningReportResponse;
import java.util.List;

public interface StudentLearningReportService {

    StudentLearningReportResponse getSummary(Long studentId, String range);

    LearningReportTimelineResponse getTimeline(Long studentId, String range, Long snapshotId);

    StudentLearningReportResponse createSnapshot(Long studentId, String range);

    StudentLearningReportResponse generateLearningReport(Long studentId, GenerateStudentReportRequest request);

    StudentLearningReportResponse generateQuickReport(Long studentId);

    List<StudentLearningReportResponse> getReportHistory(Long studentId);

    List<StudentLearningReportResponse> getReportHistory(Long studentId, int page, int size);

    StudentLearningReportResponse getLatestReport(Long studentId);

    StudentLearningReportResponse getReportById(Long studentId, Long reportId);

    StudentLearningReportResponse.StudentMetrics getCurrentMetrics(Long studentId);

    boolean canGenerateNewReport(Long studentId);

    int getCooldownRemainingMinutes(Long studentId);

    long countReports(Long studentId);
}
