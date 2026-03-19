package com.exe.skillverse_backend.student_learning_report_service.service;

import com.exe.skillverse_backend.student_learning_report_service.dto.request.GenerateStudentReportRequest;
import com.exe.skillverse_backend.student_learning_report_service.dto.response.StudentLearningReportResponse;
import java.util.List;

/**
 * Service interface cho Student Learning Report.
 * Định nghĩa các operations chính cho báo cáo học tập cá nhân của học viên.
 */
public interface StudentLearningReportService {

    /**
     * Tạo báo cáo học tập cá nhân cho học viên.
     * 
     * @param studentId ID của học viên
     * @param request Request chứa cấu hình báo cáo
     * @return Báo cáo học tập đã được tạo
     */
    StudentLearningReportResponse generateLearningReport(Long studentId, GenerateStudentReportRequest request);

    /**
     * Tạo báo cáo nhanh với cấu hình mặc định.
     * 
     * @param studentId ID của học viên
     * @return Báo cáo học tập comprehensive
     */
    StudentLearningReportResponse generateQuickReport(Long studentId);

    /**
     * Lấy lịch sử báo cáo của học viên.
     * 
     * @param studentId ID của học viên
     * @return Danh sách báo cáo theo thứ tự mới nhất
     */
    List<StudentLearningReportResponse> getReportHistory(Long studentId);

    /**
     * Lấy lịch sử báo cáo (phân trang).
     * 
     * @param studentId ID của học viên
     * @param page Số trang
     * @param size Số lượng mỗi trang
     * @return Danh sách báo cáo phân trang
     */
    List<StudentLearningReportResponse> getReportHistory(Long studentId, int page, int size);

    /**
     * Lấy báo cáo mới nhất của học viên.
     * 
     * @param studentId ID của học viên
     * @return Báo cáo mới nhất hoặc null nếu chưa có
     */
    StudentLearningReportResponse getLatestReport(Long studentId);

    /**
     * Lấy báo cáo theo ID.
     * 
     * @param studentId ID của học viên (để verify ownership)
     * @param reportId ID của báo cáo
     * @return Báo cáo
     */
    StudentLearningReportResponse getReportById(Long studentId, Long reportId);

    /**
     * Lấy các metrics hiện tại của học viên (không tạo báo cáo).
     * 
     * @param studentId ID của học viên
     * @return Metrics của học viên
     */
    StudentLearningReportResponse.StudentMetrics getCurrentMetrics(Long studentId);

    /**
     * Kiểm tra xem học viên có thể tạo báo cáo mới không (rate limit check).
     * 
     * @param studentId ID của học viên
     * @return true nếu có thể tạo báo cáo mới
     */
    boolean canGenerateNewReport(Long studentId);

    /**
     * Đếm số báo cáo của học viên.
     * 
     * @param studentId ID của học viên
     * @return Số lượng báo cáo
     */
    long countReports(Long studentId);
}
