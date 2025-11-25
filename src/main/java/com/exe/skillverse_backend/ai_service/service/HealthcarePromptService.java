package com.exe.skillverse_backend.ai_service.service;

import org.springframework.stereotype.Service;

/**
 * Healthcare domain expert prompt service.
 * Contains prompts for healthcare, medical, and health-related careers.
 */
@Service
public class HealthcarePromptService extends BaseExpertPromptService {

    public String getPrompt(String industry, String role) {
        if (industry == null || role == null) {
            return null;
        }

        String normalizedIndustry = industry.toLowerCase();
        String normalizedRole = role.toLowerCase();

        // Medical Practice
        boolean isMedical = normalizedIndustry.contains("medical") || normalizedIndustry.contains("y học") ||
                           normalizedIndustry.contains("bác sĩ") || normalizedIndustry.contains("doctor") ||
                           normalizedIndustry.contains("practice") || normalizedIndustry.contains("chuyên khoa");

        if (isMedical) {
            if (normalizedRole.contains("general doctor") || normalizedRole.contains("bác sĩ đa khoa")) return getGeneralDoctorPrompt();
            if (normalizedRole.contains("specialist doctor") || normalizedRole.contains("bác sĩ chuyên khoa")) return getSpecialistDoctorPrompt();
            if (normalizedRole.contains("pediatrician") || normalizedRole.contains("nhi khoa")) return getPediatricianPrompt();
            if (normalizedRole.contains("cardiologist") || normalizedRole.contains("tim mạch")) return getCardiologistPrompt();
            if (normalizedRole.contains("dermatologist") || normalizedRole.contains("da liễu")) return getDermatologistPrompt();
            if (normalizedRole.contains("radiologist") || normalizedRole.contains("chẩn đoán hình ảnh")) return getRadiologistPrompt();
            if (normalizedRole.contains("surgeon") || normalizedRole.contains("phẫu thuật")) return getSurgeonPrompt();
        }

        // Nursing & Clinical Care
        boolean isNursing = normalizedIndustry.contains("nursing") || normalizedIndustry.contains("điều dưỡng") ||
                           normalizedIndustry.contains("clinical care") || normalizedIndustry.contains("chăm sóc bệnh nhân") ||
                           normalizedIndustry.contains("nurse") || normalizedIndustry.contains("y tá");

        if (isNursing) {
            if (normalizedRole.contains("registered nurse") || normalizedRole.contains("điều dưỡng")) return getRegisteredNursePrompt();
            if (normalizedRole.contains("assistant nurse") || normalizedRole.contains("y tá")) return getAssistantNursePrompt();
            if (normalizedRole.contains("clinical care specialist")) return getClinicalCareSpecialistPrompt();
            if (normalizedRole.contains("icu nurse")) return getIcuNursePrompt();
            if (normalizedRole.contains("emergency care nurse")) return getEmergencyCareNursePrompt();
        }

        // Medical Technology & Laboratory
        boolean isMedTech = normalizedIndustry.contains("medical technology") || normalizedIndustry.contains("xét nghiệm") ||
                           normalizedIndustry.contains("thiết bị y tế") || normalizedIndustry.contains("laboratory") ||
                           normalizedIndustry.contains("radiologic") || normalizedIndustry.contains("ultrasound") ||
                           normalizedIndustry.contains("pharmacy technician") || normalizedIndustry.contains("biomedical");

        if (isMedTech) {
            if (normalizedRole.contains("medical laboratory technician") || normalizedRole.contains("ktv xét nghiệm")) return getMedicalLaboratoryTechnicianPrompt();
            if (normalizedRole.contains("radiologic technologist") || normalizedRole.contains("ktv chẩn đoán hình ảnh")) return getRadiologicTechnologistPrompt();
            if (normalizedRole.contains("ultrasound technician") || normalizedRole.contains("ktv siêu âm")) return getUltrasoundTechnicianPrompt();
            if (normalizedRole.contains("pharmacy technician")) return getPharmacyTechnicianPrompt();
            if (normalizedRole.contains("biomedical engineer") || normalizedRole.contains("ktv thiết bị y tế")) return getBiomedicalEngineerPrompt();
        }

        // Pharmacy & Pharmaceutical
        boolean isPharmacy = normalizedIndustry.contains("pharmacy") || normalizedIndustry.contains("dược") ||
                            normalizedIndustry.contains("pharmaceutical") || normalizedIndustry.contains("dược sĩ") ||
                            normalizedIndustry.contains("drug") || normalizedIndustry.contains("thuốc");

        if (isPharmacy) {
            if (normalizedRole.contains("pharmacist") || normalizedRole.contains("dược sĩ")) return getPharmacistPrompt();
            if (normalizedRole.contains("clinical pharmacist")) return getClinicalPharmacistPrompt();
            if (normalizedRole.contains("pharmacy assistant")) return getPharmacyAssistantPrompt();
            if (normalizedRole.contains("pharmaceutical sales representative")) return getPharmaceuticalSalesRepresentativePrompt();
        }

        // Mental Health & Psychology
        boolean isMentalHealth = normalizedIndustry.contains("mental health") || normalizedIndustry.contains("sức khỏe tinh thần") ||
                                normalizedIndustry.contains("psychology") || normalizedIndustry.contains("tâm lý") ||
                                normalizedIndustry.contains("psychologist") || normalizedIndustry.contains("psychotherapist") ||
                                normalizedIndustry.contains("counselor") || normalizedIndustry.contains("therapist");

        if (isMentalHealth) {
            if (normalizedRole.contains("psychologist") || normalizedRole.contains("chuyên gia tâm lý")) return getPsychologistPrompt();
            if (normalizedRole.contains("psychotherapist") || normalizedRole.contains("nhà trị liệu tâm lý")) return getPsychotherapistPrompt();
            if (normalizedRole.contains("school counselor") || normalizedRole.contains("cố vấn học đường")) return getSchoolCounselorPrompt();
            if (normalizedRole.contains("mental health counselor") || normalizedRole.contains("cố vấn sức khỏe tinh thần")) return getMentalHealthCounselorPrompt();
            if (normalizedRole.contains("behavioral therapist") || normalizedRole.contains("nhà trị liệu hành vi")) return getBehavioralTherapistPrompt();
        }

        // Public Health & Fitness & Nutrition
        boolean isPublicHealth = normalizedIndustry.contains("public health") || normalizedIndustry.contains("sức khỏe cộng đồng") ||
                                normalizedIndustry.contains("nutrition") || normalizedIndustry.contains("dinh dưỡng") ||
                                normalizedIndustry.contains("fitness") || normalizedIndustry.contains("pt") ||
                                normalizedIndustry.contains("health education") || normalizedIndustry.contains("giáo dục sức khỏe") ||
                                normalizedIndustry.contains("occupational therapist") || normalizedIndustry.contains("trị liệu phục hồi chức năng") ||
                                normalizedIndustry.contains("speech therapist") || normalizedIndustry.contains("trị liệu ngôn ngữ");

        if (isPublicHealth) {
            if (normalizedRole.contains("public health specialist") || normalizedRole.contains("chuyên gia sức khỏe cộng đồng")) return getPublicHealthSpecialistPrompt();
            if (normalizedRole.contains("nutritionist") || normalizedRole.contains("chuyên gia dinh dưỡng")) return getNutritionistPrompt();
            if (normalizedRole.contains("fitness coach") || normalizedRole.contains("pt") || normalizedRole.contains("personal trainer")) return getFitnessCoachPrompt();
            if (normalizedRole.contains("health education specialist") || normalizedRole.contains("chuyên gia giáo dục sức khỏe")) return getHealthEducationSpecialistPrompt();
            if (normalizedRole.contains("occupational therapist") || normalizedRole.contains("trị liệu phục hồi chức năng")) return getOccupationalTherapistPrompt();
            if (normalizedRole.contains("speech therapist") || normalizedRole.contains("trị liệu ngôn ngữ")) return getSpeechTherapistPrompt();
        }

        return null;
    }

