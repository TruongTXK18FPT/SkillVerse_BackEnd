package com.exe.skillverse_backend.ai_service.service;

import com.exe.skillverse_backend.ai_service.config.MistralProperties;
import com.exe.skillverse_backend.shared.exception.ApiException;
import com.exe.skillverse_backend.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Service for interacting with Mistral AI API
 * Specialized for latest career trends and up-to-date information
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "mistral.api", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MistralAiService {

    private final MistralProperties mistralProperties;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Call Mistral AI API with the given prompt
     * 
     * @param prompt The user's prompt/question
     * @return AI-generated response
     */
    public String callMistralAPI(String prompt) {
        try {
            log.info("Calling Mistral AI API with model: {}", mistralProperties.getModel());

            // Prepare request headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + mistralProperties.getKey());

            // Prepare request body (Mistral Chat Completions format)
            Map<String, Object> requestBody = Map.of(
                    "model", mistralProperties.getModel(),
                    "messages", List.of(
                            Map.of(
                                    "role", "user",
                                    "content", prompt)),
                    "max_tokens", mistralProperties.getMaxTokens(),
                    "temperature", mistralProperties.getTemperature());

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

            // Make API call
            ResponseEntity<String> response = restTemplate.postForEntity(
                    mistralProperties.getBaseUrl(),
                    requestEntity,
                    String.class);

            // Parse response
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode choices = root.path("choices");

                if (choices.isArray() && !choices.isEmpty()) {
                    String content = choices.get(0)
                            .path("message")
                            .path("content")
                            .asText();

                    log.info("Successfully received response from Mistral AI");
                    return content;
                }
            }

            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Failed to parse Mistral AI response");

        } catch (HttpClientErrorException.TooManyRequests e) {
            log.error("Mistral AI rate limit exceeded: {}", e.getMessage());
            throw new ApiException(
                    ErrorCode.SERVICE_UNAVAILABLE,
                    "Mistral AI rate limit exceeded. Please try again later.");
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("Mistral AI API error: {} - {}", e.getStatusCode(), e.getMessage());
            throw new ApiException(
                    ErrorCode.SERVICE_UNAVAILABLE,
                    "Mistral AI service error: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error calling Mistral AI: {}", e.getMessage(), e);
            throw new ApiException(
                    ErrorCode.INTERNAL_ERROR,
                    "Failed to communicate with Mistral AI");
        }
    }

    /**
     * Check if Mistral AI service is enabled
     */
    public boolean isEnabled() {
        return mistralProperties.getEnabled() != null && mistralProperties.getEnabled();
    }

    /**
     * Get current Mistral model name
     */
    public String getCurrentModel() {
        return mistralProperties.getModel();
    }
}
