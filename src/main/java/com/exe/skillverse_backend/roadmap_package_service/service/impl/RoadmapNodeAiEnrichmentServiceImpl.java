package com.exe.skillverse_backend.roadmap_package_service.service.impl;

import com.exe.skillverse_backend.roadmap_package_service.service.RoadmapNodeAiEnrichmentService;
import com.exe.skillverse_backend.ai_rag_service.service.AiRagGateway;
import com.exe.skillverse_backend.shared.repository.SkillRepository;
import com.exe.skillverse_backend.shared.entity.Skill;
import com.exe.skillverse_backend.ai_knowledge_service.util.AiKnowledgeSlugUtils;
import com.exe.skillverse_backend.ai_knowledge_service.entity.AiKnowledgeDocument;
import com.exe.skillverse_backend.ai_knowledge_service.repository.AiKnowledgeDocumentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * [STANDARD] Modern, highly optimized service for sequential, node-by-node AI enrichment
 * of roadmap templates based on student diagnostic levels and gaps/strengths.
 *
 * Note: Designed to replace and prevent bloated monolithic logic like the legacy
 * {@link com.exe.skillverse_backend.ai_service.service.AiRoadmapServiceImpl} which is pending cleanup.
 */
@Service
@Slf4j
public class RoadmapNodeAiEnrichmentServiceImpl implements RoadmapNodeAiEnrichmentService {

    private final ChatModel mistralChatModel;
    private final ObjectMapper objectMapper;
    private final AtomicLong nextAllowedRequestTime = new AtomicLong(0);

    @Autowired(required = false)
    private AiRagGateway aiRagGateway;

    @Autowired(required = false)
    private SkillRepository skillRepository;

    @Autowired(required = false)
    private AiKnowledgeDocumentRepository aiKnowledgeDocumentRepository;

    private static final int MAX_RETRIES = 2;
    private static final long RETRY_BACKOFF_MS = 5000;

    public RoadmapNodeAiEnrichmentServiceImpl(
            @Qualifier("mistralAiChatModel") ChatModel mistralChatModel,
            ObjectMapper objectMapper) {
        this.mistralChatModel = mistralChatModel;
        this.objectMapper = objectMapper;
    }

    @Override
    public EnrichedNode enrichNode(
            String nodeTitle,
            String nodeDescription,
            String baselineExpectedOutput,
            String baselineRubric,
            String skillName,
            String studentLevel,
            String studentGoal,
            boolean isGap,
            boolean isStrength) {
        return enrichNode(nodeTitle, nodeDescription, baselineExpectedOutput, baselineRubric, skillName, studentLevel, studentGoal, isGap, isStrength, null, null);
    }

    @Override
    public EnrichedNode enrichNode(
            String nodeTitle,
            String nodeDescription,
            String baselineExpectedOutput,
            String baselineRubric,
            String skillName,
            String studentLevel,
            String studentGoal,
            boolean isGap,
            boolean isStrength,
            String pinnedDocumentIdsJson) {
        return enrichNode(nodeTitle, nodeDescription, baselineExpectedOutput, baselineRubric, skillName, studentLevel, studentGoal, isGap, isStrength, pinnedDocumentIdsJson, null);
    }