    // --- I. Medical Practice (Y học – Bác sĩ) ---

    public String getGeneralDoctorPrompt() {
        return getBaseExpertPersona() + """
            
            ## 🩺 LĨNH VỤC: GENERAL DOCTOR (BÁC SĨ ĐA KHOA)
            
            ### 🧠 KIẾN THỨC TRỌNG TÂM:
            1. **General Medicine**: Kiến thức rộng về các bệnh thông thường, chẩn đoán sơ bộ.
            2. **Clinical Skills**: Khám bệnh, lấy bệnh sử, các kỹ thuật cơ bản.
            3. **Pharmacology**: Kiến thức về thuốc, chỉ định, chống chỉ định, tác dụng phụ.
            4. **Preventive Care**: Tư vấn phòng bệnh, tiêm chủng, sức khỏe cộng đồng.
            5. **Patient Communication**: Giao tiếp với bệnh nhân, giải thích bệnh tình.
            
            ### 🚀 LỘ TRÌNH TƯ VẤN:
            - **Resident Doctor**: Bác sĩ nội trú tại bệnh viện.
            - **General Practitioner**: Mở phòng khám riêng hoặc làm tại trạm y tế.
            - **Family Doctor**: Bác sĩ gia đình, chăm sóc sức khỏe toàn diện.
            
            ### ⚠️ LƯU Ý:
            - Bác sĩ đa khoa là "cánh cửa đầu tiên" của hệ thống y tế.
            - Cần cập nhật kiến thức liên tục do y học phát triển nhanh.
            """;
    }

    public String getSpecialistDoctorPrompt() {
        return getBaseExpertPersona() + """
            
            ## 🏥 LĨNH VỤC: SPECIALIST DOCTOR (BÁC SĨ CHUYÊN KHOA)
            
            ### 🧠 KIẾN THỨC TRỌNG TÂM:
            1. **Specialty Knowledge**: Chuyên sâu về một lĩnh vực (tim mạch, tiêu hóa, nội tiết...).
            2. **Advanced Diagnostics**: Các xét nghiệm chuyên sâu, chẩn đoán phức tạp.
            3. **Treatment Protocols**: Phác đồ điều trị chuyên ngành, thuốc đặc trị.
            4. **Research Skills**: Đọc nghiên cứu y khoa, tham gia hội thảo.
            5. **Interdisciplinary Collaboration**: Phối hợp với các chuyên khoa khác.
            
            ### 🚀 LỘ TRÌNH TƯ VẤN:
            - **Specialist Resident**: Bác sĩ nội trú chuyên khoa.
            - **Specialist Doctor**: Bác sĩ chuyên khoa tại bệnh viện lớn.
            - **Department Head**: Trưởng khoa, chuyên gia hàng đầu.
            
            ### ⚠️ LƯU Ý:
            - Đòi hỏi 6-7 năm đào tạo chuyên sâu sau đại học.
            - Cân bằng giữa chuyên môn cao và kỹ năng giao tiếp.
            """;
    }

    public String getPediatricianPrompt() {
        return getBaseExpertPersona() + """
            
            ## 👶 LĨNH VỤC: PEDIATRICIAN (BÁC SĨ NHI KHOA)
            
            ### 🧠 KIẾN THỨC TRỌNG TÂM:
            1. **Child Development**: Sự phát triển thể chất và tinh thần của trẻ.
            2. **Pediatric Diseases**: Bệnh đặc trưng của trẻ em, vaccin.
            3. **Neonatology**: Chăm sóc trẻ sơ sinh, trẻ non tháng.
            4. **Child Psychology**: Hiểu tâm lý trẻ, giao tiếp với phụ huynh.
            5. **Pediatric Nutrition**: Dinh dưỡng cho trẻ theo từng giai đoạn.
            
            ### 🚀 LỘ TRÌNH TƯ VẤN:
            - **Pediatric Resident**: Bác sĩ nội trú nhi khoa.
            - **Hospital Pediatrician**: Bác sĩ nhi khoa tại bệnh viện.
            - **Private Pediatrician**: Mở phòng khám nhi riêng.
            
            ### ⚠️ LƯU Ý:
            - Cần sự kiên nhẫn và yêu trẻ vô điều kiện.
            - Giao tiếp với cả trẻ em và phụ huynh là kỹ năng quan trọng.
            """;
    }

    public String getCardiologistPrompt() {
        return getBaseExpertPersona() + """
            
            ## ❤️ LĨNH VỤC: CARDIOLOGIST (BÁC SĨ TIM MẠCH)
            
            ### 🧠 KIẾN THỨC TRỌNG TÂM:
            1. **Cardiovascular Anatomy**: Giải phẫu và sinh lý hệ tim mạch.
            2. **Cardiac Diagnostics**: ECG, Echo, Holter, stress test.
            3. **Interventional Cardiology**: Stent, angioplasty, pacemaker.
            4. **Heart Diseases**: Suy tim, nhịp tim, bệnh mạch vành.
            5. **Preventive Cardiology**: Phòng ngừa bệnh tim mạch, tư vấn lifestyle.
            
            ### 🚀 LỘ TRÌNH TƯ VẤN:
            - **Cardiology Fellow**: Bác sĩ chuyên khoa tim mạch.
            - **Interventional Cardiologist**: Chuyên can thiệp tim mạch.
            - **Cardiac Electrophysiologist**: Chuyên về điện sinh lý tim.
            
            ### ⚠️ LƯU Ý:
            - Tim mạch là ngành "nóng" với áp lực cao và kỹ thuật phức tạp.
            - Cập nhật liên tục về các kỹ thuật can thiệp mới.
            """;
    }

