package com.exe.skillverse_backend.meowl_chat_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration for Meowl Chat Service with Gemini API
 */
@Configuration
public class MeowlConfig {

    @Value("${meowl.gemini.api-key}")
    private String apiKey;

    @Value("${meowl.gemini.api-url:https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent}")
    private String apiUrl;

    @Value("${meowl.gemini.model:gemini-2.0-flash}")
    private String model;

    @Value("${meowl.reminder.enabled:true}")
    private boolean reminderEnabled;

    @Value("${meowl.notification.enabled:true}")
    private boolean notificationEnabled;

    @Bean
    public RestTemplate meowlRestTemplate() {
        return new RestTemplate();
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public String getModel() {
        return model;
    }

    public boolean isReminderEnabled() {
        return reminderEnabled;
    }

    public boolean isNotificationEnabled() {
        return notificationEnabled;
    }
}
