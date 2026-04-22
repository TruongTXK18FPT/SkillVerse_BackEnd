package com.exe.skillverse_backend.question_bank_service.service;

import com.exe.skillverse_backend.question_bank_service.dto.response.SkillResolveResponse;

public interface SkillResolveService {

    /**
     * Use AI to analyze a skill name and determine the best matching
     * domain, industry, and job role from the existing ExpertPromptConfig entries.
     */
    SkillResolveResponse resolveSkill(String skillName);

    /**
     * Resolve the skill via AI and auto-create the question bank if it doesn't exist.
     */
    SkillResolveResponse resolveAndCreateQuestionBank(String skillName);
}
