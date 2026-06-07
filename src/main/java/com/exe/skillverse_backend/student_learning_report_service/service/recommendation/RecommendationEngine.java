package com.exe.skillverse_backend.student_learning_report_service.service.recommendation;

import com.exe.skillverse_backend.student_learning_report_service.dto.response.StudentLearningReportResponse;
import com.exe.skillverse_backend.student_learning_report_service.dto.response.StudentLearningReportResponse.Recommendation;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Pure rule-based recommendation engine for the student learning report.
 *
 * <p>The engine inspects multi-dimensional learning data (study habits,
 * roadmap progress, tasks, courses, short-term jobs) and emits a list of
 * {@link Recommendation} entries grouped into four tiers:
 * {@code CRITICAL}, {@code IMPROVE}, {@code NEXT_STEP}, and {@code STRENGTH}.
 *
 * <p>This replaces the previous AI-flavoured copywriting with deterministic,
 * data-grounded suggestions so we no longer need an LLM call to render the
 * "Phân Tích &amp; Đề Xuất" panel.
 */
@Component
public class RecommendationEngine {

    public static final String TIER_CRITICAL = "CRITICAL";
    public static final String TIER_IMPROVE = "IMPROVE";
    public static final String TIER_NEXT_STEP = "NEXT_STEP";
    public static final String TIER_STRENGTH = "STRENGTH";

    public static final String CAT_STUDY = "STUDY";
    public static final String CAT_ROADMAP = "ROADMAP";
    public static final String CAT_TASK = "TASK";
    public static final String CAT_COURSE = "COURSE";
    public static final String CAT_JOB = "JOB";
    public static final String CAT_GROWTH = "GROWTH";

    private static final int MAX_RECOMMENDATIONS = 8;

    private static final Map<String, Integer> TIER_ORDER = Map.of(
            TIER_CRITICAL, 0,
            TIER_IMPROVE, 1,
            TIER_NEXT_STEP, 2,
            TIER_STRENGTH, 3);

    /**
     * Build a prioritised list of recommendations for a learner.
     *
     * @param input snapshot of every dimension required to generate advice
     * @return ordered, deduplicated list capped at {@value #MAX_RECOMMENDATIONS}
     */
    public List<Recommendation> generate(EngineInput input) {
        List<Recommendation> all = new ArrayList<>();
        Objects.requireNonNull(input, "input must not be null");

        evaluateStudy(input, all);
        evaluateRoadmap(input, all);
        evaluateTask(input, all);
        evaluateCourse(input, all);
        evaluateJob(input, all);
        evaluateGrowth(input, all);

        if (all.isEmpty()) {
            all.add(Recommendation.builder()
                    .id("growth-empty-state")
                    .tier(TIER_STRENGTH)
                    .category(CAT_GROWTH)
                    .title("Bắt đầu hành trình của bạn")
                    .analysis("Hệ thống chưa có đủ dữ liệu để phân tích. Tạo phiên học, "
                            + "thêm task hoặc apply một roadmap để bắt đầu nhận đề xuất cá nhân hoá.")
                    .action("Mở trang Lộ trình hoặc Study Planner để tạo mục tiêu đầu tiên.")
                    .linkPath("/journey-create")
                    .linkLabel("Tạo lộ trình")
                    .build());
        }

        all.sort(Comparator
                .comparingInt((Recommendation r) -> TIER_ORDER.getOrDefault(r.getTier(), 99))
                .thenComparing(Recommendation::getCategory, Comparator.nullsLast(String::compareTo)));

        if (all.size() > MAX_RECOMMENDATIONS) {
            return new ArrayList<>(all.subList(0, MAX_RECOMMENDATIONS));
        }
        return all;
    }

    // ---------------------------------------------------------------------
    // Dimension evaluators
    // ---------------------------------------------------------------------

