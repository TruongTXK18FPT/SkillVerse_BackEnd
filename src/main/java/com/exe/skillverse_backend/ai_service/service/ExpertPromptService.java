package com.exe.skillverse_backend.ai_service.service;

public interface ExpertPromptService {

    String getSystemPrompt(String domain, String industry, String jobRole);

    String getGenericExpertPrompt(String role);
}
