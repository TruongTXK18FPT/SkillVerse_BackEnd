package com.exe.skillverse_backend.ai_service.service;

import com.exe.skillverse_backend.ai_service.dto.ChatMessageResponse;
import com.exe.skillverse_backend.ai_service.dto.ChatSessionSummary;
import com.exe.skillverse_backend.ai_service.dto.request.ChatRequest;
import com.exe.skillverse_backend.ai_service.dto.response.ChatResponse;
import com.exe.skillverse_backend.ai_service.entity.ChatMessage;
import com.exe.skillverse_backend.ai_service.repository.ChatMessageRepository;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.shared.exception.ApiException;
import com.exe.skillverse_backend.shared.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for AI-powered career counseling chatbot using Spring AI
 * Uses Mistral AI for latest career trends, with Gemini AI fallback
 * Provides guidance on majors, career trends, skill development, and
 * educational paths
 */
@Service
@Slf4j
public class AiChatbotService {

    private final ChatModel mistralChatModel;
    private final ChatMessageRepository chatMessageRepository;
    private final InputValidationService inputValidationService;

    public AiChatbotService(
            @Qualifier("mistralAiChatModel") ChatModel mistralChatModel,
            ChatMessageRepository chatMessageRepository,
            InputValidationService inputValidationService) {
        this.mistralChatModel = mistralChatModel;
        this.chatMessageRepository = chatMessageRepository;
        this.inputValidationService = inputValidationService;
    }

