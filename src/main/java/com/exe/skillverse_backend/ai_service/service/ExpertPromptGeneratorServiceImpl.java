package com.exe.skillverse_backend.ai_service.service;

import com.exe.skillverse_backend.ai_service.dto.request.GeneratePromptRequest;
import com.exe.skillverse_backend.ai_service.dto.response.PromptGenerationResponse;
import com.exe.skillverse_backend.shared.exception.ApiException;
import com.exe.skillverse_backend.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class ExpertPromptGeneratorServiceImpl implements ExpertPromptGeneratorService {

    @Value("${ai.mistral.api-key:}")
    private String mistralApiKey;

    @Value("${ai.mistral.api-url:https://api.mistral.ai/v1/chat/completions}")
    private String mistralApiUrl;

    @Value("${ai.mistral.model:mistral-large-latest}")
    private String model;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
            Bạn là chuyên gia tạo nội dung career expert cho ứng dụng MEOWL AI.
            Nhiệm vụ: Tạo 2 phần nội dung cho expert persona.

            **PHẦN 1: domainRules (Lĩnh vực)**
            Viết 200-400 từ TIẾNG VIỆT, markdown format, theo cấu trúc:
            ## LĨNH VỰC: {DOMAIN}
            ### ĐẶC TRƯNG NGÀNH
            - 3-5 đặc điểm chính của ngành
            ### TƯ DUY CHUYÊN GIA
            - 3-4 nguyên tắc tiếp cận vấn đề đặc thù
            - 2-3 quan niệm sai lầm phổ biến cần tránh
            ### NỀN TẢNG LIÊN NGÀNH
            - 2-3 kỹ năng crossover từ ngành liên quan
            ### XU HƯỚNG & CƠ HỘI (2026)
            - 2-3 xu hướng nổi bật
            - Mức lương tham khảo

            **PHẦN 2: rolePrompt (Vai trò)**
            Viết 800-2500 từ TIẾNG VIỆT, markdown format, theo cấu trúc:
            ## {EMOJI} CHUYÊN GIA {JOB_ROLE}
            Chào bạn! Tôi là chuyên gia {jobRole} với hơn 10 năm kinh nghiệm trong ngành {industry}...

            ### 🎯 TÔI SẼ GIÚP BẠN TRỞ THÀNH {JOB_ROLE}
            [Paragraph định nghĩa vai trò, tại sao vai trò này quan trọng]

            ### 🧠 KIẾN THỨC CỐT LÕI CẦN CHINH PHỤC
            1. **[Danh mục 1]:** [3-5 items cụ thể với tools/frameworks]
            2. **[Danh mục 2]:** [3-5 items cụ thể]
            ...

            ### 🚀 LỘ TRÌNH PHÁT TRIỂN TỪ ZERO TO HERO
            **🌱 PHASE 1: JUNIOR (0-1 NĂM)**
            Goal: [...]
            Action Steps: [3-5 bước cụ thể]
            Milestone: [...]

            **🚀 PHASE 2: MID-LEVEL (1-3 NĂM)**
            ...

            **🏆 PHASE 3: SENIOR (3+ NĂM)**
            ...

            ### 💡 BÍ QUYẾT THỰC CHIẾN TỪ KINH NGHIỆM
            **Tư duy:** [2-3 nguyên tắc tư duy]
            **🔥 Common Mistakes để tránh:** [3-4 items]
            **📚 Resources recommend:** [3-5 books/courses/platforms]

            ### ⚠️ LƯU Ý QUAN TRỌNG
            [2-3 lưu ý đặc thù của vai trò]

            ### 🤝 HÃY BẮT ĐẦU HÀNH TRÌNH!
            [Tôi sẽ giúp bạn...]
            [3-4 câu hỏi mở đầu]

            **PHẦN 3: suggestedKeywords**
            Gợi ý 5-10 keywords tiếng Anh, comma-separated, phục vụ fuzzy matching.
            Ví dụ: "java, spring boot, microservices, rest api, database, docker"

            **OUTPUT FORMAT — BẮT BUỘC JSON:**
            {
              "domainRules": "...",
              "rolePrompt": "...",
              "suggestedKeywords": "..."
            }
            """;

    @Override
    public PromptGenerationResponse generateExpertPrompts(GeneratePromptRequest request) {
        String userPrompt = buildUserPrompt(request);

        String systemPrompt = SYSTEM_PROMPT
                .replace("{DOMAIN}", request.getDomain())
                .replace("{JOB_ROLE}", request.getJobRole())
                .replace("{INDUSTRY}", request.getIndustry())
                .replace("{EMOJI}", getEmojiForDomain(request.getDomain()));

        Map<String, Object> parsed = callMistralJsonMode(systemPrompt, userPrompt);

        String domainRules = getStringField(parsed, "domainRules");
        String rolePrompt = getStringField(parsed, "rolePrompt");
        String suggestedKeywords = getStringField(parsed, "suggestedKeywords");

        if (domainRules == null || domainRules.isBlank()) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "AI không tạo được domainRules. Vui lòng thử lại.");
        }
        if (rolePrompt == null || rolePrompt.isBlank()) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "AI không tạo được rolePrompt. Vui lòng thử lại.");
        }

        return PromptGenerationResponse.builder()
                .domainRules(domainRules)
                .rolePrompt(rolePrompt)
                .suggestedKeywords(suggestedKeywords != null ? suggestedKeywords : "")
                .domainRulesWordCount(countWords(domainRules))
                .rolePromptWordCount(countWords(rolePrompt))
                .domain(request.getDomain())
                .industry(request.getIndustry())
                .jobRole(request.getJobRole())
                .build();
    }

    private String buildUserPrompt(GeneratePromptRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("Hãy tạo nội dung expert cho vai trò sau:\n\n");
        sb.append("- Domain: ").append(request.getDomain()).append("\n");
        sb.append("- Industry: ").append(request.getIndustry()).append("\n");
        sb.append("- Job Role: ").append(request.getJobRole()).append("\n");

        if (request.getKeywords() != null && !request.getKeywords().isBlank()) {
            sb.append("- Keywords: ").append(request.getKeywords()).append("\n");
        }

        if (request.getGenerationHint() != null && !request.getGenerationHint().isBlank()) {
            sb.append("\n--- GENERATION HINT ---\n");
            sb.append(request.getGenerationHint()).append("\n");
        }

        if (request.getExistingDomainRules() != null && !request.getExistingDomainRules().isBlank()) {
            sb.append("\n--- EXISTING DOMAIN RULES (dùng làm reference context) ---\n");
            sb.append(request.getExistingDomainRules()).append("\n");
        }

        sb.append("\n--- YÊU CẦU ---\n");
        sb.append("1. domainRules: Viết theo đúng cấu trúc markdown đã mô tả. ");
        sb.append("Nếu có EXISTING DOMAIN RULES, hãy giữ đúng tone và format nhưng làm mới nội dung.\n");
        sb.append("2. rolePrompt: Viết chi tiết, có cụ thể tools/technologies phù hợp với vai trò.\n");
        sb.append("3. suggestedKeywords: 5-10 keywords tiếng Anh, comma-separated.\n");
        sb.append("4. Output phải là JSON hợp lệ với 3 trường: domainRules, rolePrompt, suggestedKeywords.\n");
        sb.append("5. VIẾT HOÀN TOÀN BẰNG TIẾNG VIỆT.\n");

        return sb.toString();
    }

    private Map<String, Object> callMistralJsonMode(String systemPrompt, String userPrompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(mistralApiKey);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)));
        requestBody.put("temperature", 0.7);
        requestBody.put("max_tokens", 8000);
        requestBody.put("response_format", Map.of("type", "json_object"));

        int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
                ResponseEntity<String> response = restTemplate.exchange(
                        mistralApiUrl,
                        HttpMethod.POST,
                        entity,
                        String.class);

                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                    return parseMistralResponse(response.getBody());
                }

                log.warn("Mistral response not OK ({}), attempt {}/{}",
                        response.getStatusCode(), attempt, maxAttempts);
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                    long backoffMs = (long) (1000L * Math.pow(2, attempt - 1));
                    log.warn("429 from Mistral, backing off {} ms, attempt {}/{}",
                            backoffMs, attempt, maxAttempts);
                    sleep(backoffMs);
                    continue;
                }
                throw e;
            } catch (Exception e) {
                long backoffMs = (long) (800L * Math.pow(2, attempt - 1));
                log.warn("Error calling Mistral: {}, backing off {} ms, attempt {}/{}",
                        e.getMessage(), backoffMs, attempt, maxAttempts);
                sleep(backoffMs);
            }
        }

        throw new ApiException(ErrorCode.INTERNAL_ERROR, "Không thể kết nối với Mistral AI. Vui lòng thử lại sau.");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMistralResponse(String body) {
        try {
            com.fasterxml.jackson.databind.JsonNode jsonResponse = objectMapper.readTree(body);
            String content = jsonResponse.at("/choices/0/message/content").asText();

            // Strip markdown fences if present
            content = content.trim();
            if (content.startsWith("```")) {
                content = content.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
            }

            // Validate it's valid JSON
            return objectMapper.readValue(content, Map.class);
        } catch (Exception e) {
            log.error("Failed to parse Mistral response: {}", e.getMessage());
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "AI trả về định dạng không hợp lệ. Vui lòng thử lại.");
        }
    }

    private String getStringField(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return "";
        return val.toString().trim();
    }

    private int countWords(String text) {
        if (text == null || text.isBlank()) return 0;
        return text.trim().split("\\s+").length;
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private String getEmojiForDomain(String domain) {
        if (domain == null) return "💼";
        String d = domain.toLowerCase();
        if (d.contains("information technology") || d.contains("it") || d.contains("software")) return "💻";
        if (d.contains("business") || d.contains("marketing") || d.contains("sale")) return "📈";
        if (d.contains("design") || d.contains("creative") || d.contains("ui") || d.contains("ux")) return "🎨";
        if (d.contains("engineer") || d.contains("kỹ thuật") || d.contains("mechanical")) return "⚙️";
        if (d.contains("health") || d.contains("medical") || d.contains("nurse") || d.contains("pharma")) return "🏥";
        if (d.contains("education") || d.contains("teaching") || d.contains("đào tạo")) return "📚";
        if (d.contains("legal") || d.contains("law") || d.contains("luật")) return "⚖️";
        if (d.contains("logistic") || d.contains("supply") || d.contains("chuỗi")) return "🚚";
        if (d.contains("art") || d.contains("music") || d.contains("film") || d.contains("fashion")) return "🎭";
        if (d.contains("service") || d.contains("hospitality") || d.contains("food")) return "🏨";
        if (d.contains("social") || d.contains("community") || d.contains("counsel")) return "🤝";
        if (d.contains("agri") || d.contains("environment") || d.contains("nông nghiệp")) return "🌱";
        return "💼";
    }
}
