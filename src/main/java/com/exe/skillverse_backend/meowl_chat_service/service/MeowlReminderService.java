package com.exe.skillverse_backend.meowl_chat_service.service;

import com.exe.skillverse_backend.meowl_chat_service.dto.MeowlChatResponse;
import java.util.List;

public interface MeowlReminderService {
    
    List<MeowlChatResponse.MeowlReminder> getRemindersForUser(Long userId, String language);
    
    List<MeowlChatResponse.MeowlNotification> getNotifications(Long userId, String language);
}
