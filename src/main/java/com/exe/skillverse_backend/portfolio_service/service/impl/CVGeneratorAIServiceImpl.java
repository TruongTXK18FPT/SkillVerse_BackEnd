package com.exe.skillverse_backend.portfolio_service.service.impl;

import com.exe.skillverse_backend.portfolio_service.dto.AIEnhanceRequest;
import com.exe.skillverse_backend.portfolio_service.dto.AIEnhanceResponse;
import com.exe.skillverse_backend.portfolio_service.service.CVGeneratorAIService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import com.exe.skillverse_backend.portfolio_service.dto.CVGenerationRequest;
import com.exe.skillverse_backend.portfolio_service.dto.CompletedMissionDTO;
import com.exe.skillverse_backend.portfolio_service.dto.ExternalCertificateDTO;
import com.exe.skillverse_backend.portfolio_service.dto.MentorReviewDTO;
import com.exe.skillverse_backend.portfolio_service.dto.PortfolioEducationDTO;
import com.exe.skillverse_backend.portfolio_service.dto.PortfolioProjectDTO;
import com.exe.skillverse_backend.portfolio_service.dto.PortfolioWorkExperienceDTO;
import com.exe.skillverse_backend.portfolio_service.dto.UserProfileDTO;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

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
            List<CompletedMissionDTO> completedMissions,
            CVGenerationRequest request) {
        try {
            String prompt = buildCVPrompt(profile, projects, certificates, reviews, completedMissions, request);
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
                    "dribbbleUrl": "string or null",
                    "avatarUrl": "string or null"
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
                10. Use manual work experience as the primary source for the experience section
                11. Completed system missions may be used as real freelance/project evidence when relevant
                12. Return ONLY the JSON object, nothing else
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
            List<CompletedMissionDTO> completedMissions,
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
        if (profile.getEmail() != null) prompt.append("Email: ").append(profile.getEmail()).append("\n");
        if (profile.getPhone() != null) prompt.append("Phone: ").append(profile.getPhone()).append("\n");
        if (profile.getAddress() != null) prompt.append("Address: ").append(profile.getAddress()).append("\n");
        if (profile.getLinkedinUrl() != null) prompt.append("LinkedIn: ").append(profile.getLinkedinUrl()).append("\n");
        if (profile.getGithubUrl() != null) prompt.append("GitHub: ").append(profile.getGithubUrl()).append("\n");
        if (profile.getPortfolioWebsiteUrl() != null)
            prompt.append("Website: ").append(profile.getPortfolioWebsiteUrl()).append("\n");
        if (profile.getBehanceUrl() != null) prompt.append("Behance: ").append(profile.getBehanceUrl()).append("\n");
        if (profile.getDribbbleUrl() != null)
            prompt.append("Dribbble: ").append(profile.getDribbbleUrl()).append("\n");
        if (profile.getPortfolioAvatarUrl() != null)
            prompt.append("Avatar URL: ").append(profile.getPortfolioAvatarUrl()).append("\n");

        if (profile.getWorkExperiences() != null && !profile.getWorkExperiences().isEmpty()) {
            prompt.append("\n--- WORK EXPERIENCE ---\n");
            for (PortfolioWorkExperienceDTO experience : profile.getWorkExperiences()) {
                prompt.append("- Company: ").append(defaultString(experience.getCompanyName())).append("\n");
                prompt.append("  Position: ").append(defaultString(experience.getPosition())).append("\n");
                if (experience.getLocation() != null)
                    prompt.append("  Location: ").append(experience.getLocation()).append("\n");
                if (experience.getStartDate() != null || experience.getEndDate() != null) {
                    prompt.append("  Duration: ")
                            .append(defaultString(experience.getStartDate()))
                            .append(" -> ")
                            .append(Boolean.TRUE.equals(experience.getCurrentJob())
                                    ? "Present"
                                    : defaultString(experience.getEndDate()))
                            .append("\n");
                }
                if (experience.getDescription() != null)
                    prompt.append("  Description: ").append(experience.getDescription()).append("\n");
            }
        }

        if (profile.getEducationHistory() != null && !profile.getEducationHistory().isEmpty()) {
            prompt.append("\n--- EDUCATION HISTORY ---\n");
            for (PortfolioEducationDTO education : profile.getEducationHistory()) {
                prompt.append("- Institution: ").append(defaultString(education.getInstitution())).append("\n");
                prompt.append("  Degree: ").append(defaultString(education.getDegree())).append("\n");
                if (education.getFieldOfStudy() != null)
                    prompt.append("  Field: ").append(education.getFieldOfStudy()).append("\n");
                if (education.getStatus() != null)
                    prompt.append("  Status: ").append(education.getStatus()).append("\n");
                if (education.getLocation() != null)
                    prompt.append("  Location: ").append(education.getLocation()).append("\n");
                if (education.getStartDate() != null || education.getEndDate() != null) {
                    prompt.append("  Duration: ")
                            .append(defaultString(education.getStartDate()))
                            .append(" -> ")
                            .append(defaultString(education.getEndDate()))
                            .append("\n");
                }
                if (education.getDescription() != null)
                    prompt.append("  Description: ").append(education.getDescription()).append("\n");
            }
        }

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

        if (Boolean.TRUE.equals(request.getIncludeCompletedMissions()) && !completedMissions.isEmpty()) {
            prompt.append("\n--- COMPLETED SYSTEM MISSIONS ---\n");
            completedMissions.forEach(mission -> {
                prompt.append("- ").append(mission.getJobTitle());
                if (mission.getRecruiterCompanyName() != null)
                    prompt.append(" | Company: ").append(mission.getRecruiterCompanyName());
                if (mission.getCompletedAt() != null)
                    prompt.append(" | Completed: ").append(mission.getCompletedAt());
                prompt.append("\n");
                if (mission.getJobDescription() != null)
                    prompt.append("  Scope: ").append(mission.getJobDescription()).append("\n");
                if (mission.getWorkNote() != null)
                    prompt.append("  Work Note: ").append(mission.getWorkNote()).append("\n");
                if (mission.getRequiredSkills() != null && !mission.getRequiredSkills().isEmpty())
                    prompt.append("  Skills: ").append(String.join(", ", mission.getRequiredSkills())).append("\n");
                if (mission.getReviewComment() != null)
                    prompt.append("  Review: ").append(mission.getReviewComment()).append("\n");
                if (mission.getDeliverables() != null && !mission.getDeliverables().isEmpty()) {
                    prompt.append("  Deliverables: ")
                            .append(mission.getDeliverables().stream()
                                    .map(CompletedMissionDTO.DeliverableInfo::getFileName)
                                    .filter(name -> name != null && !name.isBlank())
                                    .collect(java.util.stream.Collectors.joining(", ")))
                            .append("\n");
                }
            });
        }

        if (request.getAdditionalInstructions() != null && !request.getAdditionalInstructions().isEmpty()) {
            prompt.append("\n--- ADDITIONAL INSTRUCTIONS ---\n");
            prompt.append(request.getAdditionalInstructions()).append("\n");
        }

        prompt.append("\nReturn ONLY valid JSON. No markdown fences, no explanations.");

        return prompt.toString();
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
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

    /**
     * Enhance a specific CV section using AI.
     * Generates improved content based on user's instruction while preserving
     * factual information.
     */
    @Override
    public AIEnhanceResponse enhanceSection(AIEnhanceRequest request) {
        try {
            String prompt = buildEnhancePrompt(request);
            log.info("Enhancing CV section: {} with instruction: {}",
                    request.getSection(), request.getInstruction());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(mistralApiKey);

            String systemPrompt = buildEnhanceSystemPrompt(request.getSection());

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", prompt)));
            requestBody.put("temperature", 0.5);
            requestBody.put("max_tokens", 1500);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    mistralApiUrl,
                    HttpMethod.POST,
                    entity,
                    String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode jsonResponse = objectMapper.readTree(response.getBody());
                String content = jsonResponse.at("/choices/0/message/content").asText();

                // Parse the response which may contain multiple alternatives
                List<String> alternatives = parseAlternatives(content);
                String primary = alternatives.isEmpty() ? content : alternatives.get(0);

                log.info("Successfully enhanced section: {}", request.getSection());

                return AIEnhanceResponse.builder()
                        .enhancedContent(primary)
                        .alternatives(alternatives.size() > 1
                                ? alternatives.subList(1, alternatives.size())
                                : new ArrayList<>())
                        .section(request.getSection())
                        .itemId(request.getItemId())
                        .success(true)
                        .build();
            }

            throw new RuntimeException("AI service returned non-OK status: " + response.getStatusCode());

        } catch (HttpClientErrorException e) {
            log.error("AI service error for section enhancement: {}", e.getMessage());
            return AIEnhanceResponse.builder()
                    .section(request.getSection())
                    .itemId(request.getItemId())
                    .success(false)
                    .errorMessage("AI service error: " + e.getMessage())
                    .build();
        } catch (Exception e) {
            log.error("Error enhancing CV section", e);
            return AIEnhanceResponse.builder()
                    .section(request.getSection())
                    .itemId(request.getItemId())
                    .success(false)
                    .errorMessage("Failed to enhance: " + e.getMessage())
                    .build();
        }
    }

    private String buildEnhanceSystemPrompt(String section) {
        return switch (section.toLowerCase()) {
            case "summary" -> """
                    You are an expert CV writer specializing in professional summaries.
                    Rewrite the provided summary to be more compelling and professional.
                    Keep it concise (2-4 sentences, max 150 words).
                    Highlight key strengths without inventing facts.
                    Return ONLY the enhanced summary text, no markdown, no explanations.
                    """;
            case "experience" -> """
                    You are an expert CV writer specializing in work experience descriptions.
                    Rewrite the job description into 3-5 impactful bullet points.
                    Use action verbs, quantify achievements where possible.
                    Keep technical accuracy - don't invent technologies or metrics.
                    Return ONLY the bullet points, one per line, starting with "- ".
                    """;
            case "project" -> """
                    You are an expert CV writer specializing in project descriptions.
                    Rewrite the project description to be concise yet impactful.
                    Focus on: what was built, your role, technologies used, and key outcomes.
                    Maximum 2-3 sentences or bullet points.
                    Return ONLY the enhanced description, no markdown, no explanations.
                    """;
            case "education" -> """
                    You are an expert CV writer specializing in education sections.
                    Enhance the education description if needed, keeping it factual.
                    Focus on relevant coursework, achievements, or honors if applicable.
                    Return ONLY the enhanced text, no markdown, no explanations.
                    """;
            case "skill" -> """
                    You are an expert CV writer specializing in skills sections.
                    Organize and enhance the skills presentation for maximum impact.
                    Group related skills, use professional terminology.
                    Return ONLY the skills list, no markdown, no explanations.
                    """;
            default -> """
                    You are an expert CV writer.
                    Enhance the provided content to be more professional and impactful.
                    Keep all factual information accurate - do not invent facts.
                    Return ONLY the enhanced text, no markdown, no explanations.
                    """;
        };
    }

    private String buildEnhancePrompt(AIEnhanceRequest request) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Section: ").append(request.getSection()).append("\n\n");

        if (request.getContextData() != null && !request.getContextData().isEmpty()) {
            prompt.append("Context:\n");
            request.getContextData().forEach((key, value) -> {
                if (value != null && !value.isBlank()) {
                    prompt.append("- ").append(key).append(": ").append(value).append("\n");
                }
            });
            prompt.append("\n");
        }

        prompt.append("Current content:\n");
        prompt.append(request.getCurrentContent() != null ? request.getCurrentContent() : "").append("\n\n");

        if (request.getInstruction() != null && !request.getInstruction().isBlank()) {
            prompt.append("User's request: ").append(request.getInstruction()).append("\n\n");
        }

        prompt.append("Please provide 2-3 alternative versions separated by \"---\" on its own line.");

        return prompt.toString();
    }

    private List<String> parseAlternatives(String content) {
        List<String> alternatives = new ArrayList<>();

        // Split by "---" separator
        String[] parts = content.split("\\n?---\\n?");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                // Remove markdown fences if present
                trimmed = trimmed.replaceAll("^```\\w*\\s*", "").replaceAll("\\s*```$", "");
                alternatives.add(trimmed);
            }
        }

        // If no separators found, treat entire content as single alternative
        if (alternatives.isEmpty() && !content.trim().isEmpty()) {
            alternatives.add(content.trim());
        }

        return alternatives;
    }
}