    public String getDermatologistPrompt() {
        return getBaseExpertPersona() + """
            
            ## 🧴 LĨNH VỤC: DERMATOLOGIST (BÁC SĨ DA LIỄU)
            
            ### 🧠 KIẾN THỨC TRỌNG TÂM:
            1. **Dermatology**: Bệnh về da, tóc, móng, niêm mạc.
            2. **Cosmetic Dermatology**: Trẻ hóa da, laser, filler, botox.
            3. **Dermatologic Surgery**: Phẫu thuật da, cắt u, nốt ruồi.
            4. **Allergy & Immunology**: Dị ứng, bệnh miễn dịch da.
            5. **Dermatopathology**: Giải phẫu bệnh da.
            
            ### 🚀 LỘ TRÌNH TƯ VẤN:
            - **Dermatology Resident**: Bác sĩ nội trú da liễu.
            - **Medical Dermatologist**: Chuyên điều trị bệnh da.
            - **Cosmetic Dermatologist**: Chuyên thẩm mỹ da.
            
            ### ⚠️ LƯU Ý:
            - Kết hợp giữa y học và thẩm mỹ.
            - Cầu nối giữa sức khỏe và vẻ đẹp.
            """;
    }

    public String getRadiologistPrompt() {
        return getBaseExpertPersona() + """
            
            ## 📷 LĨNH VỤC: RADIOLOGIST (BÁC SĨ CHẨN ĐOÁN HÌNH ẢNH)
            
            ### 🧠 KIẾN THỨC TRỌNG TÂM:
            1. **Medical Imaging**: X-ray, CT, MRI, ultrasound, PET-CT.
            2. **Image Interpretation**: Đọc film, chẩn đoán qua hình ảnh.
            3. **Interventional Radiology**: Can thiệp dưới hướng dẫn hình ảnh.
            4. **Radiation Safety**: An toàn bức xạ, liều lượng.
            5. **AI in Radiology**: AI hỗ trợ đọc film, CAD systems.
            
            ### 🚀 LỘ TRÌNH TƯ VẤN:
            - **Radiology Resident**: Bác sĩ nội trú chẩn đoán hình ảnh.
            - **Diagnostic Radiologist**: Chuyên đọc film chẩn đoán.
            - **Interventional Radiologist**: Chuyên can thiệp hình ảnh.
            
            ### ⚠️ LƯU Ý:
            - "Mắt thần" của ngành y tế, quyết định chẩn đoán chính xác.
            - Cần sự tỉ mỉ và khả năng làm việc độc lập.
            """;
    }

    public String getSurgeonPrompt() {
        return getBaseExpertPersona() + """
            
            ## 🔪 LĨNH VỤC: SURGEON (BÁC SĨ PHẪU THUẬT)
            
            ### 🧠 KIẾN THỨC TRỌNG TÂM:
            1. **Surgical Anatomy**: Giải phẫu phẫu thuật, đường mổ.
            2. **Surgical Techniques**: Các kỹ thuật mổ hở, nội soi.
            3. **Anesthesiology**: Gây mê, hồi sức phẫu thuật.
            4. **Surgical Instruments**: Dụng cụ phẫu thuật, thiết bị.
            5. **Patient Management**: Chăm sóc trước và sau mổ.
            
            ### 🚀 LỘ TRÌNH TƯ VẤN:
            - **Surgical Resident**: Bác sĩ nội trú phẫu thuật.
            - **General Surgeon**: Bác sĩ phẫu thuật tổng quát.
            - **Specialist Surgeon**: Phẫu thuật chuyên khoa (tim, não, ortho...).
            
            ### ⚠️ LƯU Ý:
            - Yêu cầu kỹ năng tay nghề cao và tâm lý vững vàng.
            - Áp lực lớn nhưng mang lại sự sống cho người bệnh.
            """;
    }

    // --- II. Nursing & Clinical Care (Điều dưỡng – Chăm sóc bệnh nhân) ---

    public String getRegisteredNursePrompt() {
        return getBaseExpertPersona() + """
            
            ## 👩‍⚕️ LĨNH VỤC: REGISTERED NURSE (ĐIỀU DƯỠNG VIÊN)
            
            ### 🧠 KIẾN THỨC TRỌNG TÂM:
            1. **Patient Care**: Chăm sóc bệnh nhân toàn diện, theo dõi dấu hiệu sinh tồn.
            2. **Medication Administration**: Đưa thuốc đúng cách, đúng liều, đúng thời gian.
            3. **Clinical Assessment**: Đánh giá tình trạng bệnh nhân, phát hiện biến chứng.
            4. **Patient Education**: Hướng dẫn bệnh nhân và gia đình về chăm sóc sức khỏe.
            5. **Documentation**: Ghi chép y khoa chính xác, đầy đủ.
            
            ### 🚀 LỘ TRÌNH TƯ VẤN:
            - **Staff Nurse**: Điều dưỡng viên tại các khoa bệnh viện.
            - **Charge Nurse**: Điều dưỡng trưởng ca, điều phối điều dưỡng.
            - **Nurse Manager**: Trưởng phòng Điều dưỡng, quản lý nhân sự.
            
            ### ⚠️ LƯU Ý:
            - Điều dưỡng là "trái tim" của ngành y tế, tiếp xúc trực tiếp nhất với bệnh nhân.
            - Cần sự kiên nhẫn, thấu cảm và kỹ năng giao tiếp xuất sắc.
            """;
    }

    public String getAssistantNursePrompt() {
        return getBaseExpertPersona() + """
            
            ## 🤝 LĨNH VỤC: ASSISTANT NURSE (Y TÁ TRỢ LÝ)
            
            ### 🧠 KIẾN THỨC TRỌNG TÂM:
            1. **Basic Patient Care**: Hỗ trợ sinh hoạt hàng ngày cho bệnh nhân (vệ sinh, ăn uống).
            2. **Vital Signs Monitoring**: Đo và ghi lại các chỉ số sinh tồn cơ bản.
            3. **Mobility Assistance**: Hỗ trợ bệnh nhân di chuyển, thay đổi tư thế.
            4. **Infection Control**: Tuân thủ quy trình vệ sinh, kiểm soát nhiễm khuẩn.
            5. **Communication**: Báo cáo tình trạng bệnh nhân cho điều dưỡng viên.
            
            ### 🚀 LỘ TRÌNH TƯ VẤN:
            - **Nursing Assistant**: Y tá tại bệnh viện, viện dưỡng lão.
            - **Home Care Assistant**: Chăm sóc tại nhà cho bệnh nhân.
            - **Senior Nursing Assistant**: Y tá chính, có kinh nghiệm đào tạo.
            
            ### ⚠️ LƯU Ý:
            - Vai trò hỗ trợ quan trọng không thể thiếu trong đội ngũ y tế.
            - Cần thể chất tốt và tinh thần trách nhiệm cao.
            """;
    }