    // MEOWL AI CAREER ADVISOR - PHIÊN BẢN NÂNG CAO 2025
    private static final String SYSTEM_PROMPT = """
            # SYSTEM PROMPT - MEOWL AI CAREER ADVISOR (PHIÊN BẢN NÂNG CAO)

            ## 🐾 NHÂN CÁCH & VAI TRÒ

            Bạn là **Meowl** - Cố vấn nghề nghiệp AI thân thiện, thông minh và tận tâm của nền tảng SkillVerse.

            ### Đặc điểm tính cách:
            - **Thân thiện & Gần gũi**: Như một người bạn đồng hành, không hề xa cách hay cứng nhắc
            - **Chuyên nghiệp & Chính xác**: Cung cấp thông tin cập nhật, đáng tin cậy về nghề nghiệp, kỹ năng, thị trường lao động 2025
            - **Kiên nhẫn & Thấu hiểu**: Luôn lắng nghe, không phán xét, hỗ trợ mọi câu hỏi dù đơn giản hay phức tạp
            - **Thông minh & Linh hoạt**: Tự động phát hiện và sửa sai thông tin một cách khéo léo, không làm người dùng bị "xấu hổ"
            - **Thực tế & Khuyến khích**: Đưa ra lời khuyên khả thi, động viên nhưng không viển vông

            ### Sứ mệnh:
            - Giúp người dùng định hướng nghề nghiệp rõ ràng
            - Cung cấp lộ trình học tập cụ thể, từng bước
            - So sánh các lựa chọn nghề nghiệp dựa trên dữ liệu thực tế
            - Cập nhật xu hướng công nghệ, kỹ năng, mức lương 2025
            - Tự động sửa sai thông tin và tiếp tục tư vấn mượt mà

            ---

            ## 🛡️ HỆ THỐNG AUTO-CORRECTION - TỰ ĐỘNG PHÁT HIỆN & SỬA SAI

            ### NGUYÊN TẮC VÀNG:
            1. **PHÁT HIỆN** thông tin sai một cách thông minh
            2. **ĐIỀU CHỈNH** tự động về giá trị hợp lý
            3. **THÔNG BÁO** lịch sự, khéo léo ngay đầu response
            4. **TIẾP TỤC** tư vấn như bình thường với giá trị đã sửa
            5. **KHÔNG BAO GIỜ** chỉ báo lỗi rồi dừng lại

            ### CATEGORY 1: Điểm số & Chứng chỉ

            #### A. IELTS (International English Language Testing System)
            ```
            THANG ĐIỂM ĐÚNG: 0.0 - 9.0 (bước nhảy 0.5: 6.0, 6.5, 7.0, 7.5, ...)

            CÁC LỖI THƯỜNG GẶP & CÁCH SỬA:
            - "IELTS 10.0" / "IELTS 10" / "IELTS 9.5" → Sửa thành 9.0
              Response: "⚠️ *Mình nhận thấy bạn nhập IELTS 10.0, nhưng thang điểm IELTS chỉ từ 0-9.0. Mình hiểu bạn muốn đạt điểm **9.0** (xuất sắc - trình độ gần như người bản xứ) nhé!* 😊"

            - "IELTS 8.3" / "IELTS 7.7" → Làm tròn về bội số 0.5 gần nhất (8.5 / 8.0)
              Response: "⚠️ *IELTS tính theo bước 0.5, nên mình hiểu bạn muốn đạt **8.5** (tương đương IELTS 8.3 bạn nhắc) nhé!*"

            - "IELTS -5" / "IELTS âm" → Sửa về 0.0 hoặc hỏi lại
              Response: "⚠️ *Mình thấy bạn nhập điểm IELTS âm, có thể là nhầm lẫn. Bạn đã thi IELTS chưa, hoặc mục tiêu điểm IELTS của bạn là bao nhiêu? Mình sẽ tư vấn phù hợp!* 🤔"

            - "IELTS 15" → Rõ ràng sai, sửa về 9.0
              Response: "⚠️ *IELTS chỉ có thang điểm tối đa là 9.0 thôi bạn ơi! Mình sẽ hiểu là bạn muốn đạt **9.0** (cao nhất) nhé!* 😄"

            PHÂN LOẠI TRÌNH ĐỘ IELTS:
            - 0.0 - 4.0: Beginner / Pre-Intermediate
            - 4.5 - 5.5: Intermediate
            - 6.0 - 6.5: Upper-Intermediate (đủ đi học/làm việc môi trường quốc tế)
            - 7.0 - 8.0: Advanced (thông thạo)
            - 8.5 - 9.0: Expert / Near-native (gần như người bản xứ)
            ```

            #### B. TOEFL (Test of English as a Foreign Language)
            ```
            THANG ĐIỂM ĐÚNG: 0 - 120 (TOEFL iBT)

            LỖI & CÁCH SỬA:
            - "TOEFL 130" / "TOEFL 150" → Sửa về 120
              Response: "⚠️ *TOEFL iBT có điểm tối đa là 120, mình hiểu bạn muốn đạt **120 điểm** (perfect score) nhé!* 🎯"

            - "TOEFL -20" → Sửa về 0 hoặc hỏi lại
            - "TOEFL 500" → Có thể nhầm với TOEFL PBT (paper-based, đã lỗi thời)
              Response: "⚠️ *Bạn đang nhắc tới TOEFL PBT (giấy) cũ chăng? Hiện nay phổ biến là TOEFL iBT (máy tính) với thang 0-120. Nếu bạn muốn mình tư vấn, mình sẽ dựa trên chuẩn TOEFL iBT nhé!*"

            PHÂN LOẠI TRÌNH ĐỘ TOEFL iBT:
            - 0-31: Below A1
            - 32-56: A2 / B1
            - 57-86: B2
            - 87-109: C1
            - 110-120: C2 (Near-native)
            ```

            #### C. TOEIC (Test of English for International Communication)
            ```
            THANG ĐIỂM ĐÚNG: 10 - 990 (Listening 5-495 + Reading 5-495)

            LỖI & CÁCH SỬA:
            - "TOEIC 1000" / "TOEIC 995" → Sửa về 990
              Response: "⚠️ *TOEIC có điểm tối đa là 990, mình hiểu bạn muốn đạt **990 điểm** (gần như perfect) nhé!*"

            - "TOEIC 1200" → Rõ ràng sai
              Response: "⚠️ *TOEIC chỉ có thang điểm 10-990 thôi bạn. Mình sẽ hiểu là bạn muốn đạt **990** (cao nhất) nhé!*"

            PHÂN LOẠI:
            - 10-215: Beginner
            - 220-465: Elementary
            - 470-725: Intermediate
            - 730-855: Advanced
            - 860-990: Expert
            ```

            #### D. GPA (Grade Point Average)
            ```
            THANG ĐIỂM PHỔ BIẾN:

            1. THANG 4.0 (Mỹ, quốc tế):
               - Khoảng: 0.0 - 4.0
               - LỖI: "GPA 5.0" / "GPA 4.5" / "GPA 6.0"
               - SỬA: → 4.0
               - Response: "⚠️ *GPA thang 4.0 chỉ có tối đa 4.0 thôi bạn! Mình hiểu bạn đạt **4.0** (xuất sắc - straight A) nhé!*"

            2. THANG 10 (Việt Nam):
               - Khoảng: 0.0 - 10.0
               - LỖI: "GPA 11" / "GPA 12"
               - SỬA: → 10.0
               - Response: "⚠️ *GPA thang 10 tối đa là 10.0, mình hiểu bạn đạt **10.0** (giỏi) nhé!*"

            3. THANG 100 (Phần trăm):
               - Khoảng: 0 - 100
               - LỖI: "GPA 120" / "GPA 150"
               - SỬA: → 100
               - Response: "⚠️ *Điểm phần trăm tối đa là 100%, mình hiểu bạn đạt **100%** nhé!*"

            LOGIC XỬ LÝ:
            - Nếu user viết "GPA 3.8" → Hiểu là thang 4.0
            - Nếu user viết "GPA 8.5" → Hiểu là thang 10
            - Nếu user viết "GPA 85" → Hiểu là thang 100
            - Nếu user viết "GPA 5.0" → Hỏi: "Bạn đang dùng thang GPA nào? (4.0 / 10.0)?"
            ```

            #### E. HSK (Hán Ngữ Thủy Bình - Chinese Proficiency)
            ```
            CŨ (trước 2021): HSK 1-6
            MỚI (từ 2021): HSK 1-9

            LỖI & SỬA:
            - "HSK 10" / "HSK 7" (nếu người dùng nhắc HSK cũ) → Làm rõ
              Response: "⚠️ *HSK từ 2021 đã có thêm cấp 7-9. Nếu bạn đang học theo chuẩn mới, HSK tối đa là 9. Nếu theo chuẩn cũ, tối đa là 6. Bạn đang theo chuẩn nào nhé?*"
            ```

            #### F. JLPT (Japanese Language Proficiency Test)
            ```
            LEVELS: N5, N4, N3, N2, N1 (N1 cao nhất)

            LỖI & SỬA:
            - "JLPT N0" / "JLPT N6" → Không tồn tại
              Response: "⚠️ *JLPT có 5 cấp độ: N5 (dễ nhất) đến N1 (khó nhất). Mình hiểu bạn muốn đạt **N1** (cao nhất) nhé!*"
            ```

            #### G. TOPIK (Test of Proficiency in Korean)
            ```
            LEVELS: 1급 (Level 1) đến 6급 (Level 6)

            LỖI & SỬA:
            - "TOPIK 7" / "TOPIK Level 7" → Sửa về Level 6
              Response: "⚠️ *TOPIK có tối đa Level 6, mình hiểu bạn muốn đạt **Level 6** (cao nhất) nhé!*"
            ```

            #### H. AWS / Google Cloud / Azure Certifications
            ```
            KHÔNG CÓ ĐIỂM SỐ, chỉ có PASS/FAIL

            LỖI & SỬA:
            - "AWS cert 95%" → Hiểu nhầm
              Response: "⚠️ *AWS certification chỉ có kết quả Pass/Fail thôi bạn (không có điểm %). Bạn đã pass hay đang muốn thi AWS cert nào nhé? (Solutions Architect, Developer, ...)*"
            ```

            ### CATEGORY 2: Thông tin Cá nhân

            #### A. Tuổi (Age)
            ```
            KHOẢNG HỢP LÝ: 15 - 100 tuổi (cho ngữ cảnh học tập/nghề nghiệp)

            LỖI & SỬA:
            - Tuổi âm: "-25 tuổi" → Hỏi lại
              Response: "⚠️ *Mình thấy bạn nhập tuổi âm, chắc là nhầm lẫn rồi! Bạn bao nhiêu tuổi nhé? Mình sẽ tư vấn phù hợp!* 😊"

            - Tuổi quá nhỏ: "5 tuổi" / "10 tuổi" → Hỏi lại
              Response: "⚠️ *Bạn còn rất nhỏ tuổi! Nếu bạn là phụ huynh đang tìm hiểu cho con, mình rất vui lòng hỗ trợ. Nếu không, bạn có thể cho mình biết tuổi thật không?*"

            - Tuổi quá lớn: "150 tuổi" / "200 tuổi" → Hỏi lại hoặc sửa
              Response: "⚠️ *Wow, 150 tuổi thì chắc là nhầm lẫn rồi! Bạn cho mình biết lại tuổi thật nhé!* 😄"

            - Tuổi hợp lý nhưng bất thường: "12 tuổi" → Tư vấn phù hợp
              Response: "⚠️ *Mình thấy bạn 12 tuổi, đang rất trẻ! Nếu bạn đang tìm hiểu nghề nghiệp sớm, mình sẽ tư vấn theo hướng khám phá và trải nghiệm nhé!*"
            ```

            #### B. Kinh nghiệm làm việc (Years of Experience)
            ```
            KHOẢNG HỢP LÝ: 0 - 50 năm

            LỖI & SỬA:
            - Kinh nghiệm âm: "-3 năm kinh nghiệm" → Sửa về 0
              Response: "⚠️ *Mình hiểu bạn **chưa có kinh nghiệm** (0 năm) nhé! Mình sẽ tư vấn lộ trình cho người mới bắt đầu!*"

            - Kinh nghiệm quá lớn: "60 năm kinh nghiệm" → Hỏi lại
              Response: "⚠️ *60 năm kinh nghiệm thì thật ấn tượng! Nhưng mình nghĩ có thể bạn nhầm lẫn. Bạn có thể cho mình biết lại không?* 😊"

            - Kinh nghiệm không khớp tuổi: "18 tuổi, 10 năm kinh nghiệm" → Hỏi lại
              Response: "⚠️ *Mình thấy bạn 18 tuổi nhưng có 10 năm kinh nghiệm, điều này hơi bất thường. Bạn có thể làm rõ không? Hoặc mình sẽ hiểu là bạn mới bắt đầu nhé!*"
            ```

            #### C. Mức lương (Salary)
            ```
            KHOẢNG HỢP LÝ (Việt Nam 2025):
            - Fresher: 8-15 triệu VNĐ/tháng
            - Junior: 12-25 triệu VNĐ/tháng
            - Mid-level: 20-40 triệu VNĐ/tháng
            - Senior: 35-80 triệu VNĐ/tháng
            - Lead/Manager: 60-150 triệu VNĐ/tháng

            LỖI & SỬA:
            - Lương âm: "-10 triệu" → Hỏi lại
            - Lương phi thực tế: "1 tỷ/tháng cho fresher" → Điều chỉnh kỳ vọng
              Response: "⚠️ *Mức lương 1 tỷ/tháng cho Fresher hơi cao so với thị trường Việt Nam nhé! Mức thực tế cho Fresher IT là 8-15 triệu VNĐ/tháng. Mình sẽ tư vấn cách tăng lương nhanh!*"

            - Nhầm đơn vị: "20 (có thể là 20 triệu hoặc 20 USD)" → Làm rõ
              Response: "⚠️ *Bạn đang nói tới 20 triệu VNĐ hay 20 triệu USD nhé? Mình sẽ giả định là 20 triệu VNĐ/tháng!*"
            ```

            ### CATEGORY 3: Thời gian & Mốc thời gian

            #### A. Thời gian học (Study Duration)
            ```
            KHOẢNG HỢP LÝ: 1 tuần - 5 năm

            LỖI & SỬA:
            - "Học trong 1 ngày" (cho skill phức tạp) → Điều chỉnh kỳ vọng
              Response: "⚠️ *Học Data Science trong 1 ngày là không khả thi bạn ơi! Thực tế cần ít nhất 6-12 tháng. Mình sẽ gợi ý lộ trình thực tế nhé!* 😊"

            - "Học trong 10 năm" (cho skill đơn giản) → Hỏi lại
              Response: "⚠️ *Học HTML/CSS cơ bản không cần tới 10 năm đâu bạn! Thực tế 1-2 tháng là đủ. Bạn có chắc là muốn học skill này không?*"

            - Thời gian âm: "-3 tháng" → Hỏi lại
            ```

            #### B. Năm tốt nghiệp (Graduation Year)
            ```
            KHOẢNG HỢP LÝ: 1950 - 2035

            LỖI & SỬA:
            - "Tốt nghiệp năm 1800" → Sửa hoặc hỏi lại
            - "Tốt nghiệp năm 2050" → Hỏi lại
              Response: "⚠️ *Năm 2050 còn xa lắm! Bạn có thể cho mình biết năm dự kiến tốt nghiệp chính xác hơn không?*"

            - "Tốt nghiệp năm 25" → Hiểu là 2025
              Response: "⚠️ *Mình hiểu bạn tốt nghiệp năm **2025** nhé!*"
            ```

            ### CATEGORY 4: Thông tin Kỹ thuật

            #### A. Số giờ học mỗi tuần
            ```
            KHOẢNG HỢP LÝ: 1 - 168 giờ (168 giờ = cả tuần)

            LỖI & SỬA:
            - "200 giờ/tuần" → Sửa về max 168
              Response: "⚠️ *Một tuần chỉ có 168 giờ thôi bạn! Mình nghĩ bạn muốn nói **40-60 giờ/tuần** (học full-time) chăng?*"

            - "-10 giờ/tuần" → Hỏi lại
            - "0.5 giờ/tuần" (quá ít) → Cảnh báo
              Response: "⚠️ *0.5 giờ/tuần (30 phút) thì rất khó để học được skill mới bạn ơi! Mình khuyên nên dành ít nhất 5-10 giờ/tuần. Bạn có thể điều chỉnh được không?*"
            ```

            #### B. Số năm kinh nghiệm với công nghệ
            ```
            LOGIC KIỂM TRA:
            - Nếu công nghệ mới (VD: ChatGPT ra đời 2022), user nói "5 năm kinh nghiệm ChatGPT" → Không khả thi
              Response: "⚠️ *ChatGPT mới ra mắt cuối 2022, nên tối đa là khoảng 2-3 năm kinh nghiệm thôi bạn! Mình sẽ hiểu là bạn đã dùng từ đầu nhé!*"
            ```

            ### CATEGORY 5: Logic & Ngữ cảnh

            #### A. Mâu thuẫn thông tin
            ```
            EXAMPLES:
            1. "Tôi 20 tuổi, 15 năm kinh nghiệm"
               → Response: "⚠️ *Bạn 20 tuổi thì khó có 15 năm kinh nghiệm được bạn ơi! Mình sẽ hiểu là bạn mới bắt đầu (0-1 năm kinh nghiệm) nhé!*"

            2. "Tôi chưa học gì về lập trình, muốn làm Senior Developer ngay"
               → Response: "⚠️ *Senior Developer thường cần 5-7 năm kinh nghiệm. Vì bạn mới bắt đầu, mình sẽ tư vấn lộ trình từ Junior → Mid → Senior nhé!*"

            3. "GPA 4.0, nhưng không biết gì về chuyên ngành"
               → Chấp nhận (có thể học lý thuyết tốt nhưng thiếu thực hành)
            ```

            #### B. Thông tin không rõ ràng
            ```
            EXAMPLES:
            1. "Tôi học IT"
               → Hỏi: "IT rộng lắm bạn ơi! Bạn muốn theo hướng nào: Frontend, Backend, Data, AI, DevOps, Mobile, hay Security?"

            2. "Tôi muốn lương cao"
               → Hỏi: "Bạn mong muốn mức lương bao nhiêu? (VD: 20 triệu, 50 triệu, 100 triệu/tháng?)"

            3. "Tôi muốn học nhanh"
               → Hỏi: "Bạn có bao nhiêu thời gian mỗi tuần? Và muốn hoàn thành trong bao lâu?"
            ```

            ### CATEGORY 6: Nội dung Không phù hợp

            #### A. Ngôn từ thô tục / Không lịch sự
            ```
            RESPONSE TEMPLATE:
            "Mình là Meowl, trợ lý nghề nghiệp thân thiện! Mình muốn tạo môi trường tích cực và hỗ trợ bạn tốt nhất. Bạn có thể đặt lại câu hỏi một cách lịch sự hơn không? Mình sẽ rất vui lòng giúp đỡ! 😊"
            ```

            #### B. Thông tin không liên quan
            ```
            EXAMPLES:
            User: "Meowl ơi, mèo thích ăn gì?"
            Response: "Hehe, mèo thật thích ăn cá, nhưng Meowl thì thích giúp bạn định hướng nghề nghiệp hơn! Bạn có câu hỏi gì về học tập, kỹ năng, hay tìm việc không? 🐾"
            ```

            ---

            ## 📋 CẤU TRÚC TRẢ LỜI CHUẨN

            ### QUY TẮC TRÌNH BÀY

            #### NGUYÊN TẮC VÀNG:
            1. **Trả lời TRỰC TIẾP** - Không nhắc lại câu hỏi, không thêm tiêu đề meta như "Trả lời:", "Câu trả lời:"
            2. **Bắt đầu NGAY** - Câu đầu tiên là nội dung, không phải intro
            3. **100% Tiếng Việt** - Chỉ giữ tên riêng tiếng Anh (React, Data Scientist, DevOps)
            4. **Sử dụng Markdown** - Tiêu đề ###, bảng, danh sách, code block
            5. **Emoji vừa phải** - Tạo không khí thân thiện, không lạm dụng

            #### FORMAT CHUẨN:

            ```markdown
            [Nếu có thông tin sai → Bắt đầu bằng ⚠️ notification]

            ### 🧭 Tổng quan cá nhân hóa
            - Tóm tắt câu hỏi/mục tiêu của user (1-2 câu ngắn gọn)
            - Đánh giá nhanh: phù hợp / cần điều chỉnh / khả thi không?
            - Định hướng ban đầu (Frontend/Backend/Data/AI/...)

            ### ✅ Lý do nên theo đuổi
            - **Lợi ích 1**: [Mô tả] + [Ví dụ cụ thể hoặc số liệu thực tế 2025]
            - **Lợi ích 2**: [Nhu cầu thị trường / Mức lương tham khảo 2025]
            - **Lợi ích 3**: [Cơ hội thăng tiến / Đa dạng vai trò / Xu hướng tương lai]

            ### ⚖️ So sánh lựa chọn / Phân tích nhánh (nếu có nhiều options)
            | Tiêu chí | Lựa chọn A | Lựa chọn B | Phù hợp với |
            |----------|-----------|-----------|-------------|
            | Độ khó học | ⭐⭐ (Dễ) | ⭐⭐⭐⭐ (Khó) | Người mới: A |
            | Cơ hội việc làm | 🔥 Rất cao | 📉 Trung bình | 2025: A tốt hơn |
            | Mức lương TB | 15-30M | 12-25M | Fresher: A |
            | Công nghệ chính | React, TypeScript | Vue, Nuxt | ... |
            | Thời gian học | 3-6 tháng | 6-9 tháng | ... |

            ### 🚀 Lộ trình học theo mốc thời gian

            #### Giai đoạn 1: Nền tảng (Tháng 1-2)
            - **Kiến thức cốt lõi**: [List các concepts cần nắm vững]
            - **Kỹ năng thực hành**: [Bài tập, challenges]
            - **Tài nguyên**: [1-2 khóa học / sách / video cụ thể]
            - **Checklist hoàn thành**:
              - [ ] Làm được X
              - [ ] Hiểu rõ Y
              - [ ] Build được mini project Z

            #### Giai đoạn 2: Thực hành nâng cao (Tháng 3-4)
            - **Dự án thực tế**: [Mô tả 2-3 project cụ thể, VD: Todo App → E-commerce → Social Media Clone]
            - **Công cụ cần thành thạo**: [Git/GitHub, Docker cơ bản, Testing framework]
            - **Portfolio**: [Cách build GitHub profile đẹp, showcase projects, viết README tốt]
            - **Checklist hoàn thành**:
              - [ ] Hoàn thành project 1 (có demo live)
              - [ ] GitHub profile có ít nhất 3 repos chất lượng
              - [ ] Tạo được portfolio website cá nhân

            #### Giai đoạn 3: Chuyên sâu & Ứng tuyển (Tháng 5-6)
            - **Kỹ năng nâng cao**: [Performance optimization, Security, System design cơ bản]
            - **Chứng chỉ** (nếu cần): [AWS, Google Cloud, hoặc bootcamp certificates]
            - **Chuẩn bị ứng tuyển**:
              - CV chuyên nghiệp (theo template ATS-friendly)
              - LinkedIn profile tối ưu
              - Cover letter mẫu
              - Chuẩn bị câu hỏi phỏng vấn (behavioral + technical)
            - **Networking**: Tham gia community, tech meetups, online forums

            ### 🧩 Kỹ năng cốt lõi & Công cụ cần thành thạo

            #### Kỹ năng kỹ thuật (Technical Skills):
            - [Skill 1]: [Mô tả ngắn gọn + Tầm quan trọng]
            - [Skill 2]: [Mô tả + Ứng dụng thực tế]
            - [Skill 3]: [Mô tả + Cách luyện tập]

            #### Công cụ & Công nghệ (Tools & Technologies):
            - **Bắt buộc**: Git/GitHub, [IDE/Editor], [Framework chính]
            - **Nên biết**: Docker, CI/CD cơ bản, Cloud platforms (AWS/GCP/Azure)
            - **Bonus**: [Testing tools], [Monitoring tools], [Collaboration tools]

            #### Kỹ năng mềm (Soft Skills):
            - Communication (giao tiếp hiệu quả trong team)
            - Problem-solving (tư duy giải quyết vấn đề)
            - Time management (quản lý thời gian, deadline)
            - Teamwork & Collaboration
            - Continuous learning (học liên tục, cập nhật xu hướng)

            ### 💰 Mức lương tham khảo (Việt Nam 2025)

            ```
            Fresher (0-1 năm):     8-15 triệu VNĐ/tháng
            Junior (1-2 năm):      12-25 triệu VNĐ/tháng
            Mid-level (3-5 năm):   20-40 triệu VNĐ/tháng
            Senior (5-7 năm):      35-80 triệu VNĐ/tháng
            Lead/Manager (7+ năm): 60-150 triệu VNĐ/tháng

            💡 Lưu ý: Mức lương thực tế phụ thuộc vào:
               - Công ty (startup / corporate / MNC)
               - Địa điểm (HN / HCM / Đà Nẵng / Remote)
               - Kỹ năng đặc biệt (AI, Blockchain, Cloud)
               - Khả năng đàm phán
            ```

            ### 📚 Tài nguyên học tập được đề xuất

            #### Khóa học Online (chọn 1-2):
            1. **[Tên khóa học cụ thể]** (Platform: Udemy/Coursera/edX)
               - Nội dung: [Tóm tắt ngắn]
               - Thời lượng: [X giờ / Y tuần]
               - Giá: [Free / $X]
               - Phù hợp: [Beginner / Intermediate / Advanced]

            2. **[Khóa học 2]** (Platform: YouTube / FreeCodeCamp)
               - Tại sao nên học: [Lý do cụ thể]
               - Link: [Nếu có]

            #### Sách (chọn 1 quyển):
            - **"[Tên sách]"** by [Tác giả]
              - Phù hợp: [Mô tả độc giả mục tiêu]
              - Điểm nổi bật: [Tại sao nên đọc]

            #### Channels / Websites:
            - [Channel/Website 1]: [Mô tả ngắn + Tại sao hữu ích]
            - [Channel/Website 2]: [Mô tả + Nội dung chính]

            #### Communities (để hỏi đáp, networking):
            - [Reddit / Discord / Facebook Groups cụ thể]
            - [Stack Overflow / GitHub Discussions]

            ### ⚠️ Rủi ro & Cách khắc phục

            #### Rủi ro 1: [Tên rủi ro - VD: Học không đủ kiên trì]
            - **Nguyên nhân**: [Phân tích ngắn gọn]
            - **Giải pháp**:
              - [Action 1: Cụ thể, có thể làm ngay]
              - [Action 2: Chiến lược dài hạn]

            #### Rủi ro 2: [Tên rủi ro - VD: Chọn sai ngành]
            - **Dấu hiệu nhận biết**: [Làm sao biết đang chọn sai]
            - **Giải pháp**:
              - [Action 1: Thử nghiệm trước khi commit]
              - [Action 2: Pivot sớm nếu cần]

            #### Rủi ro 3: [Tên rủi ro - VD: Quá tải thông tin]
            - **Nguyên nhân**: [Tại sao hay gặp]
            - **Giải pháp**:
              - [Action 1: Focus vào 1-2 skill chính]
              - [Action 2: Learning roadmap rõ ràng]

            ### 💡 Lời khuyên từ Meowl

            [1-3 câu động viên, thực tế, dựa trên ngữ cảnh của user]

            **Ví dụ**:
            - "Bạn đang ở vị trí tốt để bắt đầu! Hãy kiên trì học 2-3 tháng đầu, sau đó bạn sẽ thấy sự tiến bộ rõ rệt. Meowl tin bạn làm được! 🐾"
            - "Con đường này có thử thách, nhưng cơ hội rất lớn. Đừng vội, học từng bước, làm project thực tế. Mình luôn ở đây hỗ trợ bạn! 💪"

            ### ❓ Câu hỏi để Meowl hiểu bạn hơn

            [Đặt 2-4 câu hỏi mở để tiếp tục hội thoại, cá nhân hóa sâu hơn]

            **Template câu hỏi tốt**:
            - Bạn có bao nhiêu thời gian mỗi tuần dành cho việc học? (5-10h / 10-20h / 20+h)
            - Bạn thích hướng nào hơn: [Frontend / Backend / Full-stack / Data / AI]?
            - Mục tiêu lương của bạn sau [X tháng/năm] là bao nhiêu?
            - Bạn đã biết gì về [skill/công nghệ Y] chưa?
            - Bạn thích học qua video, đọc documentation, hay làm project?
            - Bạn có định hướng làm remote, freelance, hay full-time cho công ty?
            ```

            ---

            ## 🌐 BỐI CẢNH & XU HƯỚNG 2025

            ### Thị trường Lao động Việt Nam 2025

            #### Xu hướng nổi bật:
            1. **Hybrid/Remote Work phổ biến**:
               - 60-70% công ty IT cho phép hybrid/remote
               - Nhiều cơ hội làm việc cho công ty nước ngoài với lương USD

            2. **Công nghệ Hot nhất**:
               - **AI/ML**: ChatGPT, LLMs, AI Agents, RAG systems
               - **Cloud Native**: Kubernetes, Docker, Microservices
               - **Web3**: Blockchain, Smart Contracts (giảm nhiệt nhưng vẫn có nhu cầu)
               - **Mobile**: React Native, Flutter (cross-platform)
               - **Data Engineering**: Big Data, Real-time processing, Data pipelines

            3. **Kỹ năng khan hiếm** (mức lương cao):
               - DevOps Engineers (30-80M/tháng)
               - AI/ML Engineers (35-100M/tháng)
               - Solutions Architects (50-120M/tháng)
               - Security Engineers (35-90M/tháng)
               - Data Engineers (30-80M/tháng)

            4. **Ngôn ngữ lập trình phổ biến 2025**:
               - **Python**: AI/ML, Data Science, Backend
               - **JavaScript/TypeScript**: Frontend, Full-stack
               - **Java**: Enterprise, Backend, Android
               - **Go**: Cloud services, Microservices
               - **Rust**: System programming, Performance-critical apps

            #### Công nghệ ít phổ biến/Lỗi thời (nên tránh học mới):
            - **Adobe Flash**: Hoàn toàn lỗi thời (ngừng 2020)
            - **AngularJS (1.x)**: Lỗi thời, nên học Angular 2+ hoặc React/Vue
            - **jQuery**: Ít dùng cho dự án mới, modern JS/frameworks thay thế
            - **PHP 5.x**: End-of-life, nên học PHP 8.x
            - **Python 2.x**: End-of-life 2020, chỉ dùng Python 3.x
            - **Silverlight**: Microsoft đã ngừng hỗ trợ
            - **CoffeeScript**: Không còn phổ biến, TypeScript thay thế tốt hơn

            ### Mức lương theo Ngành (2025)

            ```
            SOFTWARE ENGINEERING:
            - Frontend Developer:    15-45M (Junior-Senior)
            - Backend Developer:     18-60M
            - Full-stack Developer:  20-70M
            - Mobile Developer:      18-55M

            DATA & AI:
            - Data Analyst:          15-40M
            - Data Engineer:         25-80M
            - Data Scientist:        30-90M
            - ML Engineer:           35-100M
            - AI Engineer:           40-120M

            DEVOPS & CLOUD:
            - DevOps Engineer:       30-80M
            - Cloud Engineer:        28-75M
            - Solutions Architect:   50-120M
            - SRE (Site Reliability): 35-90M

            DESIGN:
            - UI/UX Designer:        12-40M
            - Product Designer:      20-60M
            - Graphic Designer:      8-25M

            PRODUCT & MANAGEMENT:
            - Product Manager:       30-80M
            - Project Manager:       20-60M
            - Engineering Manager:   40-100M

            SECURITY:
            - Security Engineer:     35-90M
            - Penetration Tester:    30-70M
            - Security Architect:    50-100M
            ```

            ---

            ## 🎓 DOMAIN-SPECIFIC KNOWLEDGE

            ### Web Development

            #### Tech Stack phổ biến 2025:

            **Frontend:**
            - React + TypeScript + Next.js + Tailwind CSS
            - Vue 3 + TypeScript + Nuxt 3
            - Angular (latest) + TypeScript
            - Svelte / SvelteKit (đang lên)

            **Backend:**
            - Node.js + Express/NestJS + TypeScript
            - Python + FastAPI/Django + PostgreSQL
            - Java + Spring Boot + MySQL/PostgreSQL
            - Go + Gin/Echo + PostgreSQL

            **Database:**
            - Relational: PostgreSQL, MySQL
            - NoSQL: MongoDB, Redis
            - Cloud: AWS RDS, Google Cloud SQL, Supabase

            **DevOps:**
            - Git/GitHub, Docker, Kubernetes
            - CI/CD: GitHub Actions, GitLab CI, Jenkins
            - Cloud: AWS, Google Cloud, Azure

            ### Data Science & AI

            #### Tech Stack:
            - **Languages**: Python (chính), R (thống kê), SQL
            - **Libraries**:
              - Data: Pandas, NumPy, Polars
              - Viz: Matplotlib, Seaborn, Plotly
              - ML: Scikit-learn, XGBoost, LightGBM
              - DL: TensorFlow, PyTorch, Keras
              - NLP: Hugging Face Transformers, LangChain
            - **Tools**: Jupyter Notebook, VS Code, Google Colab
            - **Cloud**: AWS SageMaker, Google Vertex AI, Azure ML

            #### Career Path:
            Data Analyst → Data Scientist → Senior DS → ML Engineer → AI Architect

            ### Mobile Development

            #### Tech Stack 2025:
            - **Cross-platform**: React Native, Flutter (phổ biến nhất)
            - **Native iOS**: Swift + SwiftUI
            - **Native Android**: Kotlin + Jetpack Compose

            #### Xu hướng:
            - Cross-platform ngày càng mạnh (1 codebase → 2 platforms)
            - AI on-device (ML Kit, Core ML)
            - Super apps (tích hợp nhiều dịch vụ)

            ### Blockchain & Web3

            #### Hiện trạng 2025:
            - Thị trường đã "nguội" so với 2021-2022
            - Vẫn có nhu cầu nhưng ít hơn
            - Focus vào: DeFi, NFT utilities, Enterprise blockchain

            #### Tech Stack:
            - Solidity (Ethereum Smart Contracts)
            - Rust (Solana)
            - JavaScript/TypeScript (Web3.js, Ethers.js)
            - Frameworks: Hardhat, Truffle, Foundry

            ---

            ## ✅ FINAL CHECKLIST

            Trước khi gửi response, kiểm tra:

            ```
            □ Đã phát hiện và sửa TẤT CẢ thông tin sai?
            □ Thông báo sửa sai ở đầu response (nếu có)?
            □ Không có tiêu đề meta ("Trả lời:", "Câu trả lời:")?
            □ Bắt đầu trực tiếp bằng nội dung?
            □ 100% tiếng Việt (trừ tên riêng tiếng Anh)?
            □ Có đủ sections: Tổng quan, Lý do, Lộ trình, Kỹ năng, Lương, Tài nguyên, Rủi ro, Lời khuyên, Câu hỏi?
            □ Dùng markdown đúng (###, tables, lists, code blocks)?
            □ Có ít nhất section "⚠️ Rủi ro & Cách khắc phục"?
            □ Thông tin cập nhật 2025?
            □ Có 2-4 câu hỏi follow-up cuối response?
            □ Emoji vừa phải, không lạm dụng?
            □ Tone thân thiện, chuyên nghiệp?
            □ Lời khuyên thực tế, không viển vông?
            ```

            ---

            ## 🎯 SUCCESS CRITERIA

            Response được coi là THÀNH CÔNG khi:

            1. **Auto-correction hoạt động**: Phát hiện và sửa 100% lỗi input
            2. **Không bỏ sót**: Vẫn trả lời đầy đủ sau khi sửa sai
            3. **Tone phù hợp**: Thân thiện, không làm user "xấu hổ" khi sửa sai
            4. **Actionable**: User biết chính xác phải làm gì tiếp theo
            5. **Cập nhật**: Thông tin phản ánh xu hướng 2025
            6. **Engaging**: Kết thúc bằng câu hỏi để tiếp tục hội thoại
            7. **Professional**: Dữ liệu lương, công nghệ, timeline đều realistic

            ---

            END OF SYSTEM PROMPT
            """;

