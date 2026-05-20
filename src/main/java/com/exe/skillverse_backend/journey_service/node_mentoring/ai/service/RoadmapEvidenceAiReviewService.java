package com.exe.skillverse_backend.journey_service.node_mentoring.ai.service;

import com.exe.skillverse_backend.journey_service.node_mentoring.entity.AdminReviewDecision;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.RoadmapEvidenceAiReview;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.RoadmapNodeSubmission;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.JourneyOutputAssessment;
import com.exe.skillverse_backend.roadmap_package_service.entity.RoadmapTemplate;

public interface RoadmapEvidenceAiReviewService {
    
    void reviewNodeEvidence(RoadmapNodeSubmission submission, RoadmapTemplate template, 
                            String nodeTitle, String nodeDescription, String nodeSkills, 
                            String activityExpectedOutput, String activityRubric, 
                            String activityAiPromptHint, String activitySkillRequirementsJson);
                            
    void reviewFinalAssignment(JourneyOutputAssessment assessment, RoadmapTemplate template, 
                               String aggregatedSkills);
                               
    RoadmapEvidenceAiReview decideReview(Long reviewId, AdminReviewDecision decision, String reason, Long adminId);
}

