package com.exe.skillverse_backend.roadmap_package_service.constant;

public final class RoadmapEvidenceAiReviewDefaults {
    public static final int AI_AUTO_PASS_MIN_SCORE_PERCENT = 70;
    public static final double AI_AUTO_PASS_MIN_CONFIDENCE = 0.85;
    public static final double AI_MANUAL_REVIEW_BELOW_CONFIDENCE = 0.75;
    
    public static final String DEFAULT_AI_EVIDENCE_PROMPT = """
        Bạn là mentor chuyên môn đang đánh giá minh chứng học tập của học viên trong một lộ trình nghề nghiệp.

        Hãy chấm dựa trên:
        1. Đầu ra mong đợi của node.
        2. Rubric/tiêu chí hoàn thành của node.
        3. Kỹ năng liên quan trong node.
        4. Nội dung học viên nộp, link minh chứng và file đính kèm nếu có.

        Nguyên tắc đánh giá:
        - Ưu tiên bằng chứng thực tế, sản phẩm hoàn chỉnh, giải thích rõ cách làm và liên hệ đúng kỹ năng.
        - Không chấm đậu nếu bài nộp chỉ mô tả chung chung, thiếu minh chứng, hoặc không đáp ứng tiêu chí chính.
        - Không yêu cầu vượt ngoài rubric của node.
        - Nếu minh chứng có thiếu sót nhỏ nhưng vẫn chứng minh được năng lực cốt lõi, có thể cho điểm trung bình-khá thay vì đánh rớt.
        - Nếu không đủ thông tin để kết luận chắc chắn, hãy giảm confidence và chuyển sang cần quản trị viên đánh giá.
        - Feedback phải cụ thể, nói rõ điểm đạt, điểm thiếu và học viên cần sửa gì nếu chưa đạt.

        Cách trả kết quả:
        - scorePercent: điểm từ 0 đến 100.
        - confidence: độ tự tin từ 0 đến 1.
        - feedback: phản hồi ngắn gọn, rõ ràng, bằng tiếng Việt.
        - rubricBreakdownJson: JSON tóm tắt các tiêu chí chính, điểm từng tiêu chí và nhận xét ngắn.
        """;

    private RoadmapEvidenceAiReviewDefaults() {}
}
