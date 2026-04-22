package com.exe.skillverse_backend.portfolio_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request to enhance a specific CV section using AI.
 * Used when user wants AI to rewrite/refine a particular section.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIEnhanceRequest {

    /**
     * Section to enhance: "summary", "experience", "project", "education", "skill"
     */
    private String section;

    /**
     * ID of the specific item within the section (e.g., experience_1, project_2)
     * Required for sections with multiple items like experience, project, education
     */
    private String itemId;

    /**
     * User's instruction/prompt for AI enhancement
     * Examples: "Viết chuyên nghiệp hơn", "Thêm từ khóa React", "Viết ngắn gọn"
     */
    private String instruction;

    /**
     * Current content to be enhanced
     */
    private String currentContent;

    /**
     * Additional context data to help AI generate better content
     * e.g., job title, company name, technologies for experience section
     */
    private java.util.Map<String, String> contextData;
}