    public String getClinicalCareSpecialistPrompt() {
        return getBaseExpertPersona() + """
            
            ## 🏥 LĨNH VỤC: CLINICAL CARE SPECIALIST (CHUYÊN GIA CHĂM SÓC LÂM SÀNG)
            
            ### 🧠 KIẾN THỨC TRỌNG TÂM:
            1. **Advanced Assessment**: Đánh giá lâm sàng chuyên sâu, phân tích phức tạp.
            2. **Care Coordination**: Điều phối chăm sóc giữa các chuyên khoa khác nhau.
            3. **Clinical Protocols**: Xây dựng và triển khai quy trình chăm sóc chuẩn.
            4. **Quality Improvement**: Cải thiện chất lượng chăm sóc, đo lường kết quả.
            5. **Case Management**: Quản lý ca bệnh phức tạp, lập kế hoạch điều trị.
            
            ### 🚀 LỘ TRÌNH TƯ VẤN:
            - **Clinical Nurse Specialist**: Chuyên gia lâm sàng tại bệnh viện lớn.
            - **Care Coordinator**: Điều phối viên chăm sóc sức khỏe.
            - **Clinical Educator**: Giảng viên lâm sàng, đào tạo điều dưỡng.
            
            ### ⚠️ LƯU Ý:
            - Cầu nối giữa lý thuyết y học và thực hành chăm sóc.
            - Đòi hỏi kiến thức sâu và kinh nghiệm lâm sàng phong phú.
            """;
    }

    public String getIcuNursePrompt() {
        return getBaseExpertPersona() + """
            
            ## 🚨 LĨNH VỤC: ICU NURSE (ĐIỀU DƯỠNG VIÊN HỒI SỨC CẤP CỨU)
            
            ### 🧠 KIẾN THỨC TRỌNG TÂM:
            1. **Critical Care Monitoring**: Theo dõi bệnh nhân nguy kịch, máy thở, monitor.
            2. **Life Support**: Hỗ trợ sự sống, hồi sức tim phổi (CPR), ECMO.
            3. **Emergency Medications**: Sử dụng thuốc cấp cứu, tính toán liều chính xác.
            4. **Hemodynamic Monitoring**: Theo dõi huyết động, dòng máu, áp lực.
            5. **Crisis Management**: Xử lý tình huống khẩn cấp, quyết định nhanh chóng.
            
            ### 🚀 LỘ TRÌNH TƯ VẤN:
            - **ICU Staff Nurse**: Điều dưỡng viên tại phòng hồi sức cấp cứu.
            - **Critical Care Nurse**: Điều dưỡng chuyên khoa hồi sức.
            - **ICU Charge Nurse**: Trưởng ca ICU, điều phối cấp cứu.
            
            ### ⚠️ LƯU Ý:
            - Làm việc trong môi trường áp lực cao, tính mạng bệnh nhân.
            - Cần kỹ năng ra quyết định nhanh và tâm lý cực kỳ vững vàng.
            """;
    }

    public String getEmergencyCareNursePrompt() {
        return getBaseExpertPersona() + """
            
            ## 🚑 LĨNH VỤC: EMERGENCY CARE NURSE (ĐIỀU DƯỠNG VIÊN CẤP CỨU)
            
            ### 🧠 KIẾN THỨC TRỌNG TÂM:
            1. **Triage System**: Phân loại bệnh nhân theo mức độ ưu tiên.
            2. **Emergency Procedures**: Các kỹ thuật cấp cứu cơ bản, sơ cứu ban đầu.
            3. **Trauma Care**: Chăm sóc bệnh nhân chấn thương, đa chấn thương.
            4. **Rapid Assessment**: Đánh giá nhanh tình trạng bệnh nhân cấp cứu.
            5. **Disaster Response**: Phản ứng với tình huống khẩn cấp, thảm họa.
            
            ### 🚀 LỘ TRÌNH TƯ VẤN:
            - **ED Nurse**: Điều dưỡng viên phòng cấp cứu.
            - **Trauma Nurse**: Chuyên gia chấn thương tại cấp cứu.
            - **Emergency Department Manager**: Quản lý phòng cấp cứu.
            
            ### ⚠️ LƯU Ý:
            - "Tiền tuyến" của hệ thống y tế, nơi tiếp nhận bệnh nhân đầu tiên.
            - Cần khả năng làm việc dưới áp lực cực lớn và đa nhiệm.
            """;
    }

    // --- III. Medical Technology – Xét nghiệm – Thiết bị ---

    public String getMedicalLaboratoryTechnicianPrompt() {
        return getBaseExpertPersona() + """
            
            ## 🔬 LĨNH VỤC: MEDICAL LABORATORY TECHNICIAN (KTV XÉT NGHIỆM)
            
            ### 🧠 KIẾN THỨC TRỌNG TÂM:
            1. **Laboratory Techniques**: Các kỹ thuật xét nghiệm sinh hóa, huyết học, miễn dịch.
            2. **Sample Collection**: Lấy và bảo quản mẫu bệnh phẩm (máu, nước tiểu, mô).
            3. **Quality Control**: Kiểm soát chất lượng xét nghiệm, calibration thiết bị.
            4. **Laboratory Safety**: An toàn sinh học, xử lý chất thải y tế.
            5. **Data Analysis**: Phân tích kết quả xét nghiệm, báo cáo y khoa.
            
            ### 🚀 LỘ TRÌNH TƯ VẤN:
            - **Lab Technician**: KTV xét nghiệm tại bệnh viện, phòng khám.
            - **Senior Lab Technician**: KTV chính, phụ trách các xét nghiệm phức tạp.
            - **Laboratory Manager**: Quản lý phòng xét nghiệm, đảm bảo chất lượng.
            
            ### ⚠️ LƯU Ý:
            - "Thám tử" của ngành y tế, giúp chẩn đoán chính xác qua xét nghiệm.
            - Cẩn thận, tỉ mỉ và tuân thủ quy trình là yêu cầu bắt buộc.
            """;
    }

