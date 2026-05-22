package com.exe.skillverse_backend.journey_service.node_mentoring.ai.service.impl;

import com.exe.skillverse_backend.journey_service.node_mentoring.ai.service.RoadmapEvidencePromptService;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.JourneyOutputAssessment;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.RoadmapNodeSubmission;
import com.exe.skillverse_backend.roadmap_package_service.entity.RoadmapTemplate;
import com.exe.skillverse_backend.roadmap_package_service.dto.RoadmapRubricDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RoadmapEvidencePromptServiceImpl implements RoadmapEvidencePromptService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String buildNodeEvidencePrompt(
            RoadmapNodeSubmission submission, 
            RoadmapTemplate template,
            String nodeTitle,
            String nodeDescription,
            String nodeSkills,
            String activityExpectedOutput,
            String activityRubric,
            String activityAiPromptHint,
            String activitySkillRequirementsJson) {
            
        StringBuilder sb = new StringBuilder();
        sb.append("You are an expert mentor evaluating a student's submission for a roadmap node.\n\n");
        
        if (StringUtils.hasText(template.getAiEvidencePrompt())) {
            sb.append("System Instructions:\n").append(template.getAiEvidencePrompt()).append("\n\n");
        }
        
        if (StringUtils.hasText(template.getAssessmentPolicy())) {
            sb.append("Global Assessment Policy:\n").append(template.getAssessmentPolicy()).append("\n\n");
        }
        
        if (StringUtils.hasText(template.getOutputStandard())) {
            sb.append("Output Standard:\n").append(template.getOutputStandard()).append("\n\n");
        }
        
        if (StringUtils.hasText(template.getTemplateInstructions())) {
            sb.append("Template Instructions:\n").append(template.getTemplateInstructions()).append("\n\n");
        }
        
        sb.append("### Node Context\n");
        sb.append("Title: ").append(nodeTitle != null ? nodeTitle : "").append("\n");
        if (StringUtils.hasText(nodeDescription)) {
            sb.append("Description: ").append(nodeDescription).append("\n");
        }
        if (StringUtils.hasText(nodeSkills)) {
            sb.append("Node Skills: ").append(nodeSkills).append("\n");
        }
        
        sb.append("\n### Activity Context\n");
        if (StringUtils.hasText(activityExpectedOutput)) {
            sb.append("Expected Output: ").append(activityExpectedOutput).append("\n");
        }
        if (StringUtils.hasText(activityRubric)) {
            sb.append("Rubrics:\n").append(formatRubric(activityRubric)).append("\n");
        }
        if (StringUtils.hasText(activitySkillRequirementsJson)) {
            sb.append("Skill Requirements: ").append(activitySkillRequirementsJson).append("\n");
        }
        if (StringUtils.hasText(activityAiPromptHint)) {
            sb.append("AI Hint: ").append(activityAiPromptHint).append("\n");
        }
        
        sb.append("\n### Student Submission\n");
        if (StringUtils.hasText(submission.getEvidenceUrl())) {
            sb.append("Evidence URL: ").append(submission.getEvidenceUrl()).append("\n");
        }
        if (StringUtils.hasText(submission.getAttachmentUrl())) {
            sb.append("Attachment URL: ").append(submission.getAttachmentUrl()).append("\n");
        }
        sb.append("Submission Text:\n").append(submission.getSubmissionText() != null ? submission.getSubmissionText() : "").append("\n\n");
        
        sb.append(getJsonFormatInstructions());
        
        return sb.toString();
    }

    @Override
    public String buildFinalAssignmentPrompt(
            JourneyOutputAssessment assessment, 
            RoadmapTemplate template,
            String aggregatedSkills) {
            
        StringBuilder sb = new StringBuilder();
        sb.append("You are an expert mentor evaluating a student's final roadmap assignment.\n\n");
        
        if (StringUtils.hasText(template.getAiEvidencePrompt())) {
            sb.append("System Instructions:\n").append(template.getAiEvidencePrompt()).append("\n\n");
        }
        
        if (StringUtils.hasText(template.getAssessmentPolicy())) {
            sb.append("Global Assessment Policy:\n").append(template.getAssessmentPolicy()).append("\n\n");
        }
        
        if (StringUtils.hasText(template.getOutputStandard())) {
            sb.append("Output Standard:\n").append(template.getOutputStandard()).append("\n\n");
        }
        
        if (StringUtils.hasText(template.getTemplateInstructions())) {
            sb.append("Template Instructions:\n").append(template.getTemplateInstructions()).append("\n\n");
        }
        
        sb.append("### Final Assignment Context\n");
        if (StringUtils.hasText(template.getFinalAssignmentInstructions())) {
            sb.append("Instructions: ").append(template.getFinalAssignmentInstructions()).append("\n");
        }
        if (StringUtils.hasText(template.getFinalAssignmentRubric())) {
            sb.append("Rubrics:\n").append(formatRubric(template.getFinalAssignmentRubric())).append("\n");
        }
        if (StringUtils.hasText(aggregatedSkills)) {
            sb.append("Aggregated Roadmap Skills: ").append(aggregatedSkills).append("\n");
        }
        
        sb.append("\n### Student Submission\n");
        if (StringUtils.hasText(assessment.getEvidenceUrl())) {
            sb.append("Evidence URL: ").append(assessment.getEvidenceUrl()).append("\n");
        }
        if (StringUtils.hasText(assessment.getAttachmentUrl())) {
            sb.append("Attachment URL: ").append(assessment.getAttachmentUrl()).append("\n");
        }
        sb.append("Submission Text:\n").append(assessment.getSubmissionText() != null ? assessment.getSubmissionText() : "").append("\n\n");
        
        sb.append(getJsonFormatInstructions());
        
        return sb.toString();
    }
    
    private String formatRubric(String rubricJsonOrText) {
        if (rubricJsonOrText == null || rubricJsonOrText.trim().isEmpty()) {
            return "";
        }
        try {
            RoadmapRubricDto[] rubrics = objectMapper.readValue(rubricJsonOrText, RoadmapRubricDto[].class);
            if (rubrics != null && rubrics.length > 0) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < rubrics.length; i++) {
                    RoadmapRubricDto r = rubrics[i];
                    sb.append(i + 1).append(". ").append(r.getName());
                    if (r.getMaxPoints() != null) {
                        sb.append(" (Points: ").append(r.getMaxPoints()).append(")");
                    }
                    if (r.getDescription() != null && !r.getDescription().isEmpty()) {
                        sb.append(": ").append(r.getDescription());
                    }
                    if (i < rubrics.length - 1) {
                        sb.append("\n");
                    }
                }
                return sb.toString();
            }
        } catch (Exception e) {
            // Fallback for backward compatibility with plaintext rubrics
            return rubricJsonOrText;
        }
        return rubricJsonOrText;
    }

    private String getJsonFormatInstructions() {
        return "### Response Format\n" +
               "You MUST respond with valid JSON only. Do not include any markdown formatting like ```json or any other text.\n" +
               "The JSON object must have the following structure:\n" +
               "{\n" +
               "  \"scorePercent\": <integer between 0 and 100>,\n" +
               "  \"confidence\": <float between 0.0 and 1.0 representing your confidence in this evaluation>,\n" +
               "  \"feedback\": \"<detailed constructive feedback for the student>\",\n" +
               "  \"rubricBreakdownJson\": \"<optional JSON string representing detailed rubric scores, or null>\"\n" +
               "}\n";
    }
}