    private void evaluateStudy(EngineInput input, List<Recommendation> out) {
        StudentLearningReportResponse.StudyStats stats = input.studyStats;
        if (stats == null) {
            return;
        }
        int weeklyMinutes = nz(stats.getStudyMinutesWeek());
        int streak = nz(stats.getCurrentStreak());

        Integer prevWeekly = input.previousWeeklyStudyMinutes;
        if (prevWeekly != null && prevWeekly > 60 && weeklyMinutes < prevWeekly * 0.7) {
            out.add(Recommendation.builder()
                    .id("study-momentum-drop")
                    .tier(TIER_CRITICAL)
                    .category(CAT_STUDY)
                    .title("Đà học đang chậm lại")
                    .analysis("Tuần này bạn học " + weeklyMinutes + " phút, giảm "
                            + percentDrop(prevWeekly, weeklyMinutes) + "% so với kỳ trước ("
                            + prevWeekly + " phút).")
                    .action("Đặt lại lịch học cố định 25 phút/ngày trong 5 ngày tới để khôi phục đà học.")
                    .metricLabel("Phút học/tuần")
                    .metricValue(weeklyMinutes)
                    .metricTarget(Math.max(prevWeekly, 150))
                    .metricUnit("phút")
                    .linkPath("/study-planner")
                    .linkLabel("Mở Study Planner")
                    .build());
        }

        if (streak == 0 && weeklyMinutes < 60) {
            out.add(Recommendation.builder()
                    .id("study-streak-critical")
                    .tier(TIER_CRITICAL)
                    .category(CAT_STUDY)
                    .title("Khôi phục thói quen học")
                    .analysis("Streak hiện tại 0 ngày, tuần này mới " + weeklyMinutes + " phút.")
                    .action("Lên lịch 3 ngày liên tiếp × 20 phút trong tuần này để khởi động lại streak.")
                    .metricLabel("Phút học/tuần")
                    .metricValue(weeklyMinutes)
                    .metricTarget(120)
                    .metricUnit("phút")
                    .linkPath("/study-planner")
                    .linkLabel("Lên lịch học")
                    .build());
        } else if (weeklyMinutes < 120) {
            out.add(Recommendation.builder()
                    .id("study-weekly-below-target")
                    .tier(TIER_IMPROVE)
                    .category(CAT_STUDY)
                    .title("Tăng thời lượng học hàng tuần")
                    .analysis("Đang đạt " + weeklyMinutes + " phút/tuần (mục tiêu tối thiểu 120 phút).")
                    .action("Thêm 25 phút/ngày × 5 ngày tới để cán mốc 120 phút/tuần.")
                    .metricLabel("Phút học/tuần")
                    .metricValue(weeklyMinutes)
                    .metricTarget(120)
                    .metricUnit("phút")
                    .linkPath("/study-planner")
                    .linkLabel("Mở Study Planner")
                    .build());
        }

        if (weeklyMinutes >= 300 && streak >= 5) {
            out.add(Recommendation.builder()
                    .id("study-strength")
                    .tier(TIER_STRENGTH)
                    .category(CAT_STUDY)
                    .title("Thời gian học xuất sắc")
                    .analysis(weeklyMinutes + " phút/tuần và streak " + streak
                            + " ngày — đang ở nhóm học tập kỷ luật cao.")
                    .action("Duy trì nhịp hiện tại; cân nhắc nâng độ khó tài liệu để tránh chững.")
                    .metricLabel("Phút học/tuần")
                    .metricValue(weeklyMinutes)
                    .metricUnit("phút")
                    .build());
        }
    }

