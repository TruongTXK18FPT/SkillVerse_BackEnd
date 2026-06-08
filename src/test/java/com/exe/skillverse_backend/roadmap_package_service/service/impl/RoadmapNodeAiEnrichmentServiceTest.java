package com.exe.skillverse_backend.roadmap_package_service.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.exe.skillverse_backend.roadmap_package_service.service.RoadmapNodeAiEnrichmentService;
import com.exe.skillverse_backend.ai_service.service.LocalAiGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

@ExtendWith(MockitoExtension.class)
class RoadmapNodeAiEnrichmentServiceTest {

    @Mock
    private ChatModel mistralChatModel;

    private ObjectMapper objectMapper;
    private RoadmapNodeAiEnrichmentServiceImpl service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new RoadmapNodeAiEnrichmentServiceImpl(mistralChatModel, objectMapper);
    }

    @Test
    void enrichNodeSuccessfulWithJsonFormat() {
        String jsonText = "{\n"
                + "  \"description\": \"Học Java căn bản cho người mới bắt đầu bao gồm JVM, JRE, JDK và cú pháp.\",\n"
                + "  \"learningObjectives\": [\"Hiểu JVM hoạt động thế nào\", \"Viết được chương trình HelloWorld\"],\n"
                + "  \"practicalExercises\": [\"Viết ứng dụng Calculator đơn giản\"],\n"
                + "  \"successCriteria\": [\"Ứng dụng chạy không lỗi\", \"Cú pháp chuẩn chỉnh\"],\n"
                + "  \"expectedOutput\": \"- Mã nguồn chương trình Calculator\\n- Ảnh chụp kết quả màn hình\",\n"
                + "  \"rubric\": \"- Đúng yêu cầu: 5đ\\n- Code sạch: 5đ\"\n"
                + "}";

        // Mock ChatResponse and Generation hierarchy
        ChatResponse chatResponse = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);
        AssistantMessage assistantMessage = mock(AssistantMessage.class);

        when(mistralChatModel.call(any(Prompt.class))).thenReturn(chatResponse);
        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(assistantMessage);
        when(assistantMessage.getContent()).thenReturn(jsonText);

        RoadmapNodeAiEnrichmentService.EnrichedNode result = service.enrichNode(
                "Java Basics",
                "Học Java cơ bản",
                "Baseline Output",
                "Baseline Rubric",
                "Java Core",
                "BEGINNER",
                "LEARN_BASIC",
                true,
                false
        );

        assertThat(result).isNotNull();
        assertThat(result.getDescription()).contains("Học Java căn bản");
        assertThat(result.getLearningObjectives()).hasSize(2).contains("Hiểu JVM hoạt động thế nào");
        assertThat(result.getPracticalExercises()).hasSize(1).contains("Viết ứng dụng Calculator đơn giản");
        assertThat(result.getSuccessCriteria()).hasSize(2).contains("Ứng dụng chạy không lỗi");
        assertThat(result.getExpectedOutput()).contains("Mã nguồn chương trình Calculator");
        assertThat(result.getRubric()).contains("Đúng yêu cầu: 5đ");
    }

    @Test
    void enrichNodeRendersStructuredRubricItemsToMarkdown() {
        String jsonText = "{\n"
                + "  \"description\": \"Học database persistence qua thiết kế schema, repository và transaction.\",\n"
                + "  \"learningObjectives\": [\"Thiết kế schema\", \"Viết repository\", \"Quản lý transaction\"],\n"
                + "  \"practicalExercises\": [\"Tạo database cho ứng dụng học tập\"],\n"
                + "  \"successCriteria\": [\"Schema chạy được\", \"Có quan hệ rõ ràng\"],\n"
                + "  \"expectedOutput\": \"Nộp source code và sơ đồ database.\",\n"
                + "  \"rubricItems\": [\n"
                + "    {\n"
                + "      \"criterion\": \"Thiết kế database schema\",\n"
                + "      \"weight\": \"30%\",\n"
                + "      \"passDescription\": \"Có bảng, quan hệ, khóa chính/phụ rõ ràng\",\n"
                + "      \"failDescription\": \"Thiếu quan hệ hoặc schema không chạy được\"\n"
                + "    },\n"
                + "    {\n"
                + "      \"criterion\": \"Repository implementation\",\n"
                + "      \"weight\": \"40%\",\n"
                + "      \"passDescription\": \"CRUD hoạt động và có truy vấn cần thiết\",\n"
                + "      \"failDescription\": \"Repository lỗi hoặc thiếu thao tác chính\"\n"
                + "    }\n"
                + "  ]\n"
                + "}";

        ChatResponse chatResponse = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);
        AssistantMessage assistantMessage = mock(AssistantMessage.class);

        when(mistralChatModel.call(any(Prompt.class))).thenReturn(chatResponse);
        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(assistantMessage);
        when(assistantMessage.getContent()).thenReturn(jsonText);

        RoadmapNodeAiEnrichmentService.EnrichedNode result = service.enrichNode(
                "Database & Persistence",
                "Làm việc với database trong backend",
                "Baseline Output",
                "Baseline Rubric configured by Admin with enough detail",
                "Database",
                "BEGINNER",
                "BUILD_FROM_SCRATCH",
                true,
                false
        );

        assertThat(result.getRubric()).contains("| Tiêu chí | Trọng số/Điểm | Đạt | Chưa đạt |");
        assertThat(result.getRubric()).contains("| Thiết kế database schema | 30% | Có bảng, quan hệ, khóa chính/phụ rõ ràng | Thiếu quan hệ hoặc schema không chạy được |");
        assertThat(result.getRubric()).contains("| Repository implementation | 40% | CRUD hoạt động và có truy vấn cần thiết | Repository lỗi hoặc thiếu thao tác chính |");
    }

    @Test
    void enrichNodePromptRequestsStructuredRubricItemsInsteadOfMarkdownRubricString() {
        String jsonText = "{\n"
                + "  \"description\": \"Học REST API theo từng bước thực hành.\",\n"
                + "  \"learningObjectives\": [\"Hiểu REST\", \"Thiết kế endpoint\", \"Kiểm thử API\"],\n"
                + "  \"practicalExercises\": [\"Tạo API CRUD\"],\n"
                + "  \"successCriteria\": [\"API chạy đúng\"],\n"
                + "  \"expectedOutput\": \"Nộp source code API.\",\n"
                + "  \"rubricItems\": [\n"
                + "    {\"criterion\": \"REST design\", \"weight\": \"50%\", \"passDescription\": \"Endpoint rõ ràng\", \"failDescription\": \"Endpoint sai chuẩn\"}\n"
                + "  ]\n"
                + "}";

        ChatResponse chatResponse = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);
        AssistantMessage assistantMessage = mock(AssistantMessage.class);

        when(mistralChatModel.call(any(Prompt.class))).thenReturn(chatResponse);
        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(assistantMessage);
        when(assistantMessage.getContent()).thenReturn(jsonText);

        service.enrichNode(
                "REST API",
                "Thiết kế API chuẩn RESTful",
                "Original Out",
                "Original Rubric",
                "RESTful API",
                "INTERMEDIATE",
                "BUILD_PORTFOLIO",
                false,
                true
        );

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(mistralChatModel).call(promptCaptor.capture());
        String promptText = promptCaptor.getValue().getContents();

        assertThat(promptText).contains("\"rubricItems\"");
        assertThat(promptText).doesNotContain("\"rubric\": \"(Bảng rubric Markdown bắt buộc)\"");
    }

    @Test
    void enrichNodeCleansMarkdownTicksAndLeadingTrailingJunk() {
        String wrappedJsonText = "```json\n"
                + "{\n"
                + "  \"description\": \"Học REST API chi tiết.\",\n"
                + "  \"learningObjectives\": [\"Hiểu REST\"],\n"
                + "  \"practicalExercises\": [\"Tạo API GET\"],\n"
                + "  \"successCriteria\": [\"API chạy tốt\"],\n"
                + "  \"expectedOutput\": \"- Link to deployed API on Render and Postman collection check\",\n"
                + "  \"rubric\": \"- Đúng chuẩn REST: 5đ\\n- Đầy đủ HTTP Method: 3đ\\n- Validation đầu vào: 2đ\"\n"
                + "}\n"
                + "```";

        ChatResponse chatResponse = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);
        AssistantMessage assistantMessage = mock(AssistantMessage.class);

        when(mistralChatModel.call(any(Prompt.class))).thenReturn(chatResponse);
        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(assistantMessage);
        when(assistantMessage.getContent()).thenReturn(wrappedJsonText);

        RoadmapNodeAiEnrichmentService.EnrichedNode result = service.enrichNode(
                "REST API",
                "Thiết kế API chuẩn RESTful",
                "Original Out",
                "Original Rubric",
                "RESTful API",
                "INTERMEDIATE",
                "BUILD_PORTFOLIO",
                false,
                true
        );

        assertThat(result).isNotNull();
        assertThat(result.getDescription()).contains("Học REST API chi tiết.");
        assertThat(result.getLearningObjectives()).containsExactly("Hiểu REST");
        assertThat(result.getPracticalExercises()).containsExactly("Tạo API GET");
        assertThat(result.getSuccessCriteria()).containsExactly("API chạy tốt");
        assertThat(result.getExpectedOutput()).isEqualTo("- Link to deployed API on Render and Postman collection check");
        assertThat(result.getRubric()).isEqualTo("- Đúng chuẩn REST: 5đ\n- Đầy đủ HTTP Method: 3đ\n- Validation đầu vào: 2đ");
    }

    @Test
    void enrichNodeInheritsBaselineExpectedOutputAndRubricWhenAiReturnsEmptyOrTooShort() {
        String minimalJsonText = "{\n"
                + "  \"description\": \"Học Docker container.\",\n"
                + "  \"learningObjectives\": [\"Hiểu Docker image\"],\n"
                + "  \"practicalExercises\": [\"Chạy container Nginx\"],\n"
                + "  \"successCriteria\": [\"Container hoạt động\"],\n"
                + "  \"expectedOutput\": \"\",\n"
                + "  \"rubric\": \"\"\n"
                + "}";

        ChatResponse chatResponse = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);
        AssistantMessage assistantMessage = mock(AssistantMessage.class);

        when(mistralChatModel.call(any(Prompt.class))).thenReturn(chatResponse);
        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(assistantMessage);
        when(assistantMessage.getContent()).thenReturn(minimalJsonText);

        String baselineOutput = "Baseline Expected Output configured by Admin";
        String baselineRubric = "Baseline Rubric configured by Admin: Must contain dockerfile and clean build";

        RoadmapNodeAiEnrichmentService.EnrichedNode result = service.enrichNode(
                "Docker Basics",
                "Học Docker cơ bản",
                baselineOutput,
                baselineRubric,
                "DevOps",
                "ADVANCED",
                "MASTER",
                false,
                false
        );

        assertThat(result).isNotNull();
        assertThat(result.getDescription()).contains("Học Docker container.");
        assertThat(result.getExpectedOutput()).isEqualTo(baselineOutput);
        assertThat(result.getRubric()).isEqualTo(baselineRubric);
    }

    @Test
    void enrichNodeFallsBackToStaticFallbackWhenAiThrowsException() {
        when(mistralChatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("Mistral AI Service Unavailable"));

        String baselineOutput = "Baseline Output Check";
        String baselineRubric = "Baseline Rubric Scale";

        RoadmapNodeAiEnrichmentService.EnrichedNode result = service.enrichNode(
                "Spring JPA",
                "Học lập trình Spring Data JPA",
                baselineOutput,
                baselineRubric,
                "Spring Boot",
                "INTERMEDIATE",
                "LEVEL_UP",
                true, // Gap
                false
        );

        assertThat(result).isNotNull();
        assertThat(result.getDescription()).contains("Spring Data JPA");
        assertThat(result.getDescription()).contains("LƯU Ý: Đánh giá đầu vào xác định đây là một lỗ hổng (gap)");
        assertThat(result.getLearningObjectives()).anyMatch(obj -> obj.contains("Spring JPA"));
        assertThat(result.getPracticalExercises()).contains("Hoàn thành bài tập thực tế: " + baselineOutput);
        assertThat(result.getSuccessCriteria()).contains("Tiêu chí đạt: " + baselineRubric);
        assertThat(result.getExpectedOutput()).isEqualTo(baselineOutput);
        assertThat(result.getRubric()).isEqualTo(baselineRubric);
    }

    @Test
    void enrichNodeFallsBackToStaticFallbackWhenAiReturnsInvalidJson() {
        ChatResponse chatResponse = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);
        AssistantMessage assistantMessage = mock(AssistantMessage.class);

        when(mistralChatModel.call(any(Prompt.class))).thenReturn(chatResponse);
        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(assistantMessage);
        when(assistantMessage.getContent()).thenReturn("{invalid-json-content-here}");

        RoadmapNodeAiEnrichmentService.EnrichedNode result = service.enrichNode(
                "Spring Security",
                "Học lập trình Spring Security bảo mật ứng dụng",
                "Auth Check",
                "Auth Rubric",
                "Spring Security",
                "ADVANCED",
                "SECURE",
                false,
                true // Strength
        );

        assertThat(result).isNotNull();
        assertThat(result.getDescription()).contains("Spring Security");
        assertThat(result.getDescription()).contains("LƯU Ý: Đây là một thế mạnh (strength)");
        assertThat(result.getLearningObjectives()).anyMatch(obj -> obj.contains("Spring Security"));
        assertThat(result.getExpectedOutput()).isEqualTo("Auth Check");
        assertThat(result.getRubric()).isEqualTo("Auth Rubric");
    }

    @Test
    void enrichNodeSuccessfulWithLocalAi() throws Exception {
        LocalAiGateway localAiGateway = mock(LocalAiGateway.class);
        when(localAiGateway.isAvailable()).thenReturn(true);
        
        String jsonText = "{\n"
                + "  \"description\": \"Học Java căn bản với Local AI.\",\n"
                + "  \"learningObjectives\": [\"Hiểu JVM\", \"Viết được chương trình HelloWorld\"],\n"
                + "  \"practicalExercises\": [\"Viết Calculator\"],\n"
                + "  \"successCriteria\": [\"Ứng dụng chạy ok\"],\n"
                + "  \"expectedOutput\": \"Mã nguồn chương trình Calculator\",\n"
                + "  \"rubric\": \"Đúng yêu cầu: 10đ\"\n"
                + "}";
        ChatResponse localChatResponse = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);
        AssistantMessage assistantMessage = mock(AssistantMessage.class);
        when(localChatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(assistantMessage);
        when(assistantMessage.getContent()).thenReturn(jsonText);
        when(localAiGateway.callWithoutSemaphoreForResponse(any(), anyString())).thenReturn(localChatResponse);

        java.lang.reflect.Field field = RoadmapNodeAiEnrichmentServiceImpl.class.getDeclaredField("localAiGateway");
        field.setAccessible(true);
        field.set(service, localAiGateway);

        RoadmapNodeAiEnrichmentService.EnrichedNode result = service.enrichNode(
                "Java Basics",
                "Học Java cơ bản",
                "Baseline Output",
                "Baseline Rubric",
                "Java Core",
                "BEGINNER",
                "LEARN_BASIC",
                true,
                false
        );

        assertThat(result).isNotNull();
        assertThat(result.getDescription()).contains("Học Java căn bản với Local AI");
        assertThat(result.getExpectedOutput()).isEqualTo("Mã nguồn chương trình Calculator");
        verify(localAiGateway).callWithoutSemaphoreForResponse(any(), anyString());
    }

    @Test
    void enrichNodeFallsBackToMistralWhenLocalAiFails() throws Exception {
        LocalAiGateway localAiGateway = mock(LocalAiGateway.class);
        when(localAiGateway.isAvailable()).thenReturn(true);
        when(localAiGateway.callWithoutSemaphoreForResponse(any(), anyString())).thenThrow(new RuntimeException("Local AI Timeout"));

        String jsonText = "{\n"
                + "  \"description\": \"Học Java căn bản với Mistral Fallback.\",\n"
                + "  \"learningObjectives\": [\"Hiểu JVM\"],\n"
                + "  \"practicalExercises\": [\"Viết Calculator\"],\n"
                + "  \"successCriteria\": [\"Ứng dụng chạy ok\"],\n"
                + "  \"expectedOutput\": \"Mã nguồn Calculator\",\n"
                + "  \"rubric\": \"Đúng yêu cầu: 10đ\"\n"
                + "}";

        ChatResponse chatResponse = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);
        AssistantMessage assistantMessage = mock(AssistantMessage.class);

        when(mistralChatModel.call(any(Prompt.class))).thenReturn(chatResponse);
        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(assistantMessage);
        when(assistantMessage.getContent()).thenReturn(jsonText);

        java.lang.reflect.Field field = RoadmapNodeAiEnrichmentServiceImpl.class.getDeclaredField("localAiGateway");
        field.setAccessible(true);
        field.set(service, localAiGateway);

        RoadmapNodeAiEnrichmentService.EnrichedNode result = service.enrichNode(
                "Java Basics",
                "Học Java cơ bản",
                "Baseline Output",
                "Baseline Rubric",
                "Java Core",
                "BEGINNER",
                "LEARN_BASIC",
                true,
                false
        );

        assertThat(result).isNotNull();
        assertThat(result.getDescription()).contains("Học Java căn bản với Mistral Fallback");
        verify(localAiGateway).callWithoutSemaphoreForResponse(any(), anyString());
        verify(mistralChatModel).call(any(Prompt.class));
    }
}
