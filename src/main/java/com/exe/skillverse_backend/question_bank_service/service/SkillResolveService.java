package com.exe.skillverse_backend.question_bank_service.service;

import com.exe.skillverse_backend.question_bank_service.dto.response.SkillResolveResponse;

public interface SkillResolveService {

    /**
     * Resolve a skill name to the best matching domain, industry, and job role
     * from the existing ExpertPromptConfig entries using deterministic smart search.
     */
    SkillResolveResponse resolveSkill(String skillName);

    /**
     * Resolve the skill and auto-create the question bank if it doesn't exist.
     */
    SkillResolveResponse resolveAndCreateQuestionBank(String skillName);
}