    private void evaluateRoadmap(EngineInput input, List<Recommendation> out) {
        StudentLearningReportResponse.RoadmapStats stats = input.roadmapStats;
        List<StudentLearningReportResponse.RoadmapBreakdownItem> items = input.roadmapBreakdown;
        if (stats == null || items == null || items.isEmpty()) {
            return;
        }
        int progress = nz(stats.getRoadmapProgress());

        // Stagnant roadmap (no completion in 14d)
        LocalDateTime now = input.now == null ? LocalDateTime.now() : input.now;
        items.stream()
                .filter(item -> nz(item.getPendingMissions()) > 0)
                .filter(item -> item.getLastCompletedAt() != null)
                .filter(item -> Duration.between(item.getLastCompletedAt(), now).toDays() >= 14)
                .min(Comparator.comparing(StudentLearningReportResponse.RoadmapBreakdownItem::getLastCompletedAt))
                .ifPresent(item -> out.add(Recommendation.builder()
                        .id("roadmap-stagnant")
                        .tier(TIER_CRITICAL)
                        .category(CAT_ROADMAP)
                        .title("Roadmap đang đứng yên")
                        .analysis("Roadmap \"" + item.getTitle() + "\" không có mission hoàn thành trong "
                                + Duration.between(item.getLastCompletedAt(), now).toDays() + " ngày.")
                        .action("Hoàn thành mission \"" + safeTitle(item.getNextMissionTitle())
                                + "\" trong 3 ngày tới để mở khoá đà tiến độ.")
                        .metricLabel("Tiến độ roadmap")
                        .metricValue(item.getProgressPercent())
                        .metricTarget(100)
                        .metricUnit("%")
                        .linkPath("/roadmap")
                        .linkLabel("Mở roadmap")
                        .build()));

        if (progress < 50) {
            items.stream()
                    .filter(item -> nz(item.getPendingMissions()) > 0)
                    .min(Comparator.comparing(item -> nz(item.getProgressPercent())))
                    .ifPresent(item -> out.add(Recommendation.builder()
                            .id("roadmap-priority")
                            .tier(TIER_IMPROVE)
                            .category(CAT_ROADMAP)
                            .title("Ưu tiên roadmap \"" + item.getTitle() + "\"")
                            .analysis("Tiến độ hiện tại " + nz(item.getProgressPercent()) + "% với "
                                    + nz(item.getPendingMissions()) + " mission đang chờ.")
                            .action("Tập trung hoàn thành mission kế tiếp \"" + safeTitle(item.getNextMissionTitle())
                                    + "\" trong tuần này.")
                            .metricLabel("Tiến độ roadmap")
                            .metricValue(item.getProgressPercent())
                            .metricTarget(50)
                            .metricUnit("%")
                            .linkPath("/roadmap")
                            .linkLabel("Mở roadmap")
                            .build()));
        }

        if (progress >= 80) {
            out.add(Recommendation.builder()
                    .id("roadmap-strength")
                    .tier(TIER_STRENGTH)
                    .category(CAT_ROADMAP)
                    .title("Tiến độ roadmap xuất sắc")
                    .analysis("Tiến độ roadmap " + progress + "% — bạn đang giữ momentum tốt.")
                    .action("Sẵn sàng đặt mục tiêu nâng cao hoặc apply Short-term Job để áp dụng kỹ năng.")
                    .metricLabel("Tiến độ roadmap")
                    .metricValue(progress)
                    .metricUnit("%")
                    .build());
        }
    }

    private void evaluateTask(EngineInput input, List<Recommendation> out) {
        StudentLearningReportResponse.TaskStats stats = input.taskStats;
        if (stats == null) {
            return;
        }
        int overdue = nz(stats.getOverdueTasks());
        int pending = nz(stats.getPendingTasks());
        int taskProgress = nz(stats.getTaskProgress());

        if (overdue > 0) {
            out.add(Recommendation.builder()
                    .id("task-overdue")
                    .tier(TIER_CRITICAL)
                    .category(CAT_TASK)
                    .title("Dọn task quá hạn")
                    .analysis("Có " + overdue + " task quá hạn và " + pending + " task đang chờ.")
                    .action("Dành 1 buổi 60 phút hôm nay để xử lý dứt điểm hoặc dời lịch task quá hạn.")
                    .metricLabel("Task quá hạn")
                    .metricValue(overdue)
                    .metricTarget(0)
                    .metricUnit("task")
                    .linkPath("/tasks")
                    .linkLabel("Mở Task")
                    .build());
        } else if (pending >= 5) {
            out.add(Recommendation.builder()
                    .id("task-backlog")
                    .tier(TIER_IMPROVE)
                    .category(CAT_TASK)
                    .title("Áp dụng Eisenhower Matrix")
                    .analysis(pending + " task đang chờ — dễ phân tán nếu không phân loại ưu tiên.")
                    .action("Chọn top-3 task quan trọng/khẩn nhất để hoàn thành trước cuối tuần.")
                    .metricLabel("Task đang chờ")
                    .metricValue(pending)
                    .metricTarget(3)
                    .metricUnit("task")
                    .linkPath("/tasks")
                    .linkLabel("Mở Task")
                    .build());
        }

        if (taskProgress >= 80 && nz(stats.getTotalTasks()) >= 3) {
            out.add(Recommendation.builder()
                    .id("task-strength")
                    .tier(TIER_STRENGTH)
                    .category(CAT_TASK)
                    .title("Quản lý task hiệu quả")
                    .analysis("Tỷ lệ hoàn thành task " + taskProgress + "% — kỹ năng tự quản tốt.")
                    .action("Giữ thói quen rà soát task đầu mỗi tuần để duy trì hiệu suất.")
                    .metricLabel("Hoàn thành task")
                    .metricValue(taskProgress)
                    .metricUnit("%")
                    .build());
        }
    }