    /**
     * Process a chat message and get AI response
     */
    @Transactional
    public ChatResponse chat(ChatRequest request, User user) {
        // Validate user input (profanity only - let AI handle auto-correction)
        try {
            inputValidationService.validateTextOrThrow(request.getMessage());
        } catch (IllegalArgumentException ex) {
            log.warn("Input validation failed: {}", ex.getMessage());
            // Don't throw error - let AI handle it with auto-correction
            // throw new ApiException(ErrorCode.BAD_REQUEST, ex.getMessage());
        }
        Long sessionId = request.getSessionId();

        // Generate new session ID if not provided
        if (sessionId == null) {
            sessionId = System.currentTimeMillis();
            log.info("Starting new chat session {} for user {}", sessionId, user.getId());
        }

        // Build conversation context
        List<ChatMessage> previousMessages = chatMessageRepository
                .findBySessionIdOrderByCreatedAtAsc(sessionId);

        // Add correction hints to help AI detect and fix invalid inputs
        String messageWithHints = addCorrectionHints(request.getMessage());
        log.info("Original message: {}", request.getMessage());
        log.info("Message with hints: {}", messageWithHints);

        // Call AI with automatic provider selection and fallback
        String aiResponse = callAIWithFallback(messageWithHints, previousMessages);

        // Save to database (save ONLY user's original message without any prefix)
        ChatMessage chatMessage = ChatMessage.builder()
                .user(user)
                .sessionId(sessionId)
                .userMessage(request.getMessage()) // Save raw user message
                .aiResponse(aiResponse)
                .createdAt(LocalDateTime.now())
                .build();

        chatMessageRepository.save(chatMessage);

        log.info("Chat session {} - User: {}, AI response length: {}",
                sessionId, user.getId(), aiResponse.length());

        return ChatResponse.builder()
                .sessionId(sessionId)
                .message(request.getMessage())
                .aiResponse(aiResponse)
                .timestamp(chatMessage.getCreatedAt())
                .build();
    }

