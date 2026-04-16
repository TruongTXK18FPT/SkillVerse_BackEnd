package com.exe.skillverse_backend.ai_service.service.impl;

import com.exe.skillverse_backend.ai_service.service.AssessmentPromptService.QuestionInfo;
import com.exe.skillverse_backend.ai_service.service.AssessmentPromptService.TestSubmissionInfo;
import com.exe.skillverse_backend.ai_service.service.AssessmentPromptService.UserAssessmentInfo;
import com.exe.skillverse_backend.ai_service.service.AssessmentPromptService;
import com.exe.skillverse_backend.ai_service.service.TaxonomyService;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Implementation of AssessmentPromptService.
 * Creates detailed, professional assessment prompts for each domain/industry/role.
 * Uses TaxonomyService for domain detection and skill mapping.
 */
@Slf4j
@Service
public class AssessmentPromptServiceImpl implements AssessmentPromptService {

    private final TaxonomyService taxonomyService;

    public AssessmentPromptServiceImpl(TaxonomyService taxonomyService) {
        this.taxonomyService = taxonomyService;
    }

    @Override
    public String getTestGenerationPrompt(String domain, String industry, String role, UserAssessmentInfo userInfo) {
        String detectedDomain = domain;
        if (detectedDomain == null || detectedDomain.isBlank()) {
            detectedDomain = taxonomyService.detectDomain(
                userInfo.goal(),
                industry,
                role
            );
        }

        String detectedRole = role;
        if (detectedRole == null || detectedRole.isBlank()) {
            detectedRole = taxonomyService.detectRoleCategory(
                userInfo.goal()
            );
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append(getBaseSystemPrompt()).append("\n\n");
        prompt.append(getDomainSpecificInstructions(detectedDomain, detectedRole)).append("\n\n");
        prompt.append(buildUserContextSection(userInfo, detectedDomain, detectedRole)).append("\n\n");
        prompt.append(getTestRequirementsSection(detectedDomain, detectedRole, userInfo)).append("\n\n");
        prompt.append(getOutputFormatSection(detectedDomain, detectedRole, userInfo));

        return prompt.toString();
    }

    @Override
    public String getEvaluationPrompt(String domain, String industry, String role, TestSubmissionInfo submissionInfo) {
        String detectedDomain = domain;
        if (detectedDomain == null || detectedDomain.isBlank()) {
            detectedDomain = taxonomyService.detectDomain(
                submissionInfo.testTitle(), industry, role
            );
        }
        return buildEvaluationPrompt(detectedDomain, submissionInfo);
    }

    @Override
    public String detectDomain(String target, String industry, String role) {
        return taxonomyService.detectDomain(target, industry, role);
    }

    @Override
    public String detectRole(String target, String industry, String role) {
        return taxonomyService.detectRoleCategory(target);
    }

    @Override
    public Set<String> getAllowedSkills(String domain, String role) {
        String domainId = taxonomyService.mapToDomainPackId(domain);
        String roleId = taxonomyService.normalizeToRoleId(role);
        return taxonomyService.getAllowedSkills(domainId, roleId);
    }

    // ==================== Private Helper Methods ====================

    private String getBaseSystemPrompt() {
        return "Bạn là một CHUYÊN GIA ĐÁNH GIÁ KỸ NĂNG HÀNG ĐẦU với hơn 15 năm kinh nghiệm trong việc đánh giá năng lực ứng viên và xây dựng lộ trình phát triển nghề nghiệp.\n\n" +
            "NHIỆM VỤ: Tạo bài kiểm tra đánh giá kỹ năng chuyên sâu, sâu sắc và phù hợp với thực tế ngành nghề.\n\n" +
            "NGUYÊN TẮC QUAN TRỌNG:\n" +
            "1. Câu hỏi phải đánh giá ĐÚNG 'điểm đau' (pain points) và 'điểm chạm' (touch points) của ngành nghề\n" +
            "2. Không chỉ hỏi lý thuyết suông mà phải đánh giá khả năng ÁP DỤNG THỰC TẾ\n" +
            "3. Câu hỏi phải phân biệt được các cấp độ: BEGINNER, INTERMEDIATE, ADVANCED, EXPERT\n" +
            "4. Mỗi câu hỏi phải có giá trị đánh giá rõ ràng - đánh đúng năng lực thực sự cần có của người làm nghề\n" +
            "5. Tránh câu hỏi mơ hồ, hỏi kiến thức máy móc không có trong thực tế công việc";
    }

    private String getDomainSpecificInstructions(String domain, String role) {
        String upperDomain = domain.toUpperCase();
        switch (upperDomain) {
            case "IT": return getITDomainInstructions(role);
            case "DESIGN": return getDesignDomainInstructions(role);
            case "BUSINESS": return getBusinessDomainInstructions(role);
            case "ENGINEERING": return getEngineeringDomainInstructions(role);
            case "HEALTHCARE": return getHealthcareDomainInstructions(role);
            case "EDUCATION": return getEducationDomainInstructions(role);
            case "LOGISTICS": return getLogisticsDomainInstructions(role);
            case "LEGAL": return getLegalDomainInstructions(role);
            case "ARTS": return getArtsDomainInstructions(role);
            case "SERVICE": return getServiceDomainInstructions(role);
            case "SOCIALCOMMUNITY": return getSocialCommunityDomainInstructions(role);
            case "AGRICULTUREENVIRONMENT": return getAgricultureEnvironmentDomainInstructions(role);
            default: return getGenericDomainInstructions(domain, role);
        }
    }

    private String getITDomainInstructions(String role) {
        String normalizedRole = role.toLowerCase();
        StringBuilder sb = new StringBuilder();
        sb.append("## NGANH CONG NGHE THONG TIN (IT)\n\n");

        if (normalizedRole.contains("backend") || normalizedRole.contains("back-end")) {
            sb.append("### Backend Developer - Kỹ sư Backend\n");
            sb.append("Điểm đau của ngành:\n");
            sb.append("- Xử lý bất đồng bộ (Async/Await, Concurrency)\n");
            sb.append("- Tối ưu SQL queries, Indexing, Query Performance\n");
            sb.append("- Design RESTful APIs, GraphQL\n");
            sb.append("- Authentication & Authorization (OAuth2, JWT)\n");
            sb.append("- Caching strategies (Redis, Memcached)\n");
            sb.append("- Microservices architecture, Message queues (Kafka, RabbitMQ)\n");
            sb.append("- Database transactions, ACID compliance\n");
            sb.append("- Error handling, Logging, Monitoring\n");
            sb.append("- Security: SQL Injection, XSS, CSRF prevention\n\n");
            sb.append("Câu hỏi ưu tiên:\n");
            sb.append("1. Xử lý high concurrency, distributed systems\n");
            sb.append("2. Database optimization và data modeling\n");
            sb.append("3. API design best practices\n");
            sb.append("4. Security real-world scenarios\n");
            sb.append("5. Performance troubleshooting cases\n");
        } else if (normalizedRole.contains("frontend") || normalizedRole.contains("front-end")) {
            sb.append("### Frontend Developer - Kỹ sư Frontend\n");
            sb.append("Điểm đau của ngành:\n");
            sb.append("- React/Vue/Angular state management (Redux, Vuex, Context)\n");
            sb.append("- Performance optimization (lazy loading, memoization)\n");
            sb.append("- Responsive design, Cross-browser compatibility\n");
            sb.append("- Webpack/Vite build optimization\n");
            sb.append("- Accessibility (WCAG standards)\n");
            sb.append("- CSS architecture (BEM, SCSS, Tailwind)\n");
            sb.append("- TypeScript advanced types\n");
            sb.append("- Testing strategies (Unit, Integration, E2E)\n\n");
            sb.append("Câu hỏi ưu tiên:\n");
            sb.append("1. Performance optimization techniques\n");
            sb.append("2. State management patterns\n");
            sb.append("3. Component architecture\n");
            sb.append("4. Browser rendering, DOM manipulation\n");
            sb.append("5. Modern CSS và responsive design\n");
        } else if (normalizedRole.contains("data analyst") || normalizedRole.contains("data")) {
            sb.append("### Data Analyst - Chuyên gia Phân tích Dữ liệu\n");
            sb.append("Điểm đau của ngành:\n");
            sb.append("- SQL queries phức tạp, Window functions\n");
            sb.append("- Data visualization (Tableau, Power BI)\n");
            sb.append("- Statistical analysis, A/B testing\n");
            sb.append("- Python/R for data analysis (Pandas, NumPy)\n");
            sb.append("- Data cleaning, preprocessing\n");
            sb.append("- Business intelligence reporting\n");
            sb.append("- Storytelling with data\n");
            sb.append("- ETL pipelines\n\n");
            sb.append("Câu hỏi ưu tiên:\n");
            sb.append("1. Complex SQL queries và data manipulation\n");
            sb.append("2. Statistical analysis và interpretation\n");
            sb.append("3. Data visualization best practices\n");
            sb.append("4. Business metrics và KPIs\n");
            sb.append("5. Case studies phân tích dữ liệu thực tế\n");
        } else if (normalizedRole.contains("devops")) {
            sb.append("### DevOps Engineer - Kỹ sư DevOps\n");
            sb.append("Điểm đau của ngành:\n");
            sb.append("- CI/CD pipelines (Jenkins, GitLab CI, GitHub Actions)\n");
            sb.append("- Containerization (Docker)\n");
            sb.append("- Orchestration (Kubernetes)\n");
            sb.append("- Infrastructure as Code (Terraform, Ansible)\n");
            sb.append("- Cloud services (AWS, GCP, Azure)\n");
            sb.append("- Monitoring và Logging (Prometheus, Grafana, ELK)\n");
            sb.append("- Automation scripting (Bash, Python)\n");
            sb.append("- Security in DevOps (DevSecOps)\n\n");
            sb.append("Câu hỏi ưu tiên:\n");
            sb.append("1. Container orchestration scenarios\n");
            sb.append("2. Infrastructure automation\n");
            sb.append("3. CI/CD pipeline design\n");
            sb.append("4. Cloud architecture\n");
            sb.append("5. Monitoring và incident response\n");
        } else if (normalizedRole.contains("machine learning") || normalizedRole.contains("ml") || normalizedRole.contains("ai")) {
            sb.append("### AI/ML Engineer - Kỹ sư AI/Machine Learning\n");
            sb.append("Điểm đau của ngành:\n");
            sb.append("- Machine Learning algorithms (Supervised, Unsupervised, Deep Learning)\n");
            sb.append("- Model training, validation, testing\n");
            sb.append("- Feature engineering\n");
            sb.append("- Model deployment (MLOps)\n");
            sb.append("- Natural Language Processing\n");
            sb.append("- Computer Vision\n");
            sb.append("- TensorFlow, PyTorch\n");
            sb.append("- Model optimization, hyperparameter tuning\n\n");
            sb.append("Câu hỏi ưu tiên:\n");
            sb.append("1. ML algorithm selection và tuning\n");
            sb.append("2. Data preprocessing, feature engineering\n");
            sb.append("3. Model evaluation metrics\n");
            sb.append("4. Deep Learning architectures\n");
            sb.append("5. MLOps và deployment\n");
        } else {
            sb.append("### General IT - Lập trình viên IT tổng quát\n");
            sb.append("Điểm đau của ngành:\n");
            sb.append("- Data structures & Algorithms\n");
            sb.append("- Problem-solving skills\n");
            sb.append("- System design basics\n");
            sb.append("- Version control (Git)\n");
            sb.append("- Software development lifecycle\n");
            sb.append("- Code quality, testing\n\n");
            sb.append("Câu hỏi ưu tiên:\n");
            sb.append("1. Algorithm complexity, Big O notation\n");
            sb.append("2. System design scenarios\n");
            sb.append("3. Problem-solving approach\n");
            sb.append("4. Best practices in coding\n");
            sb.append("5. Debugging techniques\n");
        }
        return sb.toString();
    }

    private String getDesignDomainInstructions(String role) {
        String normalizedRole = role.toLowerCase();
        StringBuilder sb = new StringBuilder();
        sb.append("## NGANH THIET KE (DESIGN)\n\n");

        if (normalizedRole.contains("ui")) {
            sb.append("### UI Designer - Thiet ke Giao dien Nguoi dung\n");
            sb.append("Điểm đau của ngành:\n");
            sb.append("- Design systems, Component libraries\n");
            sb.append("- Typography, Color theory, Visual hierarchy\n");
            sb.append("- Responsive design principles\n");
            sb.append("- Figma/Sketch/Adobe XD proficiency\n");
            sb.append("- Animation, micro-interactions\n");
            sb.append("- Design handoff to developers\n");
            sb.append("- Accessibility standards\n");
            sb.append("- Design tokens, theming\n\n");
            sb.append("Câu hỏi ưu tiên:\n");
            sb.append("1. Visual design principles application\n");
            sb.append("2. Design system thinking\n");
            sb.append("3. UX considerations in UI\n");
            sb.append("4. Design tools proficiency\n");
            sb.append("5. Design-to-code conversion\n");
        } else if (normalizedRole.contains("ux")) {
            sb.append("### UX Designer - Thiet ke Trai nghiem Nguoi dung\n");
            sb.append("Điểm đau của ngành:\n");
            sb.append("- User research methodologies\n");
            sb.append("- Information architecture\n");
            sb.append("- Wireframing, Prototyping\n");
            sb.append("- Usability testing\n");
            sb.append("- User journey mapping\n");
            sb.append("- Interaction design\n");
            sb.append("- Accessibility (WCAG)\n");
            sb.append("- Analytics-driven design decisions\n\n");
            sb.append("Câu hỏi ưu tiên:\n");
            sb.append("1. User research analysis\n");
            sb.append("2. Information architecture\n");
            sb.append("3. Usability heuristics\n");
            sb.append("4. User journey optimization\n");
            sb.append("5. Case studies design thinking\n");
        } else {
            sb.append("### General Design - Thiet ke tong quan\n");
            sb.append("Điểm đau của ngành:\n");
            sb.append("- Design principles (Contrast, Balance, Hierarchy)\n");
            sb.append("- Color theory, Typography\n");
            sb.append("- Brand identity design\n");
            sb.append("- Print vs Digital design\n");
            sb.append("- Design tools proficiency\n");
            sb.append("- Client communication\n\n");
            sb.append("Câu hỏi ưu tiên:\n");
            sb.append("1. Visual design fundamentals\n");
            sb.append("2. Design process\n");
            sb.append("3. Tool proficiency\n");
            sb.append("4. Communication skills\n");
            sb.append("5. Creative problem-solving\n");
        }
        return sb.toString();
    }

    private String getBusinessDomainInstructions(String role) {
        return "## NGANH KINH DOANH (BUSINESS)\n\n" +
            "Điểm đau của ngành:\n" +
            "- Digital Marketing (SEO, SEM, Content Marketing)\n" +
            "- Sales strategy và pipeline management\n" +
            "- Financial analysis, Modeling\n" +
            "- Project management (Agile, Scrum)\n" +
            "- Business development\n" +
            "- Data-driven decision making\n" +
            "- Customer relationship management\n" +
            "- Market research, competitive analysis\n\n" +
            "Câu hỏi ưu tiên:\n" +
            "1. Marketing strategy cases\n" +
            "2. Financial analysis scenarios\n" +
            "3. Sales process optimization\n" +
            "4. Project management methodologies\n" +
            "5. Business analytics application\n";
    }

    private String getEngineeringDomainInstructions(String role) {
        return "## NGANH KY THUAT (ENGINEERING)\n\n" +
            "Điểm đau của ngành:\n" +
            "- Mechanical/Civil/Electrical engineering fundamentals\n" +
            "- CAD software (AutoCAD, SolidWorks)\n" +
            "- Manufacturing processes\n" +
            "- Project planning, cost estimation\n" +
            "- Quality control, standards\n" +
            "- Technical documentation\n" +
            "- Problem-solving in engineering contexts\n" +
            "- Safety regulations compliance\n\n" +
            "Câu hỏi ưu tiên:\n" +
            "1. Engineering calculations\n" +
            "2. Design principles application\n" +
            "3. Industry standards knowledge\n" +
            "4. Problem-solving scenarios\n" +
            "5. Technical documentation\n";
    }

    private String getHealthcareDomainInstructions(String role) {
        return "## NGANH Y TE (HEALTHCARE)\n\n" +
            "Điểm đau của ngành:\n" +
            "- Clinical knowledge, patient care\n" +
            "- Healthcare regulations (HIPAA)\n" +
            "- Medical terminology\n" +
            "- Patient communication\n" +
            "- Healthcare technology (EHR systems)\n" +
            "- Public health principles\n" +
            "- Ethics in healthcare\n" +
            "- Healthcare management\n\n" +
            "Câu hỏi ưu tiên:\n" +
            "1. Clinical scenarios\n" +
            "2. Regulatory compliance\n" +
            "3. Patient care protocols\n" +
            "4. Healthcare technology\n" +
            "5. Ethics và communication\n";
    }

    private String getEducationDomainInstructions(String role) {
        return "## NGANH GIAO DUC (EDUCATION)\n\n" +
            "Điểm đau của ngành:\n" +
            "- Curriculum development\n" +
            "- Instructional design\n" +
            "- E-learning platforms\n" +
            "- Student assessment\n" +
            "- Classroom management\n" +
            "- EdTech tools\n" +
            "- Learning psychology\n" +
            "- Educational technology integration\n\n" +
            "Câu hỏi ưu tiên:\n" +
            "1. Teaching methodologies\n" +
            "2. Curriculum design\n" +
            "3. Assessment strategies\n" +
            "4. EdTech proficiency\n" +
            "5. Student engagement\n";
    }

    private String getLogisticsDomainInstructions(String role) {
        return "## NGANH LOGISTICS (LOGISTICS)\n\n" +
            "Điểm đau của ngành:\n" +
            "- Supply chain management\n" +
            "- Warehouse operations\n" +
            "- Transportation planning\n" +
            "- Inventory management\n" +
            "- Logistics software (WMS, TMS)\n" +
            "- Cost optimization\n" +
            "- International trade terms\n" +
            "- Last-mile delivery\n\n" +
            "Câu hỏi ưu tiên:\n" +
            "1. Supply chain optimization\n" +
            "2. Inventory management\n" +
            "3. Transportation planning\n" +
            "4. Logistics technology\n" +
            "5. Cost analysis\n";
    }

    private String getLegalDomainInstructions(String role) {
        return "## NGANH PHAP LUAT (LEGAL)\n\n" +
            "Điểm đau của ngành:\n" +
            "- Legal research\n" +
            "- Contract drafting, review\n" +
            "- Corporate law\n" +
            "- Intellectual property\n" +
            "- Regulatory compliance\n" +
            "- Legal documentation\n" +
            "- Client consultation\n" +
            "- Litigation support\n\n" +
            "Câu hỏi ưu tiên:\n" +
            "1. Legal analysis\n" +
            "2. Contract interpretation\n" +
            "3. Regulatory knowledge\n" +
            "4. Case research\n" +
            "5. Professional ethics\n";
    }

    private String getArtsDomainInstructions(String role) {
        return "## NGANH NGHE THUAT (ARTS)\n\n" +
            "Điểm đau của ngành:\n" +
            "- Digital art, Illustration\n" +
            "- Video production, Editing\n" +
            "- 3D modeling, Animation\n" +
            "- Photography\n" +
            "- Portfolio development\n" +
            "- Client communication\n" +
            "- Industry trends\n" +
            "- Freelance business\n\n" +
            "Câu hỏi ưu tiên:\n" +
            "1. Technical skills\n" +
            "2. Creative process\n" +
            "3. Industry knowledge\n" +
            "4. Business acumen\n" +
            "5. Portfolio presentation\n";
    }

    private String getServiceDomainInstructions(String role) {
        return "## NGANH DICH VU (SERVICE)\n\n" +
            "Điểm đau của ngành:\n" +
            "- Customer service skills\n" +
            "- Hospitality management\n" +
            "- Event planning\n" +
            "- F&B operations\n" +
            "- Hotel management\n" +
            "- Service quality\n" +
            "- Client relations\n" +
            "- Problem resolution\n\n" +
            "Câu hỏi ưu tiên:\n" +
            "1. Customer service scenarios\n" +
            "2. Service quality\n" +
            "3. Problem resolution\n" +
            "4. Operations management\n" +
            "5. Client relationships\n";
    }

    private String getSocialCommunityDomainInstructions(String role) {
        return "## NGANH CONG DONG XA HOI (SOCIAL COMMUNITY)\n\n" +
            "Điểm đau của ngành:\n" +
            "- Community building\n" +
            "- Social media management\n" +
            "- Content strategy\n" +
            "- NGO management\n" +
            "- Fundraising\n" +
            "- Volunteer coordination\n" +
            "- Impact measurement\n" +
            "- Stakeholder engagement\n\n" +
            "Câu hỏi ưu tiên:\n" +
            "1. Community engagement\n" +
            "2. Content strategy\n" +
            "3. Impact measurement\n" +
            "4. Resource mobilization\n" +
            "5. Stakeholder management\n";
    }

    private String getAgricultureEnvironmentDomainInstructions(String role) {
        return "## NGANH NONG NGHIEP VA MOI TRUONG (AGRICULTURE ENVIRONMENT)\n\n" +
            "Điểm đau của ngành:\n" +
            "- Sustainable agriculture\n" +
            "- Crop management\n" +
            "- Environmental impact\n" +
            "- Soil science\n" +
            "- Water management\n" +
            "- Agricultural technology\n" +
            "- Climate adaptation\n" +
            "- Food safety\n\n" +
            "Câu hỏi ưu tiên:\n" +
            "1. Agricultural practices\n" +
            "2. Environmental management\n" +
            "3. Technology application\n" +
            "4. Sustainability\n" +
            "5. Resource optimization\n";
    }

    private String getGenericDomainInstructions(String domain, String role) {
        return "## HUONG DAN DANH GIA CHO NGANH: " + domain + "\n\n" +
            "Với vai trò: " + role + "\n\n" +
            "Hãy tạo câu hỏi đánh giá:\n" +
            "1. Kiến thức nền tảng của ngành\n" +
            "2. Kỹ năng thực hành cần thiết\n" +
            "3. Khả năng xử lý tình huống thực tế\n" +
            "4. Tư duy nghề nghiệp\n" +
            "5. Xu hướng và công nghệ mới của ngành\n";
    }

    private String buildUserContextSection(UserAssessmentInfo userInfo, String domain, String role) {
        int requestedQuestionCount = resolveRequestedQuestionCount(userInfo);
        int requestedTimeLimitMinutes = resolveRequestedTimeLimitMinutes(userInfo);
        StringBuilder sb = new StringBuilder();
        sb.append("## THONG TIN NGUOI DUNG CAN DANH GIA:\n\n");
        sb.append("- Linh vuc: ").append(userInfo.domain() != null ? userInfo.domain() : domain).append("\n");
        sb.append("- Muc tieu: ").append(userInfo.goal() != null ? userInfo.goal() : "Chua xac dinh").append("\n");
        sb.append("- Cap do hien tai: ").append(userInfo.level() != null ? userInfo.level() : "Chua xac dinh").append("\n");
        sb.append("- Ky nang da biet: ").append(userInfo.skills() != null && !userInfo.skills().isEmpty() ? String.join(", ", userInfo.skills()) : "Chua co ky nang dang ke").append("\n");
        sb.append("- Linh vuc muon tap trung: ").append(userInfo.focusAreas() != null && !userInfo.focusAreas().isEmpty() ? String.join(", ", userInfo.focusAreas()) : "Chua xac dinh").append("\n");
        sb.append("- Ngon ngu bai test: ").append(userInfo.language() != null ? userInfo.language() : "Tieng Viet").append("\n");
        sb.append("- Thoi luong bai test: ").append(userInfo.duration() != null ? userInfo.duration() : "Tieu chuan (10-15 phut)").append("\n");
        sb.append("- So luong cau hoi mong muon: ").append(requestedQuestionCount).append("\n");
        sb.append("- Gioi han thoi gian mong muon: ").append(requestedTimeLimitMinutes).append(" phut\n\n");
        sb.append("Nganh duoc xac dinh: ").append(domain).append("\n");
        sb.append("Vai tro muc tieu: ").append(role).append("\n");
        return sb.toString();
    }

    private String getTestRequirementsSection(String domain, String role, UserAssessmentInfo userInfo) {
        int requestedQuestionCount = resolveRequestedQuestionCount(userInfo);
        int requestedTimeLimitMinutes = resolveRequestedTimeLimitMinutes(userInfo);

        String difficultyDistribution = buildDifficultyDistribution(userInfo.level());

        return "## YEU CAU TAO BAI KIEM TRA:\n\n" +
            "### So luong va Cau truc:\n" +
            "- Tao DUNG " + requestedQuestionCount + " cau hoi, khong thieu, khong du\n" +
            "- Phan bo theo do kho (theo cap do nguoi dung - " + (userInfo.level() != null ? userInfo.level() : "MIXED") + "):\n" +
            difficultyDistribution + "\n\n" +
            "### Noi dung cau hoi (phan bo deu):\n" +
            "1. Kien thuc nen tang (20%): Danh gia hieu biet co ban ve nganh\n" +
            "2. Ky nang chuyen mon (30%): Danh gia ky nang thuc hanh can thiet\n" +
            "3. Xu ly tinh huong (30%): Scenario-based questions, danh gia kha nang ap dung\n" +
            "4. Tu duy phan tich (20%): Problem-solving, decision making\n\n" +
            "### Tieu chuan cau hoi:\n" +
            "- Cau hoi phai co tinh THUC TE cao - gan voi cong viec thuc su\n" +
            "- Tranh cau hoi hoc thuoc ly thuyet khong ap dung duoc\n" +
            "- Moi cau hoi phai danh gia duoc mot ky nang/cap cu the\n" +
            "- Cau tra loi dung phai co giai thich ro rang\n" +
            "- Cac dap an sai phai co tinh 'gay nhieu' cao (co the dung mot phan)\n\n" +
            "### Thoi gian:\n" +
            "- timeLimitMinutes phai bang DUNG " + requestedTimeLimitMinutes + "\n" +
            "- Tong bai test phai phu hop voi " + requestedQuestionCount + " cau hoi trong " + requestedTimeLimitMinutes + " phut\n";
    }

    private String buildDifficultyDistribution(String level) {
        if (level == null) {
            return "  - Beginner (De): 20%\n" +
                   "  - Intermediate (Trung binh): 35%\n" +
                   "  - Advanced (Kho): 30%\n" +
                   "  - Expert (Rat kho): 15%";
        }
        return switch (level.toUpperCase()) {
            case "BEGINNER" -> "  - Beginner (De): 80% (chi danh cho nguoi bat dau)\n" +
                                "  - Intermediate (Trung binh): 20% (co ban nhung can huong dan them)\n" +
                                "  - Advanced (Kho): 0%\n" +
                                "  - Expert (Rat kho): 0%";
            case "ELEMENTARY" -> "  - Beginner (De): 60% (nen tang co ban)\n" +
                                 "  - Intermediate (Trung binh): 30% (phat trien them)\n" +
                                 "  - Advanced (Kho): 10% (khao sat gioi han)\n" +
                                 "  - Expert (Rat kho): 0%";
            case "INTERMEDIATE" -> "  - Beginner (De): 0%\n" +
                                   "  - Intermediate (Trung binh): 80% (muc tieu chinh)\n" +
                                   "  - Advanced (Kho): 20% (khao sat gioi han)\n" +
                                   "  - Expert (Rat kho): 0%";
            case "ADVANCED" -> "  - Beginner (De): 0%\n" +
                               "  - Intermediate (Trung binh): 0%\n" +
                               "  - Advanced (Kho): 80% (muc tieu chinh)\n" +
                               "  - Expert (Rat kho): 20% (thach thuc cao nhat)";
            default -> "  - Beginner (De): 20%\n" +
                        "  - Intermediate (Trung binh): 35%\n" +
                        "  - Advanced (Kho): 30%\n" +
                        "  - Expert (Rat kho): 15%";
        };
    }

    private String getOutputFormatSection(String domain, String role, UserAssessmentInfo userInfo) {
        int requestedQuestionCount = resolveRequestedQuestionCount(userInfo);
        int requestedTimeLimitMinutes = resolveRequestedTimeLimitMinutes(userInfo);
        return "## DINH DANG OUTPUT (JSON):\n\n" +
            "```json\n" +
            "{\n" +
            "  \"title\": \"Danh gia Ky nang " + role + " - Cap do: [LEVEL]\",\n" +
            "  \"description\": \"Mo ta ngan gon ve bai danh gia va muc dich\",\n" +
            "  \"targetField\": \"" + domain + "\",\n" +
            "  \"questionCount\": " + requestedQuestionCount + ",\n" +
            "  \"timeLimitMinutes\": " + requestedTimeLimitMinutes + ",\n" +
            "  \"difficultyLevel\": \"mixed\",\n" +
            "  \"questions\": [\n" +
            "    {\n" +
            "      \"questionId\": 1,\n" +
            "      \"question\": \"Cau hoi (bang tieng Viet, ro rang, cu the)\",\n" +
            "      \"options\": [\"A. ...\", \"B. ...\", \"C. ...\", \"D. ...\"],\n" +
            "      \"correctAnswer\": \"Dap an dung (A, B, C, hoac D)\",\n" +
            "      \"explanation\": \"Giai thich chi tiet TAI SAO dung va TAI SAO cac dap an sai khac\",\n" +
            "      \"difficulty\": \"beginner/intermediate/advanced/expert\",\n" +
            "      \"skillArea\": \"Ky nang duoc danh gia (vi du: Database Optimization)\",\n" +
            "      \"category\": \"knowledge/skill/situation/analysis\"\n" +
            "    }\n" +
            "  ],\n" +
            "  \"metadata\": {\n" +
            "    \"domain\": \"" + domain + "\",\n" +
            "    \"targetRole\": \"" + role + "\",\n" +
            "    \"estimatedCompletionTime\": \"" + requestedTimeLimitMinutes + " phut\",\n" +
            "    \"passingScore\": \"60%\"\n" +
            "  }\n" +
            "}\n" +
            "```\n\n" +
            "LUU Y QUAN TRONG:\n" +
            "- Chi tra ve JSON hop le, khong co text khac\n" +
            "- Khong su dung markdown code blocks trong response\n" +
            "- Dam bao JSON co the parse duoc\n" +
            "- Mang questions phai chua DUNG " + requestedQuestionCount + " phan tu\n" +
            "- Giai thich phai rat chi tiet va co gia tri hoc tap\n";
    }

    private int resolveRequestedQuestionCount(UserAssessmentInfo userInfo) {
        Integer requested = userInfo.questionCount();
        if (requested != null) {
            if (requested <= 15) {
                return 15;
            }
            if (requested <= 25) {
                return 25;
            }
            return 40;
        }

        String duration = userInfo.duration();
        if (duration == null || duration.isBlank()) {
            return 25;
        }

        return switch (duration.trim().toUpperCase()) {
            case "QUICK" -> 15;
            case "STANDARD" -> 25;
            case "DEEP" -> 40;
            default -> 25;
        };
    }

    private int resolveRequestedTimeLimitMinutes(UserAssessmentInfo userInfo) {
        String duration = userInfo.duration();
        if (duration == null || duration.isBlank()) {
            return 15;
        }

        return switch (duration.trim().toUpperCase()) {
            case "QUICK" -> 5;
            case "STANDARD" -> 15;
            case "DEEP" -> 30;
            default -> 15;
        };
    }

    private String buildEvaluationPrompt(String domain, TestSubmissionInfo submissionInfo) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Bạn là chuyên gia đánh giá năng lực học tập và định hướng lộ trình kỹ năng.\n");
        prompt.append("Hãy phân tích bài làm bằng TIẾNG VIỆT tự nhiên, rõ ràng, giàu thông tin, giọng văn khích lệ nhưng không sáo rỗng.\n");
        prompt.append("Mục tiêu là giúp người học hiểu mình đang ở đâu, mạnh ở đâu và cần làm gì tiếp theo.\n\n");

        prompt.append("Ngành: ").append(domain).append("\n");
        prompt.append("Vị trí / trọng tâm: ")
                .append(submissionInfo.role() != null ? submissionInfo.role() : submissionInfo.targetField())
                .append("\n\n");

        prompt.append("## THÔNG TIN BÀI KIỂM TRA:\n");
        prompt.append("- Tiêu đề: ").append(submissionInfo.testTitle()).append("\n");
        prompt.append("- Lĩnh vực: ").append(submissionInfo.targetField()).append("\n\n");

        prompt.append("## CÂU HỎI VÀ ĐÁP ÁN ĐÚNG:\n");
        for (QuestionInfo q : submissionInfo.questions()) {
            prompt.append("Q").append(q.questionId()).append(": ").append(q.question()).append("\n");
            prompt.append("   Đáp án đúng: ").append(q.correctAnswer()).append("\n");
            prompt.append("   Giải thích: ").append(q.explanation()).append("\n");
            prompt.append("   Kỹ năng: ").append(q.skillArea()).append("\n");
            prompt.append("   Độ khó: ").append(q.difficulty()).append("\n\n");
        }

        prompt.append("\n## CÂU TRẢ LỜI CỦA NGƯỜI DÙNG:\n");
        for (var entry : submissionInfo.userAnswers().entrySet()) {
            prompt.append("Q").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }

        prompt.append("\n## YÊU CẦU ĐÁNH GIÁ:\n");
        prompt.append("1. Chấm điểm dựa trên tỷ lệ đúng.\n");
        prompt.append("2. Trả cấp độ: BEGINNER / INTERMEDIATE / ADVANCED / EXPERT.\n");
        prompt.append("3. Xác định điểm mạnh và lỗ hổng kỹ năng thật cụ thể.\n");
        prompt.append("4. Viết đánh giá tổng quan ngắn gọn nhưng giàu ý.\n");
        prompt.append("5. Viết phản hồi chi tiết dưới dạng MARKDOWN rõ cấu trúc.\n");
        prompt.append("6. Trong phản hồi chi tiết, hãy dùng **bold** cho các keyword quan trọng và nhắc tới mã câu hỏi như Q3, Q8 khi cần.\n");
        prompt.append("7. Đề xuất hành động ưu tiên theo hướng thực tế, dễ bắt đầu.\n");
        prompt.append("8. Giọng văn phải chuẩn tiếng Việt, hấp dẫn, mang tính khích lệ và định hướng.\n\n");

        prompt.append("## CẤU TRÚC MARKDOWN CHO detailedFeedback:\n");
        prompt.append("- Có các phần theo thứ tự: `## Bức tranh hiện tại`, `## Điểm mạnh nổi bật`, `## Kỹ năng cần ưu tiên`, `## Hành động đề xuất`, `## Lời nhắn từ Meowl`.\n");
        prompt.append("- Mỗi phần nên có 2-5 bullet hoặc đoạn ngắn, tránh lan man.\n");
        prompt.append("- Ưu tiên nêu đủ ngữ cảnh, tác động và bước hành động tiếp theo.\n\n");

        prompt.append("Định dạng JSON:\n");
        prompt.append("{\n");
        prompt.append("  \"scorePercentage\": diem_so,\n");
        prompt.append("  \"evaluatedLevel\": \"BEGINNER/INTERMEDIATE/ADVANCED/EXPERT\",\n");
        prompt.append("  \"skillGaps\": [\n");
        prompt.append("    {\"skill\": \"tên kỹ năng\", \"description\": \"mô tả thiếu hụt rõ ràng, có thể nhắc Qx\", \"priority\": \"high/medium/low\", \"howToImprove\": \"cách cải thiện cụ thể\"}\n");
        prompt.append("  ],\n");
        prompt.append("  \"strengths\": [\n");
        prompt.append("    {\"skill\": \"tên kỹ năng\", \"description\": \"mô tả điểm mạnh rõ ràng, có thể nhắc Qx\", \"level\": \"vung/can_cung_co\"}\n");
        prompt.append("  ],\n");
        prompt.append("  \"detailedEvaluation\": {\n");
        prompt.append("    \"knowledgeScore\": diem_ly_thuyet,\n");
        prompt.append("    \"skillScore\": diem_ky_nang,\n");
        prompt.append("    \"problemSolvingScore\": diem_xu_ly_tinh_huong,\n");
        prompt.append("    \"analysisScore\": diem_tu_duy_phan_tich\n");
        prompt.append("  },\n");
        prompt.append("  \"evaluationSummary\": \"markdown ngắn 2-4 bullet hoặc 1 đoạn ngắn, có thể dùng **bold**\",\n");
        prompt.append("  \"detailedFeedback\": \"markdown đầy đủ theo cấu trúc yêu cầu ở trên\",\n");
        prompt.append("  \"highlightKeywords\": [\"keyword 1\", \"keyword 2\", \"keyword 3\"],\n");
        prompt.append("  \"recommendations\": [\n");
        prompt.append("    \"khuyến nghị 1 cụ thể\",\n");
        prompt.append("    \"khuyến nghị 2 cụ thể\",\n");
        prompt.append("    \"khuyến nghị 3 cụ thể\"\n");
        prompt.append("  ]\n");
        prompt.append("}\n\n");
        prompt.append("Chỉ trả về JSON hợp lệ, không kèm code block, không kèm giải thích ngoài JSON.\n");

        return prompt.toString();
    }
}
