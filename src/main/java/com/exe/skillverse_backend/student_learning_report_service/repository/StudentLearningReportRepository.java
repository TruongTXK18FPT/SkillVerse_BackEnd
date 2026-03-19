package com.exe.skillverse_backend.student_learning_report_service.repository;

import com.exe.skillverse_backend.student_learning_report_service.entity.StudentLearningReport;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository cho StudentLearningReport.
 * Cung cấp các phương thức truy vấn báo cáo học tập cá nhân.
 */
@Repository
public interface StudentLearningReportRepository extends JpaRepository<StudentLearningReport, Long> {

    /**
     * Tìm tất cả báo cáo của một học viên theo thứ tự mới nhất.
     */
    List<StudentLearningReport> findByStudentIdOrderByGeneratedAtDesc(Long studentId);

    /**
     * Tìm tất cả báo cáo của học viên (phân trang).
     */
    Page<StudentLearningReport> findByStudentIdOrderByGeneratedAtDesc(Long studentId, Pageable pageable);

    /**
     * Tìm báo cáo mới nhất của học viên.
     */
    Optional<StudentLearningReport> findFirstByStudentIdOrderByGeneratedAtDesc(Long studentId);

    /**
     * Tìm báo cáo mới nhất theo loại.
     */
    Optional<StudentLearningReport> findFirstByStudentIdAndReportTypeOrderByGeneratedAtDesc(
            Long studentId, StudentLearningReport.ReportType reportType);

    /**
     * Kiểm tra xem đã có báo cáo nào được tạo trong khoảng thời gian nhất định chưa.
     * Dùng để rate limit việc tạo báo cáo.
     */
    boolean existsByStudentIdAndGeneratedAtAfter(Long studentId, LocalDateTime after);

    /**
     * Kiểm tra rate limit theo loại báo cáo.
     */
    boolean existsByStudentIdAndReportTypeAndGeneratedAtAfter(
            Long studentId, StudentLearningReport.ReportType reportType, LocalDateTime after);

    /**
     * Đếm số báo cáo của một học viên.
     */
    long countByStudentId(Long studentId);

    /**
     * Đếm số báo cáo theo loại.
     */
    long countByStudentIdAndReportType(Long studentId, StudentLearningReport.ReportType reportType);

    /**
     * Tìm các báo cáo trong khoảng thời gian.
     */
    List<StudentLearningReport> findByStudentIdAndGeneratedAtBetweenOrderByGeneratedAtDesc(
            Long studentId, LocalDateTime start, LocalDateTime end);

    /**
     * Xóa các báo cáo cũ hơn một thời điểm nhất định (cleanup job).
     */
    void deleteByGeneratedAtBefore(LocalDateTime before);

    /**
     * Thống kê số lượng báo cáo theo loại cho một học viên.
     */
    @Query("SELECT r.reportType, COUNT(r) FROM StudentLearningReport r WHERE r.student.id = :studentId GROUP BY r.reportType")
    List<Object[]> countByStudentIdGroupByReportType(@Param("studentId") Long studentId);
}