    @Override
    public EnrichedNode enrichNode(
            String nodeTitle,
            String nodeDescription,
            String baselineExpectedOutput,
            String baselineRubric,
            String skillName,
            String studentLevel,
            String studentGoal,
            boolean isGap,
            boolean isStrength,
            String pinnedDocumentIdsJson,
            String lessonsJson) {

        String translatedGoal = translateStudentGoal(studentGoal);

        // ─── TẦNG 1: XỬ LÝ TÀI LIỆU GHIM (PINNED DOCUMENTS) ─────────────────────
        String ragContext = "";
        if (aiKnowledgeDocumentRepository != null && pinnedDocumentIdsJson != null && !pinnedDocumentIdsJson.isBlank()) {
            try {
                List<Long> docIds = new ArrayList<>();
                JsonNode arr = objectMapper.readTree(pinnedDocumentIdsJson);
                if (arr.isArray()) {
                    for (JsonNode item : arr) {
                        docIds.add(item.asLong());
                    }
                }

                if (!docIds.isEmpty()) {
                    List<AiKnowledgeDocument> pinnedDocs =
                            aiKnowledgeDocumentRepository.findAllById(docIds);

                    if (pinnedDocs != null && !pinnedDocs.isEmpty()) {
                        long totalWords = 0;
                        for (var doc : pinnedDocs) {
                            String txt = doc.getExtractedText();
                            if (txt != null) {
                                totalWords += countWords(txt);
                            }
                        }

                        if (totalWords < 3000) {
                            // A. Tài liệu ngắn -> Nạp full text trực tiếp (Bypass Vector DB)
                            StringBuilder sb = new StringBuilder();
                            for (var doc : pinnedDocs) {
                                String txt = doc.getExtractedText();
                                if (txt != null && !txt.isBlank()) {
                                    sb.append("--- TÀI LIỆU RÀNG BUỘC: ").append(doc.getTitle()).append(" ---\n")
                                      .append(txt.trim()).append("\n\n");
                                }
                            }
                            ragContext = sb.toString();
                            log.info("📌 Pinned Short Doc: Direct full text inject for node '{}' (Words: {})", nodeTitle, totalWords);
                        } else {
                            // B. Tài liệu dài -> Semantic Search khóa cứng trong tài liệu ghim
                            StringBuilder sb = new StringBuilder();
                            String ragQuery = nodeTitle + (nodeDescription != null && !nodeDescription.isBlank() ? " " + nodeDescription : "");
                            for (var doc : pinnedDocs) {
                                Map<String, String> filters = Map.of(
                                    "doc_type", "skill",
                                    "document_id", String.valueOf(doc.getId())
                                );
                                if (aiRagGateway != null) {
                                    String docContext = aiRagGateway.fetchRagContext(ragQuery, filters, 3);
                                    if (docContext != null && !docContext.isBlank()) {
                                        sb.append("--- RÀNG BUỘC TỪ TÀI LIỆU: ").append(doc.getTitle()).append(" ---\n")
                                          .append(docContext.trim()).append("\n\n");
                                    }
                                }
                            }
                            ragContext = sb.toString();
                            log.info("📌 Pinned Long Doc: Scoped Semantic Search for node '{}' (Words: {})", nodeTitle, totalWords);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to process pinned documents for node '{}': {}", nodeTitle, e.getMessage());
            }
        }

        // ─── TẦNG 2: FALLBACK VỀ RAG ĐỘNG THEO SKILL NẾU RỖNG ──────────────────
        if (ragContext.isBlank()) {
            String skillSlug = null;
            if (skillRepository != null && skillName != null && !skillName.isBlank()) {
                try {
                    Optional<Skill> skillOpt = skillRepository.findByNameIgnoreCase(skillName.trim());
                    if (skillOpt.isPresent()) {
                        String canonical = skillOpt.get().getCanonicalKey();
                        if (canonical != null && !canonical.isBlank()) {
                            skillSlug = AiKnowledgeSlugUtils.toRoadmapSkillSlug(canonical);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Database lookup failed for skillName '{}': {}", skillName, e.getMessage());
                }
            }

            if (skillSlug == null && skillName != null && !skillName.isBlank()) {
                skillSlug = AiKnowledgeSlugUtils.toRoadmapSkillSlug(skillName);
            }

            if (aiRagGateway != null && skillSlug != null && !skillSlug.isBlank()) {
                try {
                    String domain = AiKnowledgeSlugUtils.toRoadmapDomain(skillSlug);
                    String ragQuery = nodeTitle + (nodeDescription != null && !nodeDescription.isBlank() ? " " + nodeDescription : "");
                    log.info("🔍 RAG Context Lookup - Query: '{}' | Domain: '{}'", ragQuery, domain);
                    ragContext = aiRagGateway.fetchRagContext(ragQuery, Map.of("doc_type", "skill", "domain", domain), 5);
                    if (ragContext != null && !ragContext.isBlank()) {
                        log.info("✅ RAG Context Found for skillSlug '{}'", skillSlug);
                    } else {
                        log.info("⚠️ RAG Context Empty for skillSlug '{}'", skillSlug);
                    }
                } catch (Exception e) {
                    log.warn("Failed to fetch RAG context for skillSlug '{}': {}", skillSlug, e.getMessage());
                }
            }
        }

        String prompt = buildPrompt(nodeTitle, nodeDescription, baselineExpectedOutput, baselineRubric,
                skillName, studentLevel, translatedGoal, isGap, isStrength, ragContext, lessonsJson);

        long start = System.currentTimeMillis();
        log.info("📤 Sequential AI Enrichment - Node: '{}' (Level: {}, Gap: {}, Strength: {})", 
                nodeTitle, studentLevel, isGap, isStrength);

        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES + 1; attempt++) {
            try {
                // Thread-safe progressive Ticket-based Rate Limiter (1.0s minimum gap between ANY Mistral requests)
                long now = System.currentTimeMillis();
                long scheduledTime;
                while (true) {
                    long currentNext = nextAllowedRequestTime.get();
                    scheduledTime = Math.max(now, currentNext);
                    long next = scheduledTime + 1000L;
                    if (nextAllowedRequestTime.compareAndSet(currentNext, next)) {
                        break;
                    }
                }
                long delay = scheduledTime - now;
                if (delay > 0) {
                    log.info("⏳ Rate Limiter - Delaying AI request for Node '{}' by {}ms to respect API rate limits", nodeTitle, delay);
                    Thread.sleep(delay);
                }

                String responseText = ChatClient.builder(mistralChatModel)
                        .build()
                        .prompt()
                        .user(prompt)
                        .call()
                        .content();

                if (responseText == null || responseText.isBlank()) {
                    throw new RuntimeException("Mistral AI returned an empty response");
                }

                EnrichedNode enriched = parseAndValidateResponse(responseText, lessonsJson);
                
                // Smart description merge: keep the original rich description from admin
                // and append the AI's personalized learning notes at the end
                String originalDesc = nodeDescription != null && !nodeDescription.isBlank() ? nodeDescription.trim() : "";
                String aiNote = enriched.getDescription() != null ? enriched.getDescription().trim() : "";
                
                if (!originalDesc.isEmpty() && !aiNote.isEmpty()) {
                    String checkSnippet = originalDesc.substring(0, Math.min(originalDesc.length(), 20));
                    if (!aiNote.contains(checkSnippet)) {
                        String mergedDesc = originalDesc + "\n\n### 🎯 Hướng dẫn cá nhân hóa cho trình độ " + studentLevel + "\n" + aiNote;
                        enriched.setDescription(mergedDesc);
                    }
                }

                // Smart inheritance: if the admin configured non-empty pristine rubric or expectedOutput
                // and the AI failed to include them or simplified them, make sure we merge/reuse them cleanly
                if (baselineRubric != null && !baselineRubric.isBlank() && 
                        (enriched.getRubric() == null || enriched.getRubric().isBlank() || enriched.getRubric().length() < 30)) {
                    enriched.setRubric(baselineRubric);
                }
                if (baselineExpectedOutput != null && !baselineExpectedOutput.isBlank() && 
                        (enriched.getExpectedOutput() == null || enriched.getExpectedOutput().isBlank() || enriched.getExpectedOutput().length() < 15)) {
                    enriched.setExpectedOutput(baselineExpectedOutput);
                }

                long duration = System.currentTimeMillis() - start;
                log.info("✅ Sequential AI Enrichment Success - Node: '{}' | Attempt: {}/{} | Latency: {}ms", 
                        nodeTitle, attempt, MAX_RETRIES + 1, duration);
                return enriched;

            } catch (Exception e) {
                lastException = e;
                log.warn("⚠️ AI Enrichment Attempt {}/{} failed for Node '{}': {}", 
                        attempt, MAX_RETRIES + 1, nodeTitle, e.getMessage());
                if (attempt <= MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_BACKOFF_MS * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        long duration = System.currentTimeMillis() - start;
        log.error("❌ Sequential AI Enrichment Failed after {} attempts for Node '{}' in {}ms. Reverting to static template fallback.", 
                MAX_RETRIES + 1, nodeTitle, duration, lastException);
        
        return buildStaticFallback(nodeTitle, nodeDescription, baselineExpectedOutput, baselineRubric, 
                skillName, studentLevel, isGap, isStrength, lessonsJson);
    }

    private String buildPrompt(
            String nodeTitle,
            String nodeDescription,
            String baselineExpectedOutput,
            String baselineRubric,
            String skillName,
            String studentLevel,
            String studentGoal,
            boolean isGap,
            boolean isStrength,
            String ragContext,
            String lessonsJson) {

        String evaluatedSkillLabel = "Kỹ năng tiêu chuẩn cần được phát triển";
        if (isGap) {
            evaluatedSkillLabel = "Đây là kĩ năng bị HỔNG (Gap) cần bù đắp bổ sung nền tảng từ cơ bản";
        } else if (isStrength) {
            evaluatedSkillLabel = "Đây là kỹ năng THẾ MẠNH (Strength) cần nâng cao thành dự án thực chiến hoặc thử thách nâng cao";
        }

        String safeSkill = skillName != null ? skillName : "kỹ năng liên quan";
        String safeDesc = nodeDescription != null && !nodeDescription.isBlank() ? nodeDescription : "Tìm hiểu và thực hành kỹ năng này.";
        String safeOutput = baselineExpectedOutput != null && !baselineExpectedOutput.isBlank() ? baselineExpectedOutput : "Sản phẩm thực hành hoàn thiện đáp ứng yêu cầu bài học.";
        String safeRubric = baselineRubric != null && !baselineRubric.isBlank() ? baselineRubric : "Hoàn thành đầy đủ các yêu cầu bài học, nộp sản phẩm đúng hạn.";

        String ragSection = "";
        if (ragContext != null && !ragContext.isBlank()) {
            ragSection = "=== TÀI LIỆU THAM KHẢO CHUYÊN MÔN (RAG CONTEXT) ===\n" +
                    "Sử dụng các tài liệu chuyên môn chính thức sau đây từ chuyên gia để thiết kế chi tiết hướng dẫn học tập, mục tiêu, bài tập và tiêu chí đánh giá:\n" +
                    ragContext + "\n\n";
        }

        String lessonsSection = "";
        if (lessonsJson != null && !lessonsJson.isBlank()) {
            lessonsSection = "=== KHUNG BÀI HỌC BAN ĐẦU CỦA ADMIN TEMPLATE (SKELETAL LESSONS) ===\n" +
                    "Đây là danh sách bài học khung do Quản trị viên thiết kế làm sườn cốt lõi. Tuyệt đối không xóa bỏ hay thay đổi các chủ đề cốt lõi này:\n" +
                    lessonsJson + "\n\n";
        }

        return "Bạn là một chuyên gia đào tạo lập trình thực tế cho SkillVerse.\n" +
                "Nhiệm vụ của bạn là cá nhân hóa và làm giàu chi tiết (enrich/elaborate) nội dung học cho một Node (Bài học) dựa trên Lộ trình mẫu (Template) và hồ sơ năng lực của học viên.\n\n" +
                "=== SƯỜN BÀI HỌC (BLUEPRINT TEMPLATE) ===\n" +
                "- Tiêu đề: " + nodeTitle + "\n" +
                "- Mô tả ban đầu: " + safeDesc + "\n" +
                "- Kỹ năng trọng tâm: " + safeSkill + "\n" +
                "- Khung bài tập mẫu có sẵn: " + safeOutput + "\n" +
                "- Tiêu chí đánh giá có sẵn (Rubric): " + safeRubric + "\n\n" +
                lessonsSection +
                ragSection +
                "=== HỒ SƠ NĂNG LỰC HỌC VIÊN ===\n" +
                "- Trình độ hiện tại: " + studentLevel + "\n" +
                "- Mục tiêu học tập: " + (studentGoal != null ? studentGoal : "Phát triển năng lực cốt lõi") + "\n" +
                "- Đánh giá kỹ năng này: " + evaluatedSkillLabel + "\n\n" +
                "=== YÊU CẦU ĐẦU RA ===\n" +
                "Hãy biên soạn chi tiết và chất lượng cao bằng tiếng Việt (định dạng Markdown):\n" +
                "1. Hướng dẫn học tập cá nhân hóa (description): Viết một hướng dẫn chi tiết và sâu sắc từ 150 đến 250 từ (tối thiểu 150 từ, chia làm 2-3 đoạn văn ngắn), giải thích cụ thể lý do tại sao học viên ở trình độ " + studentLevel + " cần học phần này dựa trên mục tiêu '" + (studentGoal != null ? studentGoal : "Phát triển") + "' và vị thế kỹ năng (" + evaluatedSkillLabel + "). TUYỆT ĐỐI không lặp lại phần 'Mô tả ban đầu' đã có sẵn của Admin, mà chỉ viết thêm phần cá nhân hóa. Cấm viết quá ngắn, sơ sài hoặc dưới 120 từ. Ngoài ra, nếu có phần 'TÀI LIỆU THAM KHẢO CHUYÊN MÔN' ở trên, hãy tích hợp sâu sắc kiến thức chuyên môn từ tài liệu đó để giải thích và làm phong phú thêm bài học.\n" +
                "2. Mục tiêu học tập cụ thể (learningObjectives): Danh sách tối thiểu 3 mục tiêu cụ thể, đo lường được.\n" +
                "3. Bài tập thực hành thực tế (practicalExercises): Thiết kế bài tập thực hành chi tiết, mô tả cụ thể từng bước thực hiện với độ khó tương thích với cấp độ học viên (Beginner/Intermediate/Advanced) và bám sát theo Khung bài tập mẫu có sẵn của chuyên gia. Nếu có tài liệu chuyên môn, hãy lồng ghép các bài thực hành/ví dụ thực tế từ tài liệu đó.\n" +
                "4. Tiêu chí thành công (successCriteria): Danh sách các chỉ số kỹ thuật cụ thể đánh giá mức độ thành công.\n" +
                "5. Mô tả sản phẩm phải nộp (expectedOutput): Nếu Khung bài tập mẫu của Admin đã có sẵn và chi tiết, hãy chỉ trả về chuỗi rỗng (\"\") để kế thừa. Chỉ thiết kế checklist sản phẩm chi tiết dạng Markdown nếu Khung mẫu ban đầu trống hoặc quá sơ sài.\n" +
                "6. Rubric chấm điểm chi tiết (rubric): Nếu Tiêu chí đánh giá (Rubric) của Admin đã có sẵn và chi tiết, hãy chỉ trả về chuỗi rỗng (\"\") để kế thừa. Chỉ thiết kế bảng điểm chi tiết dạng Markdown khi Rubric mẫu ban đầu trống.\n" +
                "7. Chi tiết hóa khung bài học (lessons): Dựa trên KHUNG BÀI HỌC BAN ĐẦU (Skeletal Lessons) của Admin ở trên, hãy GIỮ NGUYÊN chủ đề cốt lõi nhưng CHI TIẾT HÓA/LÀM PHONG PHÚ nội dung từng bài học. Hãy biến mỗi gạch đầu dòng ngắn của Admin thành một bài học chi tiết bao gồm: tiêu đề rõ ràng, mô tả cụ thể người học cần hành động làm gì, mục tiêu bài học cụ thể, và ước tính thời lượng phút học phù hợp với trình độ " + studentLevel + ".\n\n" +
                "Chỉ phản hồi bằng một chuỗi JSON duy nhất, hợp lệ, không chứa ký tự thừa hay giải thích ngoài lề, có định dạng chính xác sau:\n" +
                "{\n" +
                "  \"description\": \"(Phần hướng dẫn cá nhân hóa chi tiết, tối thiểu 150 từ)\",\n" +
                "  \"learningObjectives\": [\"Mục tiêu 1\", \"Mục tiêu 2\", \"Mục tiêu 3\"],\n" +
                "  \"practicalExercises\": [\"Bài tập thực hành chi tiết\"],\n" +
                "  \"successCriteria\": [\"Tiêu chí 1\", \"Tiêu chí 2\"],\n" +
                "  \"expectedOutput\": \"(Checklist sản phẩm dạng Markdown hoặc để rỗng)\",\n" +
                "  \"rubric\": \"(Bảng rubric Markdown hoặc để rỗng)\",\n" +
                "  \"lessons\": [\n" +
                "    {\n" +
                "      \"title\": \"(Tiêu đề bài học đã được chi tiết hóa)\",\n" +
                "      \"description\": \"(Mô tả hành động học chi tiết cho bài học này, khoảng 30-50 từ)\",\n" +
                "      \"learningObjective\": \"(Mục tiêu cụ thể của bài học này)\",\n" +
                "      \"estimatedMinutes\": 60\n" +
                "    }\n" +
                "  ]\n" +
                "}";
    }

    private EnrichedNode parseAndValidateResponse(String responseText, String lessonsJsonFallback) throws Exception {
        String cleanJson = extractJson(responseText);
        JsonNode rootNode = objectMapper.readTree(cleanJson);

        String description = rootNode.path("description").asText("");
        String expectedOutput = rootNode.path("expectedOutput").asText("");
        String rubric = rootNode.path("rubric").asText("");

        List<String> objectives = new ArrayList<>();
        JsonNode objNode = rootNode.path("learningObjectives");
        if (objNode.isArray()) {
            for (JsonNode item : objNode) {
                objectives.add(item.asText());
            }
        }

        List<String> exercises = new ArrayList<>();
        JsonNode exNode = rootNode.path("practicalExercises");
        if (exNode.isArray()) {
            for (JsonNode item : exNode) {
                exercises.add(item.asText());
            }
        }

        List<String> criteria = new ArrayList<>();
        JsonNode crNode = rootNode.path("successCriteria");
        if (crNode.isArray()) {
            for (JsonNode item : crNode) {
                criteria.add(item.asText());
            }
        }

        List<EnrichedLesson> lessons = new ArrayList<>();
        JsonNode lesNode = rootNode.path("lessons");
        if (lesNode.isArray() && lesNode.size() > 0) {
            for (JsonNode item : lesNode) {
                String title = item.path("title").asText("");
                String desc = item.path("description").asText("");
                String obj = item.path("learningObjective").asText("");
                Integer mins = item.has("estimatedMinutes") ? item.path("estimatedMinutes").asInt() : null;
                lessons.add(new EnrichedLesson(title, desc, obj, mins));
            }
        }

        // Fallback if lessons array is empty and lessonsJsonFallback is provided
        if (lessons.isEmpty() && lessonsJsonFallback != null && !lessonsJsonFallback.isBlank()) {
            try {
                JsonNode fallbackArr = objectMapper.readTree(lessonsJsonFallback);
                if (fallbackArr.isArray()) {
                    for (JsonNode item : fallbackArr) {
                        lessons.add(new EnrichedLesson(
                                item.path("title").asText(""),
                                item.path("description").asText(""),
                                item.path("learningObjective").asText(""),
                                item.has("estimatedMinutes") ? item.path("estimatedMinutes").asInt(60) : null
                        ));
                    }
                }
            } catch (Exception ignored) {}
        }

        if (description.isBlank()) {
            throw new IllegalArgumentException("AI output is missing mandatory field: 'description'");
        }

        return new EnrichedNode(description, objectives, exercises, criteria, expectedOutput, rubric, lessons);
    }

    private String extractJson(String text) {
        if (text == null) return "";
        // Match from first '{' to last '}'
        Pattern pattern = Pattern.compile("(?s)\\{.*\\}");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group();
        }
        return text;
    }

    private String translateStudentGoal(String rawGoal) {
        if (rawGoal == null || rawGoal.isBlank()) {
            return "Phát triển năng lực cốt lõi";
        }
        String cleanGoal = rawGoal.trim().toUpperCase();
        switch (cleanGoal) {
            case "EXPLORE":
                return "khám phá";
            case "PREPARE_JOB":
                return "chuẩn bị đi làm";
            case "SWITCH_CAREER":
                return "chuyển ngành";
            case "BUILD_FROM_SCRATCH":
                return "học từ đầu";
            case "UPGRADE_LEVEL":
                return "nâng cao trình độ";
            case "REVIEW":
                return "ôn tập kiến thức";
            default:
                return rawGoal;
        }
    }

    private EnrichedNode buildStaticFallback(
            String nodeTitle,
            String nodeDescription,
            String baselineExpectedOutput,
            String baselineRubric,
            String skillName,
            String studentLevel,
            boolean isGap,
            boolean isStrength,
            String lessonsJson) {

        String fallbackDesc = nodeDescription != null && !nodeDescription.isBlank() ? nodeDescription : "Khóa học thực hành dựa trên template.";
        if (isGap) {
            fallbackDesc += " LƯU Ý: Đánh giá đầu vào xác định đây là một lỗ hổng (gap) năng lực của bạn tại trình độ " + studentLevel + ". Hãy tập trung thực hành kỹ phần này.";
        } else if (isStrength) {
            fallbackDesc += " LƯU Ý: Đây là một thế mạnh (strength) được đánh giá tốt của bạn. Hãy tận dụng bài tập này để xây dựng sản phẩm chất lượng cao đưa vào Portfolio.";
        } else {
            fallbackDesc += " Nội dung được điều chỉnh tốc độ học và ví dụ phù hợp với trình độ " + studentLevel + ".";
        }

        List<String> objectives = new ArrayList<>();
        objectives.add("Nắm vững nền tảng kiến thức về " + nodeTitle);
        if (skillName != null) {
            objectives.add("Áp dụng thực tế kỹ năng " + skillName + " ở cấp độ " + studentLevel);
        }
        if (isGap) {
            objectives.add("Bù đắp lỗ hổng kỹ năng qua bài tập thực tế tự kiểm thử");
        }

        List<String> exercises = new ArrayList<>();
        if (baselineExpectedOutput != null && !baselineExpectedOutput.isBlank()) {
            exercises.add("Hoàn thành bài tập thực tế: " + baselineExpectedOutput);
        } else {
            exercises.add("Thiết kế và cài đặt một sản phẩm thực tế nhỏ áp dụng kiến thức " + nodeTitle);
        }

        List<String> criteria = new ArrayList<>();
        if (baselineRubric != null && !baselineRubric.isBlank()) {
            criteria.add("Tiêu chí đạt: " + baselineRubric);
        } else {
            criteria.add("Sản phẩm chạy ổn định không lỗi cú pháp");
            criteria.add("Đáp ứng đầy đủ các checklist yêu cầu của đề bài");
        }

        List<EnrichedLesson> lessons = new ArrayList<>();
        if (lessonsJson != null && !lessonsJson.isBlank()) {
            try {
                JsonNode arr = objectMapper.readTree(lessonsJson);
                if (arr.isArray()) {
                    for (JsonNode item : arr) {
                        lessons.add(new EnrichedLesson(
                                item.path("title").asText(""),
                                item.path("description").asText(""),
                                item.path("learningObjective").asText(""),
                                item.has("estimatedMinutes") ? item.path("estimatedMinutes").asInt(60) : null
                        ));
                    }
                }
            } catch (Exception ignored) {}
        }

        return new EnrichedNode(
                fallbackDesc,
                objectives,
                exercises,
                criteria,
                baselineExpectedOutput != null ? baselineExpectedOutput : "Checklist sản phẩm hoàn thiện theo yêu cầu bài học.",
                baselineRubric != null ? baselineRubric : "Đánh giá đạt khi hoàn thành đầy đủ các yêu cầu bài tập thực hành."
        );
    }

    private long countWords(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }
}
