package com.exe.skillverse_backend.portfolio_service.service.impl;

import com.exe.skillverse_backend.portfolio_service.dto.*;
import com.exe.skillverse_backend.portfolio_service.service.CVGeneratorAIService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CVGeneratorAIServiceImpl implements CVGeneratorAIService {

    @Value("${portfolio.ai.mistral.api-key:RyNJ2HEDKf6PCPIyMyqApfn5tWlODkqC}")
    private String mistralApiKey;

    @Value("${portfolio.ai.mistral.api-url:https://api.mistral.ai/v1/chat/completions}")
    private String mistralApiUrl;

    @Value("${portfolio.ai.mistral.model:mistral-large-latest}")
    private String model;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * Generate CV content using Mistral AI based on user's portfolio data.
     * Now returns structured JSON instead of HTML - much less token usage.
     */
    public String generateCV(
            UserProfileDTO profile,
            List<PortfolioProjectDTO> projects,
            List<ExternalCertificateDTO> certificates,
            List<MentorReviewDTO> reviews,
            CVGenerationRequest request) {
        try {
            String prompt = buildCVPrompt(profile, projects, certificates, reviews, request);
            log.info("Generating CV JSON with Mistral AI for user: {}", profile.getUserId());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(mistralApiKey);

            String systemPrompt = buildSystemPrompt(request.getTemplateName());

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", prompt)));
            requestBody.put("temperature", 0.4); // Lower temperature for more consistent JSON
            requestBody.put("max_tokens", 3000); // JSON is more compact than HTML
            requestBody.put("response_format", Map.of("type", "json_object")); // Force JSON output

            // Try with primary model, retry on 429, then fallback once
            String[] modelsToTry = new String[] { model, "mistral-small-latest" };
            for (int m = 0; m < modelsToTry.length; m++) {
                String currentModel = modelsToTry[m];
                requestBody.put("model", currentModel);

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
                            JsonNode jsonResponse = objectMapper.readTree(response.getBody());
                            String cvContent = jsonResponse.at("/choices/0/message/content").asText();

                            // Clean any markdown fences that might slip through
                            cvContent = cvContent.trim();
                            if (cvContent.startsWith("```")) {
                                cvContent = cvContent.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
                            }

                            // Validate it's valid JSON
                            objectMapper.readTree(cvContent);

                            log.info("CV JSON generated successfully for user: {} with model {}",
                                    profile.getUserId(), currentModel);
                            return cvContent;
                        }

                        log.warn("Mistral response not OK ({}), attempt {}/{} with model {}",
                                response.getStatusCode(), attempt, maxAttempts, currentModel);
                    } catch (HttpClientErrorException e) {
                        if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                            long backoffMs = (long) (1000L * Math.pow(2, attempt - 1));
                            log.warn("429 from Mistral (model {}), backing off {} ms, attempt {}/{}",
                                    currentModel, backoffMs, attempt, maxAttempts);
                            try {
                                Thread.sleep(backoffMs);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                            }
                            continue;
                        }
                        throw e;
                    } catch (Exception ex) {
                        long backoffMs = (long) (800L * Math.pow(2, attempt - 1));
                        log.warn("Transient error calling Mistral (model {}): {}. Backoff {} ms, attempt {}/{}",
                                currentModel, ex.getMessage(), backoffMs, attempt, maxAttempts);
                        try {
                            Thread.sleep(backoffMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }

                log.warn("Exhausted retries for model {}.{}", currentModel,
                        (m < modelsToTry.length - 1 ? " Trying fallback model..." : ""));
            }

            throw new RuntimeException("Failed to generate CV after retries and fallback model");

        } catch (Exception e) {
            log.error("Error generating CV with Mistral AI", e);
            throw new RuntimeException("Failed to generate CV: " + e.getMessage(), e);
        }
    }

    /**
     * Build system prompt - NOW generates structured JSON instead of HTML
     * This dramatically reduces token usage and produces consistent output
     */
    private String buildSystemPrompt(String templateName) {
        return """
                You are an expert CV writer and career consultant.
                Your task is to enhance and organize the candidate's data into a professional CV structure.

                CRITICAL: Return ONLY valid JSON matching this exact schema. No markdown, no explanations, no code fences.

                {
                  "personalInfo": {
                    "fullName": "string",
                    "professionalTitle": "string",
                    "email": "string",
                    "phone": "string",
                    "location": "string",
                    "linkedinUrl": "string or null",
                    "githubUrl": "string or null",
                    "portfolioUrl": "string or null",
                    "behanceUrl": "string or null",
                    "dribbbleUrl": "string or null"
                  },
                  "summary": "A compelling 2-4 sentence professional summary highlighting key strengths",
                  "experience": [
                    {
                      "id": "exp_1",
                      "title": "Job Title",
                      "company": "Company Name",
                      "location": "City, Country",
                      "startDate": "MM/YYYY",
                      "endDate": "MM/YYYY or null if current",
                      "isCurrent": false,
                      "description": "Brief role description",
                      "achievements": ["Quantified achievement 1", "Achievement 2"],
                      "technologies": ["Tech1", "Tech2"]
                    }
                  ],
                  "education": [
                    {
                      "id": "edu_1",
                      "degree": "Degree Name",
                      "institution": "University Name",
                      "location": "City",
                      "startDate": "YYYY",
                      "endDate": "YYYY",
                      "gpa": "3.8/4.0 or null",
                      "relevantCourses": []
                    }
                  ],
                  "skills": [
                    {
                      "category": "Category Name",
                      "skills": [
                        { "name": "Skill Name", "level": 4 }
                      ]
                    }
                  ],
                  "projects": [
                    {
                      "id": "proj_1",
                      "title": "Project Name",
                      "description": "What the project does",
                      "role": "Your role",
                      "technologies": ["Tech1"],
                      "outcomes": ["Key result 1"],
                      "url": "https://... or null",
                      "duration": "3 months",
                      "clientName": "Client or null",
                      "rating": 5
                    }
                  ],
                  "certificates": [
                    {
                      "id": "cert_1",
                      "title": "Certificate Name",
                      "issuingOrganization": "Issuer",
                      "issueDate": "MM/YYYY",
                      "credentialId": "ID or null",
                      "skills": ["Skill1"]
                    }
                  ],
                  "languages": [
                    { "name": "Vietnamese", "proficiency": "Native" }
                  ],
                  "endorsements": [
                    {
                      "quote": "Endorsement text",
                      "authorName": "Name",
                      "authorTitle": "Title",
                      "skillEndorsed": "Skill"
                    }
                  ]
                }

                RULES:
                1. Use the candidate's REAL data - do not invent facts
                2. Enhance descriptions to be more professional and impactful
                3. Write a compelling summary based on their actual experience
                4. Organize skills into logical categories (e.g., Frontend, Backend, Soft Skills)
                5. Skill levels: 1=Beginner, 2=Basic, 3=Intermediate, 4=Advanced, 5=Expert
                6. Quantify achievements where possible (%, numbers, metrics)
                7. If data for a section is missing, use an empty array []
                8. Keep experience in reverse chronological order
                9. Language proficiency: Native, Fluent, Advanced, Intermediate, or Basic
                10. Return ONLY the JSON object, nothing else
                """;
    }

    /**
     * Build comprehensive prompt for CV generation - now outputs structured JSON
     * Much shorter than before since we don't need HTML/CSS design instructions
     */
    private String buildCVPrompt(
            UserProfileDTO profile,
            List<PortfolioProjectDTO> projects,
            List<ExternalCertificateDTO> certificates,
            List<MentorReviewDTO> reviews,
            CVGenerationRequest request) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Create a structured CV JSON for the following candidate.\n\n");

        if (request.getTargetRole() != null && !request.getTargetRole().isEmpty()) {
            prompt.append("Target Role: ").append(request.getTargetRole()).append("\n");
        }
        if (request.getTargetIndustry() != null && !request.getTargetIndustry().isEmpty()) {
            prompt.append("Target Industry: ").append(request.getTargetIndustry()).append("\n");
        }

        prompt.append("\n--- CANDIDATE DATA ---\n");

        String displayName = profile.getFullName() != null ? profile.getFullName() : "Professional";
        prompt.append("Name: ").append(displayName).append("\n");

        if (profile.getProfessionalTitle() != null)
            prompt.append("Title: ").append(profile.getProfessionalTitle()).append("\n");
        if (profile.getYearsOfExperience() != null)
            prompt.append("Experience: ").append(profile.getYearsOfExperience()).append(" years\n");
        if (profile.getLocation() != null)
            prompt.append("Location: ").append(profile.getLocation()).append("\n");
        if (profile.getBasicBio() != null && !profile.getBasicBio().isEmpty())
            prompt.append("Bio: ").append(profile.getBasicBio()).append("\n");
        if (profile.getCareerGoals() != null)
            prompt.append("Career Goals: ").append(profile.getCareerGoals()).append("\n");
        if (profile.getTagline() != null)
            prompt.append("Tagline: ").append(profile.getTagline()).append("\n");

        // Contact
        prompt.append("\n--- CONTACT ---\n");
        if (profile.getPhone() != null) prompt.append("Phone: ").append(profile.getPhone()).append("\n");
        if (profile.getAddress() != null) prompt.append("Address: ").append(profile.getAddress()).append("\n");
        if (profile.getLinkedinUrl() != null) prompt.append("LinkedIn: ").append(profile.getLinkedinUrl()).append("\n");
        if (profile.getGithubUrl() != null) prompt.append("GitHub: ").append(profile.getGithubUrl()).append("\n");
        if (profile.getPortfolioWebsiteUrl() != null)
            prompt.append("Website: ").append(profile.getPortfolioWebsiteUrl()).append("\n");
        if (profile.getBehanceUrl() != null) prompt.append("Behance: ").append(profile.getBehanceUrl()).append("\n");
        if (profile.getDribbbleUrl() != null)
            prompt.append("Dribbble: ").append(profile.getDribbbleUrl()).append("\n");

        // Skills
        if (profile.getTopSkills() != null && !profile.getTopSkills().isEmpty()) {
            prompt.append("\n--- SKILLS ---\n").append(profile.getTopSkills()).append("\n");
        }

        // Languages
        if (profile.getLanguagesSpoken() != null && !profile.getLanguagesSpoken().isEmpty()) {
            prompt.append("\n--- LANGUAGES ---\n").append(profile.getLanguagesSpoken()).append("\n");
        }

        // Projects
        if (request.getIncludeProjects() != null && request.getIncludeProjects() && !projects.isEmpty()) {
            prompt.append("\n--- PROJECTS ---\n");
            projects.forEach(project -> {
                prompt.append("- ").append(project.getTitle());
                prompt.append(" [").append(project.getProjectType()).append("]");
                if (project.getClientName() != null)
                    prompt.append(" Client: ").append(project.getClientName());
                if (project.getDuration() != null)
                    prompt.append(" Duration: ").append(project.getDuration());
                prompt.append("\n");
                if (project.getDescription() != null)
                    prompt.append("  Desc: ").append(project.getDescription()).append("\n");
                if (project.getTools() != null && !project.getTools().isEmpty())
                    prompt.append("  Tech: ").append(String.join(", ", project.getTools())).append("\n");
                if (project.getOutcomes() != null && !project.getOutcomes().isEmpty())
                    prompt.append("  Outcomes: ").append(String.join("; ", project.getOutcomes())).append("\n");
                if (project.getRating() != null)
                    prompt.append("  Rating: ").append(project.getRating()).append("/5\n");
            });
        }

        // Certificates
        if (request.getIncludeCertificates() != null && request.getIncludeCertificates() && !certificates.isEmpty()) {
            prompt.append("\n--- CERTIFICATES ---\n");
            certificates.forEach(cert -> {
                prompt.append("- ").append(cert.getTitle());
                prompt.append(" by ").append(cert.getIssuingOrganization());
                if (cert.getIssueDate() != null) prompt.append(" (").append(cert.getIssueDate()).append(")");
                if (cert.getCredentialId() != null)
                    prompt.append(" ID: ").append(cert.getCredentialId());
                prompt.append("\n");
                if (cert.getSkills() != null && !cert.getSkills().isEmpty())
                    prompt.append("  Skills: ").append(String.join(", ", cert.getSkills())).append("\n");
            });
        }

        // Reviews
        if (request.getIncludeReviews() != null && request.getIncludeReviews() && !reviews.isEmpty()) {
            prompt.append("\n--- ENDORSEMENTS ---\n");
            reviews.forEach(review -> {
                prompt.append("- \"").append(review.getFeedback()).append("\"");
                prompt.append(" - ").append(review.getMentorName());
                if (review.getMentorTitle() != null)
                    prompt.append(", ").append(review.getMentorTitle());
                if (review.getSkillEndorsed() != null)
                    prompt.append(" [Skill: ").append(review.getSkillEndorsed()).append("]");
                prompt.append("\n");
            });
        }

        if (request.getAdditionalInstructions() != null && !request.getAdditionalInstructions().isEmpty()) {
            prompt.append("\n--- ADDITIONAL INSTRUCTIONS ---\n");
            prompt.append(request.getAdditionalInstructions()).append("\n");
        }

        prompt.append("\nReturn ONLY valid JSON. No markdown fences, no explanations.");

        return prompt.toString();
    }

    /**
     * Generate a JSON representation of CV data for easy editing
     */
    public String generateCVJson(
            UserProfileDTO profile,
            List<PortfolioProjectDTO> projects,
            List<ExternalCertificateDTO> certificates,
            List<MentorReviewDTO> reviews) {
        try {
            Map<String, Object> cvData = new HashMap<>();
            cvData.put("profile", profile);
            cvData.put("projects", projects);
            cvData.put("certificates", certificates);
            cvData.put("reviews", reviews);

            return objectMapper.writeValueAsString(cvData);
        } catch (Exception e) {
            log.error("Error generating CV JSON", e);
            throw new RuntimeException("Failed to generate CV JSON: " + e.getMessage(), e);
        }
    }
}