    private void evaluateCourse(EngineInput input, List<Recommendation> out) {
        StudentLearningReportResponse.CourseStats stats = input.courseStats;
        List<StudentLearningReportResponse.CourseBreakdownItem> items = input.courseBreakdown;
        if (stats == null) {
            return;
        }
        int avg = nz(stats.getAverageActiveCourseProgress());
        int active = nz(stats.getActiveCourses());

        if (active > 0 && avg < 40 && items != null) {
            items.stream()
                    .filter(c -> "enrolled".equalsIgnoreCase(c.getStatus()))
                    .min(Comparator.comparing(c -> nz(c.getProgressPercent())))
                    .ifPresent(course -> out.add(Recommendation.builder()
                            .id("course-slow")
                            .tier(TIER_IMPROVE)
                            .category(CAT_COURSE)
                            .title("Đẩy nhanh khóa \"" + course.getCourseTitle() + "\"")
                            .analysis("Tiến độ " + nz(course.getProgressPercent())
                                    + "% — chậm hơn ngưỡng kỳ vọng (40%).")
                            .action("Học 30 phút/ngày × 7 ngày để đạt mốc kế tiếp (+20%).")
                            .metricLabel("Tiến độ khóa")
                            .metricValue(course.getProgressPercent())
                            .metricTarget(60)
                            .metricUnit("%")
                            .linkPath("/courses")
                            .linkLabel("Mở khóa học")
                            .build()));
        }

        if (avg >= 70 && active > 0) {
            out.add(Recommendation.builder()
                    .id("course-strength")
                    .tier(TIER_STRENGTH)
                    .category(CAT_COURSE)
                    .title("Tiến độ khóa học tốt")
                    .analysis("Trung bình " + avg + "% trên " + active + " khóa đang học.")
                    .action("Hoàn thành nốt và lấy chứng chỉ để bổ sung vào portfolio.")
                    .metricLabel("Tiến độ trung bình")
                    .metricValue(avg)
                    .metricUnit("%")
                    .build());
        }
    }

    private void evaluateJob(EngineInput input, List<Recommendation> out) {
        StudentLearningReportResponse.ShortTermJobStats stats = input.jobStats;
        if (stats == null) {
            return;
        }
        int totalApplied = nz(stats.getTotalJobsApplied());
        int completed = nz(stats.getCompletedJobs());
        int onTime = nz(stats.getOnTimeDeliveryRate());
        int roadmapProgress = input.roadmapStats != null ? nz(input.roadmapStats.getRoadmapProgress()) : 0;

        if (totalApplied == 0 && roadmapProgress >= 50) {
            out.add(Recommendation.builder()
                    .id("job-start-applying")
                    .tier(TIER_NEXT_STEP)
                    .category(CAT_JOB)
                    .title("Áp dụng kiến thức vào Short-term Job")
                    .analysis("Roadmap đã đạt " + roadmapProgress + "% nhưng chưa apply job nào.")
                    .action("Apply 1 Short-term Job phù hợp kỹ năng đã học để xây portfolio thực tế.")
                    .linkPath("/jobs")
                    .linkLabel("Khám phá job")
                    .build());
        } else if (completed == 0 && nz(stats.getInProgressJobs()) >= 1) {
            out.add(Recommendation.builder()
                    .id("job-finish-first")
                    .tier(TIER_IMPROVE)
                    .category(CAT_JOB)
                    .title("Hoàn thành job đầu tiên")
                    .analysis("Đã được nhận vào " + stats.getInProgressJobs() + " job nhưng chưa hoàn thành job nào.")
                    .action("Tập trung hoàn thành công việc hiện tại để xây trust score & lấy review.")
                    .linkPath("/jobs")
                    .linkLabel("Mở danh sách job")
                    .build());
        } else if (completed == 0 && totalApplied >= 1 && nz(stats.getInProgressJobs()) == 0) {
            out.add(Recommendation.builder()
                    .id("job-applying-active")
                    .tier(TIER_NEXT_STEP)
                    .category(CAT_JOB)
                    .title("Theo dõi đơn ứng tuyển")
                    .analysis("Bạn đã ứng tuyển " + totalApplied + " job nhưng chưa được nhận hoặc đang làm job nào.")
                    .action("Tiếp tục ứng tuyển các Short-term Job khác hoặc cải thiện CV để tăng tỷ lệ duyệt.")
                    .linkPath("/jobs")
                    .linkLabel("Khám phá job")
                    .build());
        }

        if (completed >= 1 && onTime < 70) {
            out.add(Recommendation.builder()
                    .id("job-on-time")
                    .tier(TIER_CRITICAL)
                    .category(CAT_JOB)
                    .title("Cải thiện on-time delivery")
                    .analysis("Tỷ lệ giao đúng hạn hiện tại " + onTime + "% (trên " + completed
                            + " job hoàn thành).")
                    .action("Đặt deadline nội bộ trước deadline thật 1 ngày và bật reminder.")
                    .metricLabel("On-time delivery")
                    .metricValue(onTime)
                    .metricTarget(90)
                    .metricUnit("%")
                    .linkPath("/jobs")
                    .linkLabel("Mở job đang làm")
                    .build());
        } else if (completed >= 3 && onTime >= 80) {
            BigDecimal earnings = stats.getTotalEarnings();
            String earningSuffix = earnings != null && earnings.signum() > 0
                    ? " và thu nhập " + earnings.toPlainString() + " VND"
                    : "";
            out.add(Recommendation.builder()
                    .id("job-strength")
                    .tier(TIER_STRENGTH)
                    .category(CAT_JOB)
                    .title("Hiệu suất công việc xuất sắc")
                    .analysis(completed + " job hoàn thành, on-time " + onTime + "%" + earningSuffix + ".")
                    .action("Cập nhật portfolio với job tốt nhất và xin testimonial từ recruiter.")
                    .metricLabel("On-time delivery")
                    .metricValue(onTime)
                    .metricUnit("%")
                    .linkPath("/portfolio")
                    .linkLabel("Cập nhật portfolio")
                    .build());
        }
    }