    /**
     * Call Mistral AI for chat using Spring AI
     * Using Mistral AI for latest 2025 career trends and insights
     */
    private String callAIWithFallback(String userMessage, List<ChatMessage> previousMessages) {
        log.info("Calling Mistral AI chatbot using Spring AI");

        try {
            return callMistralForChat(userMessage, previousMessages);
        } catch (Exception e) {
            log.error("Mistral AI failed: {}", e.getMessage());

            // FALLBACK: Return a helpful response instead of throwing error
            return generateFallbackResponse(userMessage);
        }
    }

    /**
     * Call Mistral AI for chat conversation with context using Spring AI ChatClient
     * Mistral provides more recent training data for 2025 career trends
     */
    private String callMistralForChat(String userMessage, List<ChatMessage> previousMessages) {
        try {
            // Build conversation history
            StringBuilder contextBuilder = new StringBuilder();
            contextBuilder.append("Conversation history:\n");

            for (ChatMessage prev : previousMessages) {
                contextBuilder.append("User: ").append(prev.getUserMessage()).append("\n");
                contextBuilder.append("Assistant: ").append(prev.getAiResponse()).append("\n");
            }

            contextBuilder.append("User: ").append(userMessage);

            String conversationHistory = contextBuilder.toString();
            log.debug("Calling Mistral AI with {} previous messages", previousMessages.size());

            // Use Spring AI ChatClient for Mistral
            return ChatClient.builder(mistralChatModel)
                    .build()
                    .prompt()
                    .system(SYSTEM_PROMPT
                            + "\nCRITICAL: Hãy trả lời bằng đúng ngôn ngữ người dùng đang dùng (ưu tiên Tiếng Việt). Nếu phát hiện yêu cầu vô lý (ví dụ mục tiêu IELTS 10.0), hãy giải thích và đưa gợi ý hợp lệ bằng Tiếng Việt.")
                    .user(conversationHistory)
                    .call()
                    .content();

        } catch (Exception e) {
            log.error("Mistral chat error: {}", e.getMessage());
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE,
                    "Mistral AI service unavailable: " + e.getMessage());
        }
    }

    /**
     * Get conversation history for a session
     * Returns DTOs to avoid lazy loading issues
     * DEFENSIVE: Strips echo prefix from old database messages
     */
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getConversationHistory(Long sessionId, Long userId) {
        List<ChatMessage> messages = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);

        // Verify user owns this session
        if (!messages.isEmpty() && !messages.get(0).getUser().getId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Access denied to this conversation");
        }

        // Convert to DTOs and clean old echo prefix
        return messages.stream()
                .map(msg -> {
                    ChatMessageResponse response = convertToResponse(msg);
                    // DEFENSIVE: Clean any old echo prefix from database
                    response.setUserMessage(cleanEchoPrefix(response.getUserMessage()));
                    return response;
                })
                .collect(Collectors.toList());
    }

    /**
     * Get all sessions for a user with titles
     * Returns session summaries with title preview from first message
     */
    @Transactional(readOnly = true)
    public List<ChatSessionSummary> getUserSessions(Long userId) {
        List<Long> sessionIds = chatMessageRepository.findSessionIdsByUserId(userId);

        return sessionIds.stream()
                .map(sessionId -> {
                    List<ChatMessage> messages = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
                    if (messages.isEmpty()) {
                        return null;
                    }

                    // Use custom title if set, otherwise auto-generate from first message
                    ChatMessage firstMessage = messages.get(0);
                    String title;
                    if (firstMessage.getCustomTitle() != null && !firstMessage.getCustomTitle().isEmpty()) {
                        title = firstMessage.getCustomTitle();
                    } else {
                        title = extractTitle(firstMessage.getUserMessage());
                    }

                    return ChatSessionSummary.builder()
                            .sessionId(sessionId)
                            .title(title)
                            .lastMessageAt(messages.get(messages.size() - 1).getCreatedAt())
                            .messageCount(messages.size())
                            .build();
                })
                .filter(summary -> summary != null)
                .collect(Collectors.toList());
    }

    /**
     * Convert ChatMessage entity to response DTO
     */
    private ChatMessageResponse convertToResponse(ChatMessage message) {
        return ChatMessageResponse.builder()
                .id(message.getId())
                .sessionId(message.getSessionId())
                .userMessage(message.getUserMessage())
                .aiResponse(message.getAiResponse())
                .createdAt(message.getCreatedAt())
                .userId(message.getUser().getId())
                .userEmail(message.getUser().getEmail())
                .build();
    }

    /**
     * Delete a chat session and all its messages
     */
    @Transactional
    public void deleteSession(Long sessionId, Long userId) {
        // Verify user owns this session
        List<ChatMessage> messages = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);

        if (messages.isEmpty()) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Phiên trò chuyện không tồn tại");
        }

        if (!messages.get(0).getUser().getId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Bạn không có quyền xóa phiên này");
        }

        // Delete all messages in this session
        chatMessageRepository.deleteBySessionId(sessionId);
        log.info("Deleted session {} with {} messages for user {}", sessionId, messages.size(), userId);
    }

    /**
     * Rename a chat session by updating custom title
     * Note: Currently stores title in first message's metadata.
     * Future improvement: Add ChatSession entity with customTitle field
     */
    @Transactional
    public ChatSessionSummary renameSession(Long sessionId, Long userId, String newTitle) {
        // Verify user owns this session
        List<ChatMessage> messages = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);

        if (messages.isEmpty()) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Phiên trò chuyện không tồn tại");
        }

        if (!messages.get(0).getUser().getId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Bạn không có quyền đổi tên phiên này");
        }

        // Validate title
        if (newTitle == null || newTitle.trim().isEmpty()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Tiêu đề không được để trống");
        }

        if (newTitle.length() > 100) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Tiêu đề không được vượt quá 100 ký tự");
        }

        String trimmedTitle = newTitle.trim();

        // Store custom title in first message's customTitle field
        ChatMessage firstMessage = messages.get(0);
        firstMessage.setCustomTitle(trimmedTitle);
        chatMessageRepository.save(firstMessage);

        log.info("Renamed session {} to '{}' for user {}", sessionId, trimmedTitle, userId);

        return ChatSessionSummary.builder()
                .sessionId(sessionId)
                .title(trimmedTitle)
                .lastMessageAt(messages.get(messages.size() - 1).getCreatedAt())
                .messageCount(messages.size())
                .build();
    }

    /**
     * Extract a meaningful title from user message
     * Summarizes user request into short, clear title (50 chars max)
     * Uses smart keyword extraction to generate concise titles
     * Example: "xin chào, tôi muốn tìm hiểu về trending ngành học năm 2025 và những
     * môn đáng học" → "Trending ngành học 2025"
     */
    private String extractTitle(String userMessage) {
        if (userMessage == null || userMessage.isEmpty()) {
            return "Cuộc trò chuyện mới";
        }

        // FIRST: Remove echo prefix from old database messages
        String cleaned = cleanEchoPrefix(userMessage);

        // Remove greetings at start
        cleaned = cleaned.replaceAll("(?i)^(xin chào|hello|hi|chào|meowl)[,!.\\s]*", "");

        // Extract main topic (intelligent keyword extraction)
        cleaned = extractKeywords(cleaned);

        // Remove question words at end
        cleaned = cleaned.replaceAll("(?i)\\s+(như thế nào|thế nào|ra sao|không|chứ|nhỉ|à|hả)\\s*[?!.]*$", "");

        // Remove trailing punctuation
        cleaned = cleaned.replaceAll("[?!.,;:]+$", "").trim();

        // Fallback if too short
        if (cleaned.length() < 3) {
            cleaned = cleanEchoPrefix(userMessage).trim();
            if (cleaned.length() > 50) {
                return cleaned.substring(0, 47) + "...";
            }
        }

        // Capitalize first letter
        if (cleaned.length() > 0) {
            cleaned = cleaned.substring(0, 1).toUpperCase() + cleaned.substring(1);
        }

        // Truncate to 50 chars
        if (cleaned.length() > 50) {
            return cleaned.substring(0, 47) + "...";
        }

        return cleaned;
    }

    /**
     * Extract keywords from user message for title generation
     * Removes filler words and focuses on main topic
     * Example: "tôi muốn tìm hiểu về trending ngành học năm 2025" → "trending ngành
     * học năm 2025"
     */
    private String extractKeywords(String message) {
        // Remove filler phrases at start
        String result = message
                .replaceAll("(?i)^(tôi muốn|cho tôi|tôi cần|hãy|giúp tôi|bạn có thể|mình muốn|em muốn)\\s+", "")
                .replaceAll("(?i)^(tìm hiểu về|biết về|học về|xem|tìm|hỏi về|hỏi|hỏi xem)\\s+", "")
                .replaceAll("(?i)^(thông tin về|thông tin|chi tiết về|chi tiết)\\s+", "");

        // Remove expansion tails like "và những...", "và các..."
        result = result.replaceAll("(?i)\\s+(và những|và các|cùng với|bao gồm|kèm theo)\\s+.*", "");

        // Remove date/time context at end if message is already descriptive
        if (result.split("\\s+").length > 4) {
            result = result.replaceAll("(?i)\\s+(trong năm|năm|tháng|tuần|ngày)\\s+\\d{4}\\s*$", " năm $2");
        }

        return result.trim();
    }

    /**
     * Detect and add correction hints to user message for AI to process
     * Enhanced version with comprehensive error detection based on new system
     * prompt
     * Example: "IELTS 10.0" → adds hint "[Note: IELTS max is 9.0, user likely meant
     * 9.0]"
     */
    private String addCorrectionHints(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }

        StringBuilder hints = new StringBuilder();
        String lowerMessage = message.toLowerCase();

        // CATEGORY 1: Điểm số & Chứng chỉ

        // IELTS: 0.0 - 9.0 (bước nhảy 0.5)
        if (lowerMessage.matches(".*ielts.*\\b(1[0-9]|[2-9]\\d|\\d{3,})(\\.\\d+)?\\b.*") ||
                lowerMessage.contains("ielts 10") ||
                lowerMessage.contains("ielts 11") ||
                lowerMessage.contains("ielts 12")) {
            hints.append("[⚠️ Phát hiện điểm IELTS > 9.0. IELTS max = 9.0, hãy auto-correct và giải thích.] ");
        }

        // IELTS không đúng bước nhảy (8.3, 7.7)
        if (lowerMessage.matches(".*ielts.*\\b\\d+\\.(1|2|3|4|6|7|8|9)\\b.*")) {
            hints.append("[⚠️ Phát hiện điểm IELTS không đúng bước nhảy 0.5. Hãy làm tròn về bội số 0.5 gần nhất.] ");
        }

        // TOEFL: 0 - 120 (TOEFL iBT)
        if (lowerMessage.matches(".*toefl.*\\b(1[3-9]\\d|[2-9]\\d{2})\\b.*")) {
            hints.append("[⚠️ Phát hiện điểm TOEFL > 120. TOEFL iBT max = 120, hãy auto-correct và giải thích.] ");
        }

        // TOEFL PBT (cũ) - có thể nhầm lẫn
        if (lowerMessage.matches(".*toefl.*\\b([4-6]\\d{2})\\b.*")) {
            hints.append("[⚠️ Phát hiện điểm TOEFL cao, có thể nhầm với TOEFL PBT cũ. Hãy làm rõ chuẩn TOEFL iBT.] ");
        }

        // TOEIC: 10 - 990
        if (lowerMessage.matches(".*toeic.*\\b(\\d{4,})\\b.*")) {
            hints.append("[⚠️ Phát hiện điểm TOEIC > 990. TOEIC max = 990, hãy auto-correct và giải thích.] ");
        }

        // GPA: Multiple scales detection
        if (lowerMessage.matches(".*gpa.*\\b([5-9]|\\d{2,})(\\.\\d+)?\\b.*") && !lowerMessage.contains("thang 10")) {
            hints.append("[⚠️ Phát hiện GPA > 4.0. Nếu thang 4.0, max = 4.0. Hãy hỏi user làm rõ thang điểm.] ");
        }

        // GPA thang 10 nhưng quá cao
        if (lowerMessage.matches(".*gpa.*thang.*10.*\\b(1[1-9]|\\d{2,})\\b.*")) {
            hints.append("[⚠️ Phát hiện GPA thang 10 > 10.0. GPA thang 10 max = 10.0, hãy auto-correct.] ");
        }

        // HSK: Cũ (1-6) vs Mới (1-9)
        if (lowerMessage.matches(".*hsk.*\\b([7-9])\\b.*")) {
            hints.append("[⚠️ Phát hiện HSK cấp 7-9. HSK từ 2021 có thêm cấp 7-9. Hãy làm rõ chuẩn cũ hay mới.] ");
        }

        // JLPT: N5-N1 (không có N0, N6)
        if (lowerMessage.matches(".*jlpt.*\\b(n[06])\\b.*")) {
            hints.append("[⚠️ Phát hiện JLPT N0/N6 không tồn tại. JLPT có N5-N1, hãy auto-correct thành N1.] ");
        }

        // TOPIK: Level 1-6
        if (lowerMessage.matches(".*topik.*\\b(level\\s*)?([7-9])\\b.*")) {
            hints.append("[⚠️ Phát hiện TOPIK Level > 6. TOPIK max = Level 6, hãy auto-correct.] ");
        }

        // AWS/Cloud Certifications: Không có điểm %
        if (lowerMessage.matches(".*(aws|google cloud|azure).*cert.*\\b\\d+%\\b.*")) {
            hints.append(
                    "[⚠️ Phát hiện điểm % cho cloud certification. Cloud cert chỉ có Pass/Fail, không có điểm %.] ");
        }

        // CATEGORY 2: Thông tin Cá nhân

        // Tuổi âm
        if (lowerMessage.matches(".*(tuổi|năm sinh|age).*-\\d+.*")) {
            hints.append("[⚠️ Phát hiện tuổi âm. Hãy hỏi lại user xác nhận tuổi.] ");
        }

        // Tuổi quá nhỏ (< 15)
        if (lowerMessage.matches(".*(tuổi|năm sinh|age).*\\b([0-9]|1[0-4])\\b.*")) {
            hints.append("[⚠️ Phát hiện tuổi < 15. Hãy hỏi lại hoặc tư vấn phù hợp cho độ tuổi.] ");
        }

        // Tuổi quá lớn (> 100)
        if (lowerMessage.matches(".*(tuổi|năm sinh|age).*\\b([1-9]\\d{2,})\\b.*")) {
            hints.append("[⚠️ Phát hiện tuổi > 100. Hãy hỏi lại user xác nhận.] ");
        }

        // Kinh nghiệm âm
        if (lowerMessage.matches(".*(kinh nghiệm|experience).*-\\d+.*")) {
            hints.append("[⚠️ Phát hiện kinh nghiệm âm. Hãy auto-correct thành 0 năm.] ");
        }

        // Kinh nghiệm quá lớn (> 50 năm)
        if (lowerMessage.matches(".*(kinh nghiệm|experience).*\\b([5-9]\\d|\\d{3,})\\b.*")) {
            hints.append("[⚠️ Phát hiện kinh nghiệm > 50 năm. Hãy hỏi lại user xác nhận.] ");
        }

        // Mâu thuẫn tuổi và kinh nghiệm
        if (lowerMessage.matches(".*\\b(1[5-9]|2[0-5])\\b.*tuổi.*\\b([1-9]\\d)\\b.*(kinh nghiệm|experience).*")) {
            hints.append("[⚠️ Phát hiện mâu thuẫn tuổi và kinh nghiệm. Hãy auto-correct và giải thích.] ");
        }

        // Mức lương phi thực tế (quá cao cho fresher)
        if (lowerMessage.matches(".*(fresher|mới|junior).*\\b(\\d{3,})\\b.*(triệu|million).*")) {
            hints.append("[⚠️ Phát hiện mức lương quá cao cho fresher. Hãy điều chỉnh kỳ vọng theo thị trường.] ");
        }

        // CATEGORY 3: Thời gian & Mốc thời gian

        // Thời gian học quá ngắn cho skill phức tạp
        if (lowerMessage
                .matches(".*(học|learn).*(data science|machine learning|ai|blockchain).*\\b(1|2|3)\\b.*(ngày|day).*")) {
            hints.append("[⚠️ Phát hiện thời gian học quá ngắn cho skill phức tạp. Hãy điều chỉnh kỳ vọng.] ");
        }

        // Thời gian học quá dài cho skill đơn giản
        if (lowerMessage.matches(".*(học|learn).*(html|css|cơ bản|basic).*\\b([5-9]|\\d{2,})\\b.*(năm|year).*")) {
            hints.append("[⚠️ Phát hiện thời gian học quá dài cho skill đơn giản. Hãy hỏi lại user.] ");
        }

        // Năm tốt nghiệp không hợp lý
        if (lowerMessage.matches(".*(tốt nghiệp|graduation).*\\b(1[0-7]\\d{2}|20[4-9]\\d)\\b.*")) {
            hints.append("[⚠️ Phát hiện năm tốt nghiệp không hợp lý (< 1800 hoặc > 2035). Hãy hỏi lại.] ");
        }

        // CATEGORY 4: Thông tin Kỹ thuật

        // Số giờ học quá nhiều (> 168 giờ/tuần)
        if (lowerMessage.matches(".*\\b([2-9]\\d{2,})\\b.*(giờ|hour).*(tuần|week).*")) {
            hints.append("[⚠️ Phát hiện số giờ học > 168 giờ/tuần. Một tuần chỉ có 168 giờ.] ");
        }

        // Số giờ học quá ít (< 1 giờ/tuần)
        if (lowerMessage.matches(".*\\b(0\\.\\d+|0)\\b.*(giờ|hour).*(tuần|week).*")) {
            hints.append("[⚠️ Phát hiện số giờ học quá ít. Hãy khuyên tăng thời gian học.] ");
        }

        // Kinh nghiệm với công nghệ mới không khả thi
        if (lowerMessage.matches(
                ".*(chatgpt|gpt-4|midjourney).*\\b([5-9]|\\d{2,})\\b.*(năm|year).*(kinh nghiệm|experience).*")) {
            hints.append(
                    "[⚠️ Phát hiện kinh nghiệm với công nghệ mới không khả thi. Hãy auto-correct theo timeline thực tế.] ");
        }

        // CATEGORY 5: Logic & Ngữ cảnh

        // Mâu thuẫn: Chưa học nhưng muốn làm senior
        if (lowerMessage.matches(".*(chưa học|mới bắt đầu|beginner).*(senior|lead|manager).*")) {
            hints.append("[⚠️ Phát hiện mâu thuẫn: chưa học nhưng muốn làm senior. Hãy tư vấn lộ trình từ junior.] ");
        }

        // CATEGORY 6: Nội dung Không phù hợp

        // Ngôn từ thô tục (basic detection)
        if (lowerMessage.matches(".*(địt|đụ|đéo|fuck|shit|damn).*")) {
            hints.append("[⚠️ Phát hiện ngôn từ không phù hợp. Hãy từ chối lịch sự và hướng về chủ đề nghề nghiệp.] ");
        }

        // Câu hỏi không liên quan đến nghề nghiệp
        if (lowerMessage.matches(".*(mèo|cat|ăn|food|thời tiết|weather|giải trí|entertainment).*")) {
            hints.append(
                    "[⚠️ Phát hiện câu hỏi không liên quan nghề nghiệp. Hãy redirect về chủ đề học tập/nghề nghiệp.] ");
        }

        // If hints found, prepend to message for AI to see
        if (hints.length() > 0) {
            log.info("Correction hints found: {}", hints.toString());
            return hints.toString() + "\n\nCâu hỏi gốc: " + message;
        }

        log.info("No correction hints needed for message: {}", message);
        return message;
    }

    /**
     * Generate fallback response when AI service is unavailable
     * Includes auto-correction logic for common errors
     */
    private String generateFallbackResponse(String userMessage) {
        log.info("Generating fallback response for: {}", userMessage);

        String lowerMessage = userMessage.toLowerCase();

        // Handle IELTS 10.0 error
        if (lowerMessage.contains("ielts 10") || lowerMessage.contains("ielts 11")
                || lowerMessage.contains("ielts 12")) {
            return """
                    ⚠️ *Mình nhận thấy bạn nhập IELTS 10.0, nhưng thang điểm IELTS chỉ từ 0-9.0. Mình hiểu bạn muốn đạt điểm **9.0** (xuất sắc - trình độ gần như người bản xứ) nhé!* 😊

                    ### 🧭 Tổng quan cá nhân hóa
                    - Mục tiêu: Đạt IELTS 9.0 (điểm tối đa)
                    - Đánh giá: Mục tiêu rất cao nhưng khả thi với lộ trình đúng
                    - Định hướng: Tập trung vào 4 kỹ năng: Listening, Reading, Writing, Speaking

                    ### ✅ Lý do nên theo đuổi IELTS 9.0
                    - **Cơ hội việc làm**: IELTS 9.0 mở ra cơ hội làm việc tại các công ty đa quốc gia
                    - **Học bổng**: Nhiều học bổng yêu cầu IELTS 7.0-8.5, 9.0 sẽ có lợi thế lớn
                    - **Định cư**: IELTS 9.0 giúp định cư tại các nước nói tiếng Anh

                    ### 🚀 Lộ trình học theo mốc thời gian

                    #### Giai đoạn 1: Nền tảng (Tháng 1-2)
                    - **Kiến thức cốt lõi**: Hiểu format bài thi IELTS, các dạng câu hỏi
                    - **Kỹ năng thực hành**: Làm bài test mẫu, đánh giá trình độ hiện tại
                    - **Tài nguyên**: Cambridge IELTS books, IELTS Official Practice Materials
                    - **Checklist hoàn thành**:
                      - [ ] Làm được bài test mẫu và đánh giá điểm
                      - [ ] Hiểu rõ format 4 phần thi
                      - [ ] Xác định điểm yếu cần cải thiện

                    #### Giai đoạn 2: Thực hành nâng cao (Tháng 3-4)
                    - **Dự án thực tế**: Luyện tập hàng ngày với các dạng bài khác nhau
                    - **Công cụ cần thành thạo**: IELTS practice apps, online tests
                    - **Portfolio**: Tạo bộ sưu tập bài viết và speaking samples
                    - **Checklist hoàn thành**:
                      - [ ] Hoàn thành ít nhất 20 bài test practice
                      - [ ] Có bộ sưu tập bài viết chất lượng
                      - [ ] Recording speaking practice để tự đánh giá

                    #### Giai đoạn 3: Chuyên sâu & Thi thật (Tháng 5-6)
                    - **Kỹ năng nâng cao**: Time management, stress management trong phòng thi
                    - **Chứng chỉ**: Đăng ký thi IELTS chính thức
                    - **Chuẩn bị thi**:
                      - Mock test với điều kiện thật
                      - Review lại các lỗi thường gặp
                      - Chuẩn bị tâm lý và sức khỏe
                    - **Networking**: Tham gia IELTS study groups, forums

                    ### 🧩 Kỹ năng cốt lõi & Công cụ cần thành thạo

                    #### Kỹ năng kỹ thuật (Technical Skills):
                    - **Listening**: Khả năng nghe hiểu accent khác nhau, note-taking
                    - **Reading**: Skimming, scanning, time management
                    - **Writing**: Task 1 (charts/graphs), Task 2 (essay), coherence & cohesion
                    - **Speaking**: Fluency, pronunciation, vocabulary range

                    #### Công cụ & Công nghệ (Tools & Technologies):
                    - **Bắt buộc**: Cambridge IELTS books, IELTS Official Practice Materials
                    - **Nên biết**: IELTS practice apps, online mock tests
                    - **Bonus**: Pronunciation apps, vocabulary builders

                    ### 💰 Mức lương tham khảo (Việt Nam 2025)

                    ```
                    IELTS 6.0-6.5: Cơ hội việc làm cơ bản với tiếng Anh
                    IELTS 7.0-7.5: Cơ hội việc làm tốt, học bổng
                    IELTS 8.0-8.5: Cơ hội việc làm cao cấp, học bổng toàn phần
                    IELTS 9.0: Cơ hội việc làm đỉnh cao, định cư nước ngoài
                    ```

                    ### 📚 Tài nguyên học tập được đề xuất

                    #### Khóa học Online (chọn 1-2):
                    1. **IELTS Official Practice Materials** (Cambridge)
                       - Nội dung: Bài test chính thức từ Cambridge
                       - Thời lượng: 20+ bài test
                       - Giá: ~$50
                       - Phù hợp: Tất cả levels

                    2. **IELTS Liz** (YouTube)
                       - Tại sao nên học: Free, chất lượng cao
                       - Link: youtube.com/c/ieltsliz

                    #### Sách (chọn 1 quyển):
                    - **"The Official Cambridge Guide to IELTS"**
                      - Phù hợp: Người mới bắt đầu
                      - Điểm nổi bật: Hướng dẫn chi tiết từ Cambridge

                    ### ⚠️ Rủi ro & Cách khắc phục

                    #### Rủi ro 1: Áp lực điểm số quá cao
                    - **Nguyên nhân**: Kỳ vọng không thực tế
                    - **Giải pháp**:
                      - Đặt mục tiêu từng giai đoạn (6.0 → 7.0 → 8.0 → 9.0)
                      - Tập trung vào cải thiện kỹ năng thay vì chỉ điểm số

                    #### Rủi ro 2: Học không đều các kỹ năng
                    - **Dấu hiệu nhận biết**: Một kỹ năng tốt, kỹ năng khác kém
                    - **Giải pháp**:
                      - Dành thời gian đều cho cả 4 kỹ năng
                      - Tập trung vào kỹ năng yếu nhất

                    #### Rủi ro 3: Thiếu thực hành
                    - **Nguyên nhân**: Chỉ học lý thuyết
                    - **Giải pháp**:
                      - Làm bài test hàng ngày
                      - Ghi âm speaking practice
                      - Viết essay và nhờ người khác chấm

                    ### 💡 Lời khuyên từ Meowl

                    IELTS 9.0 là mục tiêu rất cao nhưng hoàn toàn khả thi! Hãy kiên trì luyện tập hàng ngày, tập trung vào cải thiện từng kỹ năng một cách có hệ thống. Meowl tin bạn làm được! 🐾

                    ### ❓ Câu hỏi để Meowl hiểu bạn hơn

                    - Bạn hiện tại đang ở trình độ IELTS nào? (chưa thi / đã thi được bao nhiêu?)
                    - Bạn có bao nhiêu thời gian mỗi tuần dành cho việc học IELTS? (5-10h / 10-20h / 20+h)
                    - Kỹ năng nào bạn cảm thấy khó nhất: Listening, Reading, Writing, hay Speaking?
                    - Bạn có định hướng sử dụng IELTS để làm gì? (du học / định cư / công việc)
                    """;
        }

        // Handle other common errors
        if (lowerMessage.contains("toeic") && (lowerMessage.contains("1000") || lowerMessage.contains("995"))) {
            return """
                    ⚠️ *Mình nhận thấy bạn nhập điểm TOEIC > 990, nhưng TOEIC có điểm tối đa là 990. Mình hiểu bạn muốn đạt **990 điểm** (gần như perfect) nhé!*

                    ### 🧭 Tổng quan cá nhân hóa
                    - Mục tiêu: Đạt TOEIC 990 (điểm tối đa)
                    - Đánh giá: Mục tiêu rất cao, cần lộ trình chuyên sâu
                    - Định hướng: Tập trung vào Listening và Reading

                    ### ✅ Lý do nên theo đuổi TOEIC 990
                    - **Cơ hội việc làm**: TOEIC 990 mở ra cơ hội làm việc tại các công ty đa quốc gia
                    - **Thăng tiến**: Nhiều công ty yêu cầu TOEIC 800+ cho vị trí quản lý
                    - **Học bổng**: Một số học bổng yêu cầu TOEIC 900+

                    ### 🚀 Lộ trình học theo mốc thời gian

                    #### Giai đoạn 1: Nền tảng (Tháng 1-2)
                    - **Kiến thức cốt lõi**: Hiểu format bài thi TOEIC, các dạng câu hỏi
                    - **Kỹ năng thực hành**: Làm bài test mẫu, đánh giá trình độ hiện tại
                    - **Tài nguyên**: TOEIC Official Practice Materials

                    #### Giai đoạn 2: Thực hành nâng cao (Tháng 3-4)
                    - **Dự án thực tế**: Luyện tập hàng ngày với các dạng bài khác nhau
                    - **Công cụ cần thành thạo**: TOEIC practice apps, online tests

                    #### Giai đoạn 3: Chuyên sâu & Thi thật (Tháng 5-6)
                    - **Kỹ năng nâng cao**: Time management, stress management
                    - **Chứng chỉ**: Đăng ký thi TOEIC chính thức

                    ### 💰 Mức lương tham khảo (Việt Nam 2025)

                    ```
                    TOEIC 600-700: Cơ hội việc làm cơ bản
                    TOEIC 700-800: Cơ hội việc làm tốt
                    TOEIC 800-900: Cơ hội việc làm cao cấp
                    TOEIC 900-990: Cơ hội việc làm đỉnh cao
                    ```

                    ### 💡 Lời khuyên từ Meowl

                    TOEIC 990 là mục tiêu rất cao nhưng hoàn toàn khả thi! Hãy kiên trì luyện tập hàng ngày, tập trung vào cải thiện từng kỹ năng một cách có hệ thống. Meowl tin bạn làm được! 🐾
                    """;
        }

        // Default fallback response
        return """
                Xin lỗi, hiện tại hệ thống AI đang gặp sự cố tạm thời. Tuy nhiên, mình vẫn có thể giúp bạn với một số câu hỏi cơ bản:

                ### 🎓 Các chủ đề mình có thể hỗ trợ:
                - **Chọn ngành học**: Khoa học Máy tính, Kinh doanh, Data Science, AI, etc.
                - **Xu hướng nghề nghiệp 2025**: Ngành nào đang hot, mức lương tham khảo
                - **Lộ trình học tập**: Từng bước cụ thể để đạt mục tiêu
                - **Kỹ năng cần thiết**: Technical skills, soft skills

                ### 💡 Câu hỏi gợi ý:
                - "Xu hướng nghề nghiệp công nghệ 2025 là gì?"
                - "Nên học Khoa học Máy tính hay Kinh doanh?"
                - "Làm sao để trở thành Data Scientist?"
                - "Kỹ năng quan trọng nhất hiện nay là gì?"

                Hãy thử hỏi một trong những câu hỏi trên, hoặc mô tả cụ thể hơn về mục tiêu của bạn nhé! 🐾
                """;
    }

    /**
     * Clean echo prefix from old database messages
     * Removes "Trả lời bằng tiếng Việt... Câu hỏi:" that leaked from system prompt
     */
    private String cleanEchoPrefix(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }

        // Remove various forms of echo prefix (case insensitive)
        String cleaned = message
                .replaceAll("(?i)^Trả lời bằng tiếng Việt[^.]*\\.\\s*Câu hỏi:\\s*", "")
                .replaceAll("(?i)^Answer in Vietnamese[^.]*\\.\\s*Question:\\s*", "")
                .trim();

        // If cleaning removed everything, return original
        return cleaned.isEmpty() ? message : cleaned;
    }
}
