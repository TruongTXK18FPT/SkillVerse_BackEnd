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

            // Enhanced system prompt with template-specific design guidelines
            String systemPrompt = buildSystemPrompt(request.getTemplateName());

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
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
     * Build template-specific system prompt with design guidelines
     */
    private String buildSystemPrompt(String templateName) {
        String basePrompt = "You are an expert CV writer and career consultant. Create professional, well-structured CVs in HTML format with excellent formatting and design.\n\n";
        
        if (templateName == null || templateName.isEmpty()) {
            templateName = "professional"; // Default
        }
        
        switch (templateName.toLowerCase()) {
            case "professional":
                return basePrompt + """
                    📘 PROFESSIONAL TEMPLATE DESIGN GUIDELINES:
                    
                    COLOR SCHEME:
                    - Primary: Navy Blue (#1e3a8a) for headers and accents
                    - Secondary: Light Blue (#3b82f6) for section dividers
                    - Text: Dark Gray (#1f2937) for body text
                    - Background: White (#ffffff) with subtle gray sections (#f9fafb)
                    - Accent: Gold (#d97706) for highlights and achievements
                    
                    LAYOUT:
                    - Two-column layout: Left sidebar (30%) for contact/skills, Right (70%) for experience
                    - Professional header with name in large, bold font (32px)
                    - Clear section dividers with horizontal lines
                    - Consistent spacing: 20px between sections, 12px between items
                    - Use serif font (Georgia, Times New Roman) for headings
                    - Use sans-serif font (Arial, Helvetica) for body text
                    
                    STRUCTURE:
                    1. Header: Name, title, contact bar at top with icons
                    2. Left Sidebar: Photo placeholder, skills with progress bars, languages, contact details
                    3. Right Content: Professional summary, experience (reverse chronological), education, certifications
                    4. Footer: References available upon request
                    
                    STYLING:
                    - Box shadows for depth: 0 2px 4px rgba(0,0,0,0.1)
                    - Rounded corners: 8px for containers
                    - Bold section titles (18px, uppercase, letter-spacing: 1px)
                    - Use bullet points (•) for lists
                    - Add subtle background patterns in sidebar
                    """;
                
            case "minimal":
                return basePrompt + """
                    ⚪ MINIMAL TEMPLATE DESIGN GUIDELINES:
                    
                    COLOR SCHEME:
                    - Primary: Pure Black (#000000) for all text
                    - Secondary: Medium Gray (#6b7280) for dates and secondary info
                    - Background: Pure White (#ffffff)
                    - Accent: Single line color - Charcoal (#374151) for dividers only
                    - NO other colors, keep it monochrome and clean
                    
                    LAYOUT:
                    - Single-column, left-aligned layout for maximum readability
                    - Generous white space: 40px margins, 30px between sections
                    - Name at top-left in large, thin font (36px, font-weight: 300)
                    - Minimalist section headers (14px, uppercase, thin font weight)
                    - Clean, thin horizontal lines (1px) to separate sections
                    - Use only Helvetica Neue or Arial font family (light/regular weights)
                    
                    STRUCTURE:
                    1. Header: Name + title in minimal style (no borders, no backgrounds)
                    2. Contact: Single line with email, phone, location (separated by |)
                    3. Summary: 2-3 lines, italic, gray text
                    4. Experience: Title - Company - Date (no bullets, simple paragraphs)
                    5. Education & Certifications: Inline format
                    6. Skills: Comma-separated list, no progress bars
                    
                    STYLING:
                    - NO shadows, NO borders, NO background colors
                    - NO icons, NO graphics, NO decorative elements
                    - Line height: 1.8 for maximum readability
                    - Font sizes: 36px (name), 14px (headers), 11px (body)
                    - Use thin divider lines (border-top: 1px solid #e5e7eb)
                    - Keep it ultra-clean and Scandinavian-inspired
                    """;
                
            case "modern":
                return basePrompt + """
                    🎨 MODERN TEMPLATE DESIGN GUIDELINES:
                    
                    COLOR SCHEME:
                    - Primary: Vibrant Purple (#8b5cf6) for headers and key elements
                    - Secondary: Cyan/Teal (#06b6d4) for accents and highlights
                    - Tertiary: Coral (#f59e0b) for achievements and metrics
                    - Text: Near-black (#0f172a) for readability
                    - Background: White (#ffffff) with colorful gradient sections
                    - Sidebar: Gradient background (linear-gradient: #8b5cf6 to #06b6d4)
                    
                    LAYOUT:
                    - Asymmetric three-section layout: Thin left accent bar (5%), Main sidebar (25%), Content (70%)
                    - Bold, large name (40px, font-weight: 700) with gradient text effect
                    - Floating section cards with shadows
                    - Modern, clean spacing: 24px between sections
                    - Use modern sans-serif fonts (Inter, Poppins, or Montserrat)
                    - Gradient backgrounds for skill badges
                    
                    STRUCTURE:
                    1. Header: Full-width with gradient background, name + animated title
                    2. Left Accent: Vertical color bar with year milestones
                    3. Sidebar: Photo, skills with circular progress indicators, social media icons
                    4. Content: Experience cards with hover effects, project showcases with thumbnails
                    5. Floating achievement badges
                    
                    STYLING:
                    - Card-based design with box-shadows: 0 4px 6px rgba(0,0,0,0.1)
                    - Gradient buttons and badges: linear-gradient(135deg, #8b5cf6, #06b6d4)
                    - Rounded corners: 12px for cards, 24px for buttons
                    - Use CSS Grid and Flexbox for modern layouts
                    - Add subtle animations: transition: all 0.3s ease
                    - Icon integration for contact, skills, and social media
                    - Color-coded skill categories (Frontend: purple, Backend: cyan, Design: coral)
                    """;
                
            case "creative":
                return basePrompt + """
                    🎭 CREATIVE TEMPLATE DESIGN GUIDELINES:
                    
                    COLOR SCHEME:
                    - Primary: Vibrant Magenta (#ec4899) for bold statements
                    - Secondary: Electric Blue (#3b82f6) for creative flair
                    - Tertiary: Sunny Yellow (#fbbf24) for energy and highlights
                    - Quaternary: Lime Green (#84cc16) for growth metrics
                    - Text: Deep Purple (#581c87) instead of black for personality
                    - Background: Cream (#fef3c7) with colorful geometric shapes
                    
                    LAYOUT:
                    - Unique magazine-style layout with overlapping elements
                    - Asymmetric grid with varying column widths
                    - Large, artistic name (48px) with creative typography (handwriting or display font)
                    - Colorful section headers with decorative shapes (circles, triangles, waves)
                    - Use display fonts (Playfair Display, Bebas Neue) for headers
                    - Use readable sans-serif (Open Sans) for body
                    - Infographic-style data visualization for skills and achievements
                    
                    STRUCTURE:
                    1. Hero Section: Full-width with creative background pattern, name in artistic font
                    2. Profile Photo: Circular with colorful border or geometric frame
                    3. Skills: Visual infographic with icons, charts, and percentages
                    4. Experience: Timeline format with colorful nodes and connecting lines
                    5. Projects: Grid gallery with colorful overlay on hover
                    6. Sidebar: Floating colorful boxes with achievements and stats
                    
                    STYLING:
                    - Bold, overlapping sections with z-index layering
                    - Colorful geometric shapes as background elements (SVG patterns)
                    - Large drop shadows: 0 10px 25px rgba(0,0,0,0.15)
                    - Mix of rounded (24px) and sharp corners for contrast
                    - Colorful tags and badges for skills (each skill = different color)
                    - Use gradients liberally: radial-gradient, conic-gradient
                    - Add decorative elements: dots, lines, shapes, icons
                    - Timeline visualization with colorful connecting lines
                    - Percentage bars with gradient fills and animations
                    - Creative section dividers (zigzag, waves, geometric patterns)
                    """;
                
            default:
                return basePrompt + "Create a well-formatted, professional CV in HTML format.";
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

        prompt.append("\n\n🎯 FINAL REQUIREMENTS:\n");
        prompt.append("\n1. Follow the EXACT color scheme specified for the ").append(request.getTemplateName() != null ? request.getTemplateName().toUpperCase() : "PROFESSIONAL").append(" template");
        prompt.append("\n2. Implement the layout structure as described in the template guidelines");
        prompt.append("\n3. Use the specified fonts and typography styles");
        prompt.append("\n4. Apply all styling details (shadows, borders, spacing, etc.)");
        prompt.append("\n5. Make it responsive with CSS media queries for mobile devices");
        prompt.append("\n6. Include print-friendly CSS (@media print rules)");
        prompt.append("\n7. Use semantic HTML5 tags (header, section, article, aside)");
        prompt.append("\n8. Add CSS for smooth visual experience with proper contrast ratios");
        prompt.append("\n9. Include all candidate information from the data provided");
        prompt.append("\n10. Ensure the design is unique and matches the template's personality");
        prompt.append("\n\n⚠️ CRITICAL: Return ONLY the complete HTML code, starting with <!DOCTYPE html>");
        prompt.append("\nInclude ALL CSS inline in a <style> tag within <head>.");
        prompt.append("\nDo NOT include any markdown, explanations, or code fences - just pure HTML.");
        prompt.append("\nThe HTML must be fully self-contained and ready to render immediately.");

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