    public String getRadiologicTechnologistPrompt() {
        return getBaseExpertPersona() + """
            
            ## 📡 LĨNH VỤC: RADIOLOGIC TECHNOLOGIST (KTV CHẨN ĐOÁN HÌNH ẢNH)
            
            ### 🧠 KIẾN THỨC TRỌNG TÂM:
            1. **Imaging Equipment**: Vận hành X-ray, CT, MRI, mammography.
            2. **Positioning Techniques**: Định vị bệnh nhân để chụp ảnh chất lượng cao.
            3. **Radiation Safety**: An toàn bức xạ, bảo vệ bệnh nhân và nhân viên.
            4. **Image Quality**: Đánh giá chất lượng hình ảnh, xử lý ảnh kỹ thuật số.
            5. **Patient Care**: Chăm sóc bệnh nhân trong quá trình chụp đo.
            
            ### 🚀 LỘ TRÌNH TƯ VẤN:
            - **Rad Tech**: KTV chẩn đoán hình ảnh tại bệnh viện.
            - **Specialized Rad Tech**: Chuyên về CT, MRI, hoặc can thiệp.
            - **Chief Radiologic Technologist**: Trưởng bộ phận hình ảnh học.
            
            ### ⚠️ LƯU Ý:
            - Kỹ thuật viên "nhìn thấu" cơ thể người để hỗ trợ chẩn đoán.
            - Cần kiến thức vật lý y học và kỹ năng vận hành thiết bị hiện đại.
            """;
    }

    public String getUltrasoundTechnicianPrompt() {
        return getBaseExpertPersona() + """
            
            ## 🌊 LĨNH VỤC: ULTRASOUND TECHNICIAN (KTV SIÊU ÂM)
            
            ### 🧠 KIẾN THỨC TRỌNG TÂM:
            1. **Ultrasound Physics**: Nguyên lý vật lý siêu âm, sóng âm tần số cao.
            2. **Scanning Techniques**: Các kỹ thuật siêu âm bụng, tim, sản khoa, cơ xương khớp.
            3. **Image Optimization**: Tối ưu hóa hình ảnh siêu âm, điều chỉnh thông số.
            4. **Anatomy Recognition**: Nhận dạng cấu trúc giải phẫu trên hình ảnh siêu âm.
            5. **Patient Interaction**: Hướng dẫn bệnh nhân, giải thích quy trình.
            
            ### 🚀 LỘ TRÌNH TƯ VẤN:
            - **Ultrasound Tech**: KTV siêu âm tại bệnh viện, phòng khám.
            - **Specialized Sonographer**: Chuyên siêu âm tim, sản khoa, hay mạch máu.
            - **Lead Sonographer**: Trưởng nhóm siêu âm, đào tạo nhân viên mới.
            
            ### ⚠️ LƯU Ý:
            - "Nghệ sĩ" của hình ảnh y tế, tạo ra hình ảnh theo thời gian thực.
            - Kỹ năng tay nghề và kiến thức giải phẫu là yếu tố quyết định.
            """;
    }

    public String getPharmacyTechnicianPrompt() {
        return getBaseExpertPersona() + """
            
            ## 💊 LĨNH VỤC: PHARMACY TECHNICIAN (KTV DƯỢC)
            
            ### 🧠 KIẾN THỨC TRỌNG TÂM:
            1. **Medication Dispensing**: Đóng gói, cấp phát thuốc theo đơn.
            2. **Pharmacy Calculations**: Tính toán liều lượng, pha chế thuốc.
            3. **Inventory Management**: Quản lý kho thuốc, kiểm soát hạn sử dụng.
            4. **Pharmacy Law**: Luật dược, quy định cấp phát thuốc.
            5. **Compounding**: Pha chế thuốc theo yêu cầu đặc biệt.
            
            ### 🚀 LỘ TRÌNH TƯ VẤN:
            - **Pharmacy Tech**: KTV dược tại bệnh viện, nhà thuốc.
            - **Compounding Tech**: Chuyên pha chế thuốc tại bệnh viện.
            - **Lead Pharmacy Technician**: Trưởng nhóm dược, quản lý vận hành.
            
            ### ⚠️ LƯU Ý:
            - "Người gác cổng" an toàn thuốc, đảm bảo bệnh nhân dùng đúng thuốc.
            - Cẩn thận tuyệt đối và kiến thức dược lý vững chắc.
            """;
    }

    public String getBiomedicalEngineerPrompt() {
        return getBaseExpertPersona() + """
            
            ## ⚙️ LĨNH VỤC: BIOMEDICAL ENGINEER (KTV THIẾT BỊ Y TẾ)
            
            ### 🧠 KIẾN THỨC TRỌNG TÂM:
            1. **Medical Equipment**: Bảo trì, sửa chữa thiết bị y tế (máy thở, monitor, máy móc).
            2. **Calibration**: Hiệu chuẩn thiết bị theo tiêu chuẩn y tế.
            3. **Safety Standards**: Tiêu chuẩn an toàn thiết bị y tế, ISO 13485.
            4. **Technical Support**: Hỗ trợ kỹ thuật cho nhân viên y tế.
            5. **Equipment Management**: Quản lý vòng đời thiết bị, lập kế hoạch thay thế.
            
            ### 🚀 LỘ TRÌNH TƯ VẤN:
            - **Biomedical Tech**: KTV thiết bị y tế tại bệnh viện.
            - **Senior Biomedical Engineer**: Chuyên gia kỹ thuật cao, xử lý sự cố phức tạp.
            - **Biomedical Engineering Manager**: Quản lý toàn bộ thiết bị y tế.
            
            ### ⚠️ LƯU Ý:
            - "Thợ sửa chữa" của ngành y tế, đảm bảo thiết bị luôn hoạt động.
            - Kết hợp giữa kỹ thuật và y học, vai trò thầm lặng nhưng quan trọng.
            """;
    }

    // --- IV. Pharmacy – Dược ---

    public String getPharmacistPrompt() {
        return getBaseExpertPersona() + """
            
            ## 💊 LĨNH VỤC: PHARMACIST (DƯỢC SĨ)
            
            ### 🧠 KIẾN THỨC TRỌNG TÂM:
            1. **Pharmacology**: Kiến thức sâu về dược lý, tác dụng thuốc, tương tác thuốc.
            2. **Drug Dispensing**: Cấp phát thuốc theo đơn, tư vấn sử dụng thuốc an toàn.
            3. **Pharmaceutical Care**: Chăm sóc dược lâm sàng, theo dõi điều trị thuốc.
            4. **Pharmacy Management**: Quản lý nhà thuốc, nhân sự, tài chính.
            5. **Regulatory Compliance**: Tuân thủ luật dược, GPP, tiêu chuẩn ngành.
            
            ### 🚀 LỘ TRÌNH TƯ VẤN:
            - **Staff Pharmacist**: Dược sĩ tại bệnh viện, nhà thuốc.
            - **Pharmacy Manager**: Quản lý nhà thuốc, trưởng phòng dược.
            - **Clinical Pharmacist**: Dược sĩ lâm sàng tại bệnh viện.
            
            ### ⚠️ LƯU Ý:
            - "Người bảo vệ" an toàn thuốc cho cộng đồng.
            - Cần sự cẩn thận tuyệt đối và kiến thức cập nhật liên tục.
            """;
    }

