package com.exe.skillverse_backend.ai_service.service;

public interface ITPromptService {
    String getPrompt(String domain, String industry, String normalizedRole);

    String getBackendDeveloperPrompt();

    String getFrontendDeveloperPrompt();

    String getFullstackDeveloperPrompt();

    String getMobileDeveloperPrompt();

    String getWebDeveloperPrompt();

    String getGameDeveloperPrompt();

    String getDevOpsEngineerPrompt();

    String getSoftwareArchitectPrompt();

    String getAutomationQAPrompt();

    String getManualTesterPrompt();

    String getDataAnalystPrompt();

    String getBusinessIntelligencePrompt();

    String getDataEngineerPrompt();

    String getMachineLearningEngineerPrompt();

    String getAiEngineerPrompt();

    String getDataScientistPrompt();

    String getPromptEngineerPrompt();

    String getCybersecurityAnalystPrompt();

    String getSecurityEngineerPrompt();

    String getPentesterPrompt();

    String getSocAnalystPrompt();

    String getNetworkSecurityEngineerPrompt();

    String getCloudEngineerPrompt();

    String getCloudArchitectPrompt();

    String getSystemAdministratorPrompt();

    String getNetworkEngineerPrompt();

    String getProductManagerPrompt();

    String getProductOwnerPrompt();

    String getBusinessAnalystPrompt();
}
