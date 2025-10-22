package com.exe.skillverse_backend.portfolio_service.service;

import com.exe.skillverse_backend.portfolio_service.dto.*;
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
public class CVGeneratorAIService {

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
     * Generate CV content using Mistral AI based on user's portfolio data
     */
    public String generateCV(
            UserProfileDTO profile,
            List<PortfolioProjectDTO> projects,
            List<ExternalCertificateDTO> certificates,
            List<MentorReviewDTO> reviews,
            CVGenerationRequest request
    ) {
        try {
            String prompt = buildCVPrompt(profile, projects, certificates, reviews, request);
            log.info("Generating CV with Mistral AI for user: {}", profile.getUserId());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(mistralApiKey);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("messages", List.of(
                    Map.of("role", "system", "content", "You are an expert CV writer and career consultant. Create professional, well-structured CVs in HTML format with excellent formatting and design."),
                    Map.of("role", "user", "content", prompt)
            ));
            requestBody.put("temperature", 0.7);
            requestBody.put("max_tokens", 4000);

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
                                String.class
                        );

                        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                            JsonNode jsonResponse = objectMapper.readTree(response.getBody());
                            String cvContent = jsonResponse.at("/choices/0/message/content").asText();
                            log.info("CV generated successfully for user: {} with model {}", profile.getUserId(), currentModel);
                            return cvContent;
                        }