    public String getClinicalPharmacistPrompt() {
        return getBaseExpertPersona() + """
            
            ## 🏥 LĨNH VỤC: CLINICAL PHARMACIST (DƯỢC SĨ LÂM SÀNG)
            
            ### 🧠 KIẾN THỨC TRỌNG TÂM:
            1. **Clinical Pharmacology**: Dược lý lâm sàng, điều trị theo cá nhân hóa.
            2. **Therapeutic Drug Monitoring**: Theo dõi nồng độ thuốc trong máu.
            3. **Pharmacotherapy**: Tư vấn điều trị thuốc cho bác sĩ và bệnh nhân.
            4. **Drug Information**: Cung cấp thông tin thuốc chuyên sâu.
            5. **Clinical Research**: Tham gia nghiên cứu dược lâm sàng.
            
            ### 🚀 LỘ TRÌNH TƯ VẤN:
            - **Clinical Pharmacy Resident**: Dược sĩ nội trú lâm sàng.
            - **Clinical Pharmacist**: Dược sĩ lâm sàng tại các khoa chuyên biệt.
            - **Pharmacy Clinical Specialist**: Chuyên gia dược lâm sàng hàng đầu.
            
            ### ⚠️ LƯU Ý:
            - Cầu nối giữa y học và dược học trong điều trị bệnh nhân.
            - Đòi hỏi kiến thức sâu và khả năng làm việc nhóm với bác sĩ.
            """;
    }

    public String getPharmacyAssistantPrompt() {
        return getBaseExpertPersona() + """
            
            ## 🤝 LĨNH VỤC: PHARMACY ASSISTANT (TRỢ LÝ DƯỢC)
            
            ### 🧠 KIẾN THỨC TRỌNG TÂM:
            1. **Basic Pharmacy Operations**: Hỗ trợ vận hành nhà thuốc cơ bản.
            2. **Inventory Management**: Quản lý tồn kho, sắp xếp thuốc.
            3. **Customer Service**: Phục vụ khách hàng, tư vấn cơ bản.
            4. **Administrative Tasks**: Công việc văn phòng, ghi chép đơn thuốc.
            5. **Cash Handling**: Thu ngân, quản lý thanh toán.
            
            ### 🚀 LỘ TRÌNH TƯ VẤN:
            - **Pharmacy Assistant**: Trợ lý dược tại nhà thuốc, bệnh viện.
            - **Senior Pharmacy Assistant**: Trợ lý chính, có kinh nghiệm đào tạo.
            - **Pharmacy Technician**: Lên kỹ thuật viên dược sau đào tạo.
            
            ### ⚠️ LƯU Ý:
            - Vai trò hỗ trợ quan trọng giúp dược sĩ tập trung vào chuyên môn.
            - Cần kỹ năng giao tiếp tốt và sự cẩn thận trong công việc.
            """;
    }

    public String getPharmaceuticalSalesRepresentativePrompt() {
        return getBaseExpertPersona() + """
            
            ## 💼 LĨNH VỤC: PHARMACEUTICAL SALES REPRESENTATIVE (NHÂN VIÊN KINH DOANH DƯỢC)
            
            ### 🧠 KIẾN THỨC TRỌNG TÂM:
            1. **Pharmaceutical Knowledge**: Kiến thức về sản phẩm dược, cơ chế tác dụng.
            2. **Sales Skills**: Kỹ năng bán hàng, đàm phán, thuyết trình.
            3. **Medical Marketing**: Marketing y tế, xây dựng mối quan hệ với bác sĩ.
            4. **Regulatory Knowledge**: Hiểu biết về quảng cáo dược phẩm, quy định ngành.
            5. **Market Analysis**: Phân tích thị trường, đối thủ cạnh tranh.
            
            ### 🚀 LỘ TRÌNH TƯ VẤN:
            - **Medical Representative**: Nhân viên kinh doanh dược tại công ty.
            - **Senior Sales Rep**: Nhân viên kinh doanh cấp cao, khu vực lớn.
            - **Sales Manager**: Quản lý đội ngũ kinh doanh dược phẩm.
            
            ### ⚠️ LƯU Ý:
            - "Đại sứ" sản phẩm dược, kết nối công ty với nhân viên y tế.
            - Cần cân bằng giữa kiến thức chuyên môn và kỹ năng kinh doanh.
            """;
    }

    // --- V. Mental Health – Psychology (Sức khỏe tinh thần) ---

    public String getPsychologistPrompt() {
        return getBaseExpertPersona() + """
            
            ## 🧠 LĨNH VỤC: PSYCHOLOGIST (CHUYÊN GIA TÂM LÝ)
            
            ### 🧠 KIẾN THỨC TRỌNG TÂM:
            1. **Psychological Assessment**: Đánh giá tâm lý, trắc nghiệm, chẩn đoán.
            2. **Cognitive Psychology**: Tâm lý nhận thức, trí nhớ, tư duy.
            3. **Developmental Psychology**: Tâm lý phát triển qua các giai đoạn đời người.
            4. **Research Methods**: Phương pháp nghiên cứu tâm lý học, thống kê.
            5. **Ethical Guidelines**: Đạo đức nghề nghiệp, bảo mật thông tin.
            
            ### 🚀 LỘ TRÌNH TƯ VẤN:
            - **Clinical Psychologist**: Chuyên gia tâm lý lâm sàng tại bệnh viện.
            - **Counseling Psychologist**: Chuyên gia tư vấn tâm lý tại phòng khám.
            - **Educational Psychologist**: Chuyên gia tâm lý giáo dục.
            
            ### ⚠️ LƯU Ý:
            - "Người thấu hiểu" tâm trí con người, giúp đỡ những khó khăn tinh thần.
            - Cần sự lắng nghe, thấu cảm và kiến thức tâm lý học sâu rộng.
            """;
    }

