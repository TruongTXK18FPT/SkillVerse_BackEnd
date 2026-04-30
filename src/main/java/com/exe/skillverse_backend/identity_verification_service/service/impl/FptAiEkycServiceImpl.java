package com.exe.skillverse_backend.identity_verification_service.service.impl;

import com.exe.skillverse_backend.identity_verification_service.dto.IdCardExtractionResult;
import com.exe.skillverse_backend.identity_verification_service.service.FptAiEkycService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class FptAiEkycServiceImpl implements FptAiEkycService {

    @Value("${FPT_AI_KEY}")
    private String fptAiKey;

    private static final String FPT_AI_IDR_URL = "https://api.fpt.ai/vision/idr/vnm";
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;

    @Override
    public IdCardExtractionResult extractIdCardInfo(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            return IdCardExtractionResult.builder()
                    .success(false)
                    .errorMessage("Image file is empty or missing")
                    .build();
        }
        try {
            return extractIdCardInfo(image.getBytes(), image.getOriginalFilename());
        } catch (Exception e) {
            return IdCardExtractionResult.builder()
                    .success(false)
                    .errorMessage("Failed to read image bytes: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public IdCardExtractionResult extractIdCardInfo(byte[] imageBytes, String filename) {
        if (imageBytes == null || imageBytes.length == 0) {
            return IdCardExtractionResult.builder()
                    .success(false)
                    .errorMessage("Image bytes are empty")
                    .build();
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.set("api-key", fptAiKey);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            
            // Wrap the byte array into a ByteArrayResource so RestTemplate can send it correctly
            ByteArrayResource fileResource = new ByteArrayResource(imageBytes) {
                @Override
                public String getFilename() {
                    return filename != null ? filename : "image.jpg";
                }
            };
            
            body.add("image", fileResource);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            log.info("Sending request to FPT.AI IDR API...");
            ResponseEntity<String> response = restTemplate.postForEntity(FPT_AI_IDR_URL, requestEntity, String.class);
            
            String responseBody = response.getBody();
            log.debug("FPT.AI Response: {}", responseBody);

            return parseFptAiResponse(responseBody);

        } catch (Exception e) {
            log.error("Error communicating with FPT.AI API", e);
            return IdCardExtractionResult.builder()
                    .success(false)
                    .errorMessage("Failed to communicate with FPT.AI: " + e.getMessage())
                    .build();
        }
    }

    private IdCardExtractionResult parseFptAiResponse(String jsonResponse) {
        try {
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            
            // Check for error code in response
            int errorCode = rootNode.path("errorCode").asInt();
            if (errorCode != 0) {
                String errorMessage = rootNode.path("errorMessage").asText();
                return IdCardExtractionResult.builder()
                        .success(false)
                        .errorMessage("FPT.AI Error (" + errorCode + "): " + errorMessage)
                        .rawJson(jsonResponse)
                        .build();
            }

            JsonNode dataNode = rootNode.path("data").get(0); // Data is usually an array
            
            if (dataNode == null || dataNode.isMissingNode()) {
                return IdCardExtractionResult.builder()
                        .success(false)
                        .errorMessage("No data returned from FPT.AI")
                        .rawJson(jsonResponse)
                        .build();
            }

            IdCardExtractionResult result = IdCardExtractionResult.builder()
                    .success(true)
                    .rawJson(jsonResponse)
                    .build();

            // Extract fields based on FPT.AI documentation
            // Format is usually: "id": "07909900xxxx", "id_prob": 99.8
            // We use path("field").asText(null) to safely get values
            
            // Front side fields
            if (dataNode.has("id")) {
                result.setIdNumber(dataNode.path("id").asText(null));
                result.setCardType("FRONT"); // Assuming front if ID is present
            }
            if (dataNode.has("name")) {
                result.setFullName(dataNode.path("name").asText(null));
            }
            if (dataNode.has("dob")) {
                result.setDob(dataNode.path("dob").asText(null));
            }
            if (dataNode.has("sex")) {
                result.setSex(dataNode.path("sex").asText(null));
            }
            if (dataNode.has("nationality")) {
                result.setNationality(dataNode.path("nationality").asText(null));
            }
            if (dataNode.has("home")) {
                result.setPlaceOfOrigin(dataNode.path("home").asText(null));
            }
            if (dataNode.has("address")) {
                result.setPlaceOfResidence(dataNode.path("address").asText(null));
            }
            if (dataNode.has("doe")) {
                result.setExpiryDate(dataNode.path("doe").asText(null));
            }

            // Back side fields
            if (dataNode.has("issue_date")) {
                result.setIssueDate(dataNode.path("issue_date").asText(null));
                result.setCardType("BACK"); // Assuming back if issue_date is present
            }
            if (dataNode.has("issue_loc")) {
                result.setIssueLoc(dataNode.path("issue_loc").asText(null));
            }

            // Fallback for card type if explicit field exists in some API versions
            if (dataNode.has("type")) {
                String type = dataNode.path("type").asText("");
                if (type.contains("front") || type.contains("front_new")) {
                    result.setCardType("FRONT");
                } else if (type.contains("back")) {
                    result.setCardType("BACK");
                }
            }

            return result;
        } catch (Exception e) {
            log.error("Failed to parse FPT.AI response: " + jsonResponse, e);
            return IdCardExtractionResult.builder()
                    .success(false)
                    .errorMessage("Failed to parse FPT.AI response: " + e.getMessage())
                    .rawJson(jsonResponse)
                    .build();
        }
    }
}