    private void evaluateGrowth(EngineInput input, List<Recommendation> out) {
        boolean studyOk = input.studyStats != null
                && nz(input.studyStats.getStudyMinutesWeek()) >= 240
                && nz(input.studyStats.getCurrentStreak()) >= 3;
        boolean roadmapOk = input.roadmapStats != null
                && nz(input.roadmapStats.getRoadmapProgress()) >= 60;
        boolean taskOk = input.taskStats != null
                && nz(input.taskStats.getOverdueTasks()) == 0
                && nz(input.taskStats.getTaskProgress()) >= 60;

        if (studyOk && roadmapOk && taskOk) {
            out.add(Recommendation.builder()
                    .id("growth-balanced")
                    .tier(TIER_NEXT_STEP)
                    .category(CAT_GROWTH)
                    .title("Hệ sinh thái học tập đang cân bằng")
                    .analysis("Cả thời gian học, roadmap và task đều ở mức tốt.")
                    .action("Đặt lịch mentor 1-1 hoặc dạy lại kiến thức cho bạn cùng học để củng cố.")
                    .linkPath("/mentor")
                    .linkLabel("Đặt mentor")
                    .build());
        }
    }

    // ---------------------------------------------------------------------
    // Utilities
    // ---------------------------------------------------------------------

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }

    private static int percentDrop(int previous, int current) {
        if (previous <= 0) {
            return 0;
        }
        return Math.max(0, (int) Math.round(((previous - current) * 100.0) / previous));
    }

    private static String safeTitle(String title) {
        return title == null || title.isBlank() ? "mission tiếp theo" : title;
    }

    /**
     * Container of every signal needed by the engine. Built by the
     * {@code StudentLearningReportServiceImpl} prior to invoking
     * {@link #generate(EngineInput)}.
     */
    public static class EngineInput {
        public StudentLearningReportResponse.StudyStats studyStats;
        public StudentLearningReportResponse.RoadmapStats roadmapStats;
        public List<StudentLearningReportResponse.RoadmapBreakdownItem> roadmapBreakdown;
        public StudentLearningReportResponse.TaskStats taskStats;
        public StudentLearningReportResponse.CourseStats courseStats;
        public List<StudentLearningReportResponse.CourseBreakdownItem> courseBreakdown;
        public StudentLearningReportResponse.ShortTermJobStats jobStats;
        public List<StudentLearningReportResponse.JobBreakdownItem> jobBreakdown;
        /** Weekly study minutes in the most recent saved snapshot, if any. */
        public Integer previousWeeklyStudyMinutes;
        /** Reference instant (e.g. report generation time). */
        public LocalDateTime now;
    }
}