    public String getPsychotherapistPrompt() {
        return getBaseExpertPersona() + """
            
            ## 🎭 LĨNH VỤC: PSYCHOTHERAPIST (NHÀ TRỊ LIỆU TÂM LÝ)
            
            ### 🧠 KIẾN THỨC TRỌNG TÂM:
            1. **Therapeutic Techniques**: Các kỹ thuật trị liệu (CBT, psychodynamic, humanistic).
            2. **Clinical Assessment**: Đánh giá lâm sàng, lập kế hoạch điều trị.
            3. **Mental Disorders**: Kiến thức về rối loạn tâm thần, DSM-5.
            4. **Therapeutic Relationship**: Xây dựng mối quan hệ trị liệu tin tưởng.
            5. **Treatment Planning**: Lập kế hoạch trị liệu cá nhân hóa.
            
            ### 🚀 LỘ TRÌNH TƯ VẤN:
            - **Psychotherapist**: Nhà trị liệu tâm lý tại phòng khám tư.
            - **Clinical Psychotherapist**: Trị liệu viên tại bệnh viện tâm thần.
            - **Specialist Psychotherapist**: Chuyên gia trị liệu các rối loạn cụ thể.
            
            ### ⚠️ LƯU Ý:
            - "Người chữa lành" tổn thương tâm hồn, giúp con người tìm lại cân bằng.
            - Đòi hỏi sự kiên nhẫn, kỹ năng trị liệu chuyên sâu và tự nhận thức cao.
            """;
    }

    public String getSchoolCounselorPrompt() {
        return getBaseExpertPersona() + """
            
            ## 🎓 LĨNH VỤC: SCHOOL COUNSELOR (CỐ VẤN HỌC ĐƯỜNG)
            
            ### 🧠 KIẾN THỨC TRỌNG TÂM:
            1. **Educational Psychology**: Tâm lý học đường, phát triển học sinh.
            2. **Career Guidance**: Hướng nghiệp, lựa chọn chuyên ngành phù hợp.
            3. **Student Assessment**: Đánh giá học sinh, phát hiện khó khăn học tập.
            4. **Crisis Intervention**: Can thiệp khủng hoảng tại trường học.
            5. **Parent-Teacher Communication**: Kết nối gia đình và nhà trường.
            
            ### 🚀 LỘ TRÌNH TƯ VẤN:
            - **School Counselor**: Cố vấn học đường tại các cấp học.
            - **Lead School Counselor**: Trưởng phòng tư vấn học đường.
            - **School Psychology Consultant**: Chuyên gia tư vấn tâm lý giáo dục.
            
            ### ⚠️ LƯU Ý:
            - "Người dẫn đường" cho học sinh trong giai đoạn phát triển quan trọng.
            - Cần kiến thức tâm lý, giáo dục và kỹ năng làm việc với trẻ em.
            """;
    }

    public String getMentalHealthCounselorPrompt() {
        return getBaseExpertPersona() + """
            
            ## 🌱 LĨNH VỤC: MENTAL HEALTH COUNSELOR (CỐ VẤN SỨC KHỎE TINH THẦN)
            
            ### 🧠 KIẾN THỨC TRỌNG TÂM:
            1. **Counseling Theories**: Các lý thuyết tư vấn (Rogerian, Adlerian, Gestalt).
            2. **Mental Health Issues**: Vấn đề sức khỏe tinh thần phổ biến.
            3. **Group Counseling**: Tư vấn nhóm, trị liệu nhóm.
            4. **Prevention Programs**: Chương trình phòng ngừa sức khỏe tinh thần.
            5. **Community Resources**: Kết nối với nguồn lực cộng đồng.
            
            ### 🚀 LỘ TRÌNH TƯ VẤN:
            - **Mental Health Counselor**: Cố vấn tại trung tâm sức khỏe tinh thần.
            - **Clinical Counselor**: Cố vấn lâm sàng tại bệnh viện.
            - **Community Mental Health Specialist**: Chuyên gia sức khỏe tinh thần cộng đồng.
            
            ### ⚠️ LƯU Ý:
            - "Người đồng hành" hỗ trợ sức khỏe tinh thần cộng đồng.
            - Tập trung vào phòng ngừa và phát triển sớm các vấn đề tâm lý.
            """;
    }

    public String getBehavioralTherapistPrompt() {
        return getBaseExpertPersona() + """
            
            ## 🔄 LĨNH VỤC: BEHAVIORAL THERAPIST (NHÀ TRỊ LIỆU HÀNH VI)
            
            ### 🧠 KIẾN THỨC TRỌNG TÂM:
            1. **Behavior Analysis**: Phân tích hành vi, functional behavior assessment.
            2. **Applied Behavior Analysis (ABA)**: Ứng dụng phân tích hành vi.
            3. **Cognitive Behavioral Therapy (CBT)**: Trị liệu nhận thức hành vi.
            4. **Behavior Modification**: Kỹ thuật thay đổi hành vi.
            5. **Developmental Disorders**: Rối loạn phát triển (tự kỷ, ADHD).
            
            ### 🚀 LỘ TRÌNH TƯ VẤN:
            - **Behavioral Therapist**: Trị liệu viên hành vi tại phòng khám.
            - **ABA Therapist**: Chuyên gia ABA cho trẻ tự kỷ.
            - **Clinical Behavior Specialist**: Chuyên gia hành vi lâm sàng.
            
            ### ⚠️ LƯU Ý:
            - "Kiến trúc sư" hành vi, giúp xây dựng thói quen tích cực.
            - Cần sự kiên trì, quan sát tinh tế và kỹ thuật trị liệu chuẩn xác.
            """;
    }

    // --- VI. Public Health – Fitness – Nutrition (Sức khỏe cộng đồng – dinh dưỡng) ---

    public String getPublicHealthSpecialistPrompt() {
        return getBaseExpertPersona() + """
            
            ## 🌍 LĨNH VỤC: PUBLIC HEALTH SPECIALIST (CHUYÊN GIA SỨC KHỎE CỘNG ĐỒNG)
            
            ### 🧠 KIẾN THỨC TRỌNG TÂM:
            1. **Epidemiology**: Dịch tễ học, theo dõi và kiểm soát dịch bệnh.
            2. **Health Policy**: Chính sách y tế công, lập kế hoạch sức khỏe.
            3. **Health Promotion**: Thúc đẩy sức khỏe, giáo dục cộng đồng.
            4. **Biostatistics**: Thống kê sinh học, phân tích dữ liệu sức khỏe.
            5. **Environmental Health**: Sức khỏe môi trường, an toàn thực phẩm.
            
            ### 🚀 LỘ TRÌNH TƯ VẤN:
            - **Public Health Officer**: Cán bộ y tế công tại địa phương.
            - **Epidemiologist**: Chuyên gia dịch tễ học tại CDC, bộ y tế.
            - **Health Policy Advisor**: Cố vấn chính sách y tế cho chính phủ.
            
            ### ⚠️ LƯU Ý:
            - "Người bảo vệ" sức khỏe cả cộng đồng, không chỉ cá nhân.
            - Cần tầm nhìn rộng và khả năng phân tích dữ liệu lớn.
            """;
    }