                        log.warn("Mistral response not OK ({}), attempt {}/{} with model {}", response.getStatusCode(), attempt, maxAttempts, currentModel);
                    } catch (HttpClientErrorException e) {
                        if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                            long backoffMs = (long) (1000L * Math.pow(2, attempt - 1));
                            log.warn("429 from Mistral (model {}), backing off {} ms, attempt {}/{}", currentModel, backoffMs, attempt, maxAttempts);
                            try {
                                Thread.sleep(backoffMs);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                            }
                            continue; // retry
                        }
                        // Other 4xx/5xx -> do not retry endlessly
                        throw e;
                    } catch (Exception ex) {
                        // Transient network errors: brief retry
                        long backoffMs = (long) (800L * Math.pow(2, attempt - 1));
                        log.warn("Transient error calling Mistral (model {}): {}. Backoff {} ms, attempt {}/{}", currentModel, ex.getMessage(), backoffMs, attempt, maxAttempts);
                        try {
                            Thread.sleep(backoffMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }

                log.warn("Exhausted retries for model {}.{}", currentModel, (m < modelsToTry.length - 1 ? " Trying fallback model..." : ""));
            }

            throw new RuntimeException("Failed to generate CV after retries and fallback model");

        } catch (Exception e) {
            log.error("Error generating CV with Mistral AI", e);
            throw new RuntimeException("Failed to generate CV: " + e.getMessage(), e);
        }
    }

    /**
     * Build comprehensive prompt for CV generation
     */
    private String buildCVPrompt(
            UserProfileDTO profile,
            List<PortfolioProjectDTO> projects,
            List<ExternalCertificateDTO> certificates,
            List<MentorReviewDTO> reviews,
            CVGenerationRequest request
    ) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Create a professional CV in HTML format for the following candidate. ");
        prompt.append("Use modern, clean design with proper sections and styling.\n\n");

        if (request.getTemplateName() != null) {
            prompt.append("Template Style: ").append(request.getTemplateName()).append("\n");
        }

        if (request.getTargetRole() != null) {
            prompt.append("Target Role: ").append(request.getTargetRole()).append("\n");
        }

        if (request.getTargetIndustry() != null) {
            prompt.append("Target Industry: ").append(request.getTargetIndustry()).append("\n");
        }

        prompt.append("\n--- CANDIDATE PROFILE ---\n");
        
        // Use full name from basic profile, fallback to professional title
        String displayName = profile.getFullName() != null ? profile.getFullName() : 
                            (profile.getProfessionalTitle() != null ? profile.getProfessionalTitle() : "Professional");
        prompt.append("Name: ").append(displayName).append("\n");
        
        // Professional title from extended profile
        if (profile.getProfessionalTitle() != null) {
            prompt.append("Professional Title: ").append(profile.getProfessionalTitle()).append("\n");
        }
        
        // Years of experience
        if (profile.getYearsOfExperience() != null) {
            prompt.append("Years of Experience: ").append(profile.getYearsOfExperience()).append(" years\n");
        }
        
        // Location (from extended profile)
        if (profile.getLocation() != null) {
            prompt.append("Location: ").append(profile.getLocation()).append("\n");
        }
        
        // Bio (prefer basic bio, fallback to career goals)
        if (profile.getBasicBio() != null && !profile.getBasicBio().isEmpty()) {
            prompt.append("\nProfessional Summary:\n").append(profile.getBasicBio()).append("\n");
        }

        // Career goals (from extended profile)
        if (profile.getCareerGoals() != null) {
            prompt.append("\nCareer Goals:\n").append(profile.getCareerGoals()).append("\n");
        }
        
        // Tagline (from extended profile)
        if (profile.getTagline() != null) {
            prompt.append("\nTagline: ").append(profile.getTagline()).append("\n");
        }

        // Contact Information
        prompt.append("\n--- CONTACT INFORMATION ---\n");
        if (profile.getPhone() != null) {
            prompt.append("Phone: ").append(profile.getPhone()).append("\n");
        }
        if (profile.getAddress() != null) {
            prompt.append("Address: ").append(profile.getAddress()).append("\n");
        }
        if (profile.getLinkedinUrl() != null) {
            prompt.append("LinkedIn: ").append(profile.getLinkedinUrl()).append("\n");
        }
        if (profile.getGithubUrl() != null) {
            prompt.append("GitHub: ").append(profile.getGithubUrl()).append("\n");
        }
        if (profile.getPortfolioWebsiteUrl() != null) {
            prompt.append("Website: ").append(profile.getPortfolioWebsiteUrl()).append("\n");
        }
        if (profile.getBehanceUrl() != null) {
            prompt.append("Behance: ").append(profile.getBehanceUrl()).append("\n");
        }
        if (profile.getDribbbleUrl() != null) {
            prompt.append("Dribbble: ").append(profile.getDribbbleUrl()).append("\n");
        }

        // Skills (from topSkills JSON string - parse if needed, or include as-is)
        if (profile.getTopSkills() != null && !profile.getTopSkills().isEmpty()) {
            prompt.append("\n--- SKILLS ---\n");
            prompt.append(profile.getTopSkills()).append("\n");
        }

        // Languages (from languagesSpoken JSON string)
        if (profile.getLanguagesSpoken() != null && !profile.getLanguagesSpoken().isEmpty()) {
            prompt.append("\n--- LANGUAGES ---\n");
            prompt.append(profile.getLanguagesSpoken()).append("\n");
        }
        
        // Hourly rate / availability
        if (profile.getHourlyRate() != null) {
            prompt.append("\n--- RATE ---\n");
            prompt.append("Hourly Rate: ").append(profile.getHourlyRate());
            if (profile.getPreferredCurrency() != null) {
                prompt.append(" ").append(profile.getPreferredCurrency());
            }
            prompt.append("\n");
        }
        if (profile.getAvailabilityStatus() != null) {
            prompt.append("Availability: ").append(profile.getAvailabilityStatus()).append("\n");
        }

        // Projects
        if (request.getIncludeProjects() != null && request.getIncludeProjects() && !projects.isEmpty()) {
            prompt.append("\n--- PROJECTS & WORK EXPERIENCE ---\n");
            projects.forEach(project -> {
                prompt.append("\nProject: ").append(project.getTitle()).append("\n");
                prompt.append("Type: ").append(project.getProjectType()).append("\n");
                if (project.getClientName() != null) {
                    prompt.append("Client: ").append(project.getClientName()).append("\n");
                }
                if (project.getDuration() != null) {
                    prompt.append("Duration: ").append(project.getDuration()).append("\n");
                }
                if (project.getDescription() != null) {
                    prompt.append("Description: ").append(project.getDescription()).append("\n");
                }
                if (project.getTools() != null && !project.getTools().isEmpty()) {
                    prompt.append("Technologies: ").append(String.join(", ", project.getTools())).append("\n");
                }
                if (project.getOutcomes() != null && !project.getOutcomes().isEmpty()) {
                    prompt.append("Key Achievements:\n");
                    project.getOutcomes().forEach(outcome -> prompt.append("  • ").append(outcome).append("\n"));
                }
                if (project.getRating() != null) {
                    prompt.append("Client Rating: ").append(project.getRating()).append("/5 stars\n");
                }
            });
        }

        // Certificates
        if (request.getIncludeCertificates() != null && request.getIncludeCertificates() && !certificates.isEmpty()) {
            prompt.append("\n--- CERTIFICATIONS ---\n");
            certificates.forEach(cert -> {
                prompt.append("\n").append(cert.getTitle()).append("\n");
                prompt.append("Issued by: ").append(cert.getIssuingOrganization()).append("\n");
                if (cert.getIssueDate() != null) {
                    prompt.append("Date: ").append(cert.getIssueDate()).append("\n");
                }
                if (cert.getCredentialId() != null) {
                    prompt.append("Credential ID: ").append(cert.getCredentialId()).append("\n");
                }
                if (cert.getSkills() != null && !cert.getSkills().isEmpty()) {
                    prompt.append("Skills: ").append(String.join(", ", cert.getSkills())).append("\n");
                }
            });
        }

        // Mentor Reviews/Endorsements
        if (request.getIncludeReviews() != null && request.getIncludeReviews() && !reviews.isEmpty()) {
            prompt.append("\n--- PROFESSIONAL ENDORSEMENTS ---\n");
            reviews.forEach(review -> {
                prompt.append("\n\"").append(review.getFeedback()).append("\"\n");
                prompt.append("- ").append(review.getMentorName());
                if (review.getMentorTitle() != null) {
                    prompt.append(", ").append(review.getMentorTitle());
                }
                prompt.append("\n");
                if (review.getSkillEndorsed() != null) {
                    prompt.append("Endorsed skill: ").append(review.getSkillEndorsed()).append("\n");
                }
            });
        }

        if (request.getAdditionalInstructions() != null) {
            prompt.append("\n--- ADDITIONAL REQUIREMENTS ---\n");
            prompt.append(request.getAdditionalInstructions()).append("\n");
        }

        prompt.append("\n\nGenerate a complete, professional CV in HTML format with:");
        prompt.append("\n1. Modern, clean design with proper CSS styling");
        prompt.append("\n2. Well-organized sections with clear headings");
        prompt.append("\n3. Professional color scheme (subtle blues, grays)");
        prompt.append("\n4. Responsive layout that looks good on all devices");
        prompt.append("\n5. Print-friendly formatting");
        prompt.append("\n6. Use professional fonts like Arial, Helvetica, or sans-serif");
        prompt.append("\n7. Include all relevant information from the data provided");
        prompt.append("\n8. Make it ATS (Applicant Tracking System) friendly");
        prompt.append("\n\nReturn ONLY the complete HTML code, starting with <!DOCTYPE html>");

        return prompt.toString();
    }

    /**
     * Generate a JSON representation of CV data for easy editing
     */
    public String generateCVJson(
            UserProfileDTO profile,
            List<PortfolioProjectDTO> projects,
            List<ExternalCertificateDTO> certificates,
            List<MentorReviewDTO> reviews
    ) {
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