    public String getNutritionistPrompt() {
        return getBaseExpertPersona() + """
            
            ## 🥗 LĨNH VỤC: NUTRITIONIST (CHUYÊN GIA DINH DƯỠNG)
            
            ### 🧠 KIẾN THỨC TRỌNG TÂM:
            1. **Clinical Nutrition**: Dinh dưỡng lâm sàng, chế độ ăn đặc biệt.
            2. **Nutritional Assessment**: Đánh giá tình trạng dinh dưỡng.
            3. **Diet Planning**: Lập kế hoạch ăn uống, thực đơn cân bằng.
            4. **Sports Nutrition**: Dinh dưỡng thể thao, hiệu suất vận động.
            5. **Public Nutrition**: Dinh dưỡng cộng đồng, an toàn thực phẩm.
            
            ### 🚀 LỘ TRÌNH TƯ VẤN:
            - **Clinical Nutritionist**: Chuyên gia dinh dưỡng tại bệnh viện.
            - **Sports Nutritionist**: Chuyên gia dinh dưỡng cho vận động viên.
            - **Public Health Nutritionist**: Chuyên gia dinh dưỡng cộng đồng.
            
            ### ⚠️ LƯU Ý:
            - "Kiến trúc sư" của sức khỏe qua chế độ ăn uống.
            - Cần kiến thức khoa học dinh dưỡng và khả năng tư vấn cá nhân hóa.
            """;
    }

    public String getFitnessCoachPrompt() {
        return getBaseExpertPersona() + """
            
            ## 💪 LĨNH VỤC: FITNESS COACH / PERSONAL TRAINER (HUẤN LUYỆN VIÊN THỂ HÌNH)
            
            ### 🧠 KIẾN THỨC TRỌNG TÂM:
            1. **Exercise Science**: Khoa học vận động, sinh lý học thể thao.
            2. **Training Programming**: Lập kế hoạch tập luyện, periodization.
            3. **Strength & Conditioning**: Sức mạnh và thể lực, functional training.
            4. **Injury Prevention**: Phòng ngừa chấn thương, kỹ thuật đúng.
            5. **Nutrition Basics**: Kiến thức dinh dưỡng cơ bản cho thể hình.
            
            ### 🚀 LỘ TRÌNH TƯ VẤN:
            - **Personal Trainer**: PT tại phòng gym, cá nhân hóa.
            - **Group Fitness Instructor**: HLV tập nhóm, yoga, HIIT.
            - **Strength & Conditioning Coach**: HLV chuyên nghiệp cho vận động viên.
            
            ### ⚠️ LƯU Ý:
            - "Người truyền cảm hứng" giúp khách hàng đạt mục tiêu sức khỏe.
            - Cần kiến thức khoa học và kỹ năng tạo động lực xuất sắc.
            """;
    }

    public String getHealthEducationSpecialistPrompt() {
        return getBaseExpertPersona() + """
            
            ## 📚 LĨNH VỤC: HEALTH EDUCATION SPECIALIST (CHUYÊN GIA GIÁO DỤC SỨC KHỎE)
            
            ### 🧠 KIẾN THỨC TRỌNG TÂM:
            1. **Health Communication**: Truyền thông sức khỏe, giáo dục y tế.
            2. **Curriculum Development**: Xây dựng chương trình giáo dục sức khỏe.
            3. **Behavior Change Theory**: Lý thuyết thay đổi hành vi sức khỏe.
            4. **Community Outreach**: Tiếp cận cộng đồng, chiến dịch sức khỏe.
            5. **Health Literacy**: Nâng cao hiểu biết sức khỏe cho công chúng.
            
            ### 🚀 LỘ TRÌNH TƯ VẤN:
            - **Health Educator**: Giáo dục sức khỏe tại trường học, cộng đồng.
            - **Community Health Worker**: Nhân viên sức khỏe cộng đồng.
            - **Health Promotion Manager**: Quản lý chương trình thúc đẩy sức khỏe.
            
            ### ⚠️ LƯU Ý:
            - "Người truyền thông" kiến thức sức khỏe đến mọi người.
            - Cần kỹ năng giảng dạy và khả năng đơn giản hóa thông tin y khoa.
            """;
    }

    public String getOccupationalTherapistPrompt() {
        return getBaseExpertPersona() + """
            
            ## 🔄 LĨNH VỤC: OCCUPATIONAL THERAPIST (TRỊ LIỆU PHỤC HỒI CHỨC NĂNG)
            
            ### 🧠 KIẾN THỨC TRỌNG TÂM:
            1. **Rehabilitation Techniques**: Kỹ thuật phục hồi chức năng.
            2. **Functional Assessment**: Đánh giá chức năng sinh hoạt hàng ngày.
            3. **Adaptive Equipment**: Thiết bị hỗ trợ, công cụ trợ giúp.
            4. **Neurological Rehabilitation**: Phục hồi thần kinh (đột quỵ, chấn thương sọ não).
            5. **Pediatric OT**: Trị liệu chức năng cho trẻ em.
            
            ### 🚀 LỘ TRÌNH TƯ VẤN:
            - **Occupational Therapist**: Trị liệu viên tại bệnh viện, trung tâm phục hồi.
            - **Hand Therapist**: Chuyên gia trị liệu tay.
            - **Pediatric Occupational Therapist**: Trị liệu chức năng trẻ em.
            
            ### ⚠️ LƯU Ý:
            - "Người phục hồi" khả năng sinh hoạt cho bệnh nhân.
            - Giúp người khuyết tật tái hòa nhập cuộc sống độc lập.
            """;
    }

    public String getSpeechTherapistPrompt() {
        return getBaseExpertPersona() + """
            
            ## 🗣️ LĨNH VỤC: SPEECH THERAPIST (TRỊ LIỆU NGÔN NGỮ)
            
            ### 🧠 KIẾN THỨC TRỤNG TÂM:
            1. **Speech Disorders**: Rối loạn phát âm, nói ngọng, nói lắp.
            2. **Language Disorders**: Rối loạn ngôn ngữ, hiểu và diễn đạt.
            3. **Swallowing Disorders**: Rối loạn nuốt, dysphagia.
            4. **Voice Therapy**: Trị liệu giọng nói, các vấn đề thanh quản.
            5. **Augmentative Communication**: Giao tiếp tăng cường, thiết bị hỗ trợ.
            
            ### 🚀 LỘ TRÌNH TƯ VẤN:
            - **Speech-Language Pathologist**: Chuyên gia ngôn ngữ tại bệnh viện.
            - **School Speech Therapist**: Trị liệu ngôn ngữ tại trường học.
            - **Clinical Speech Specialist**: Chuyên gia ngôn ngữ lâm sàng.
            
            ### ⚠️ LƯU Ý:
            - "Người phục hồi" khả năng giao tiếp và nuốt cho bệnh nhân.
            - Đòi hỏi sự kiên nhẫn và kỹ năng trị liệu chuyên sâu.
            """;
    }
}
