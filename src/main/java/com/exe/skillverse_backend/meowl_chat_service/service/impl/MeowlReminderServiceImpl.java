package com.exe.skillverse_backend.meowl_chat_service.service.impl;

import com.exe.skillverse_backend.meowl_chat_service.dto.MeowlChatResponse;
import com.exe.skillverse_backend.meowl_chat_service.service.MeowlReminderService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Service for managing learning reminders and notifications
 * Sends cute reminders about courses, skills, and roadmaps
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MeowlReminderServiceImpl implements MeowlReminderService {

    // Cute reminder templates
    private static final Map<String, Map<String, String>> REMINDER_TEMPLATES = new HashMap<>();

    static {
        // Course reminders - English
        Map<String, String> courseEn = new HashMap<>();
        courseEn.put("incomplete",
                "Meow! 🐱 You have {count} course(s) waiting for you! Let's continue learning together! 📚✨");
        courseEn.put("almost_done",
                "You're so close! 🌟 Just a little more and you'll complete {courseName}! Keep going! 💪");
        courseEn.put("not_started",
                "Hey! 👋 Ready to start {courseName}? I'm here to help you every step of the way! 🚀");
        REMINDER_TEMPLATES.put("course_en", courseEn);

        // Course reminders - Vietnamese
        Map<String, String> courseVi = new HashMap<>();
        courseVi.put("incomplete", "Meo! 🐱 Bạn còn {count} khóa học đang chờ đấy! Cùng học tiếp nào! 📚✨");
        courseVi.put("almost_done", "Sắp xong rồi! 🌟 Chỉ còn một chút nữa là hoàn thành {courseName}! Cố lên! 💪");
        courseVi.put("not_started", "Này! 👋 Sẵn sàng bắt đầu {courseName} chưa? Mình sẽ ở đây hỗ trợ bạn! 🚀");
        REMINDER_TEMPLATES.put("course_vi", courseVi);

        // Skill reminders - English
        Map<String, String> skillEn = new HashMap<>();
        skillEn.put("practice", "Time to practice! 🎯 Your {skillName} skill needs some love today! Let's do this! 💫");
        skillEn.put("new_skill",
                "Exciting! ✨ There's a new skill waiting: {skillName}! Want to explore it together? 🌈");
        skillEn.put("level_up", "Amazing! 🎉 You're ready to level up your {skillName}! Let's reach new heights! 🚀");
        REMINDER_TEMPLATES.put("skill_en", skillEn);

        // Skill reminders - Vietnamese
        Map<String, String> skillVi = new HashMap<>();
        skillVi.put("practice",
                "Đến giờ luyện tập rồi! 🎯 Kỹ năng {skillName} của bạn cần được chăm chút hôm nay! Làm thôi! 💫");
        skillVi.put("new_skill", "Thú vị quá! ✨ Có kỹ năng mới đang chờ: {skillName}! Cùng khám phá nhé? 🌈");
        skillVi.put("level_up", "Tuyệt vời! 🎉 Bạn đã sẵn sàng nâng cấp {skillName}! Chinh phục đỉnh cao nào! 🚀");
        REMINDER_TEMPLATES.put("skill_vi", skillVi);

        // Roadmap reminders - English
        Map<String, String> roadmapEn = new HashMap<>();
        roadmapEn.put("continue",
                "Your roadmap is calling! 🗺️ Let's continue the journey to {goalName}! Adventure awaits! 🌟");
        roadmapEn.put("milestone", "Woohoo! 🎊 You've reached a milestone on your {roadmapName} roadmap! So proud! 💖");
        roadmapEn.put("stuck", "Need a paw? 🐾 I noticed you've been on {questName} for a while. Let me help! 💡");
        REMINDER_TEMPLATES.put("roadmap_en", roadmapEn);

        // Roadmap reminders - Vietnamese
        Map<String, String> roadmapVi = new HashMap<>();
        roadmapVi.put("continue",
                "Lộ trình đang gọi bạn! 🗺️ Tiếp tục hành trình đến {goalName} nào! Phiêu lưu đang chờ! 🌟");
        roadmapVi.put("milestone", "Yayyy! 🎊 Bạn đã đạt được cột mốc trong lộ trình {roadmapName}! Tự hào quá! 💖");
        roadmapVi.put("stuck", "Cần giúp không? 🐾 Mình thấy bạn đang ở {questName} lâu rồi. Để mình giúp nhé! 💡");
        REMINDER_TEMPLATES.put("roadmap_vi", roadmapVi);
    }

    /**
     * Get personalized learning reminders for a user
     */
    public List<MeowlChatResponse.MeowlReminder> getRemindersForUser(Long userId, String language) {
        List<MeowlChatResponse.MeowlReminder> reminders = new ArrayList<>();

        try {
            // TODO: Get actual user data from database
            // For now, generate sample reminders

            // Course reminder
            reminders.add(MeowlChatResponse.MeowlReminder.builder()
                    .type("course")
                    .title(language.equals("vi") ? "Tiếp tục khóa học" : "Continue Course")
                    .description(getReminderMessage("course", "incomplete", language,
                            Collections.singletonMap("count", "2")))
                    .actionUrl("/courses")
                    .emoji("📚")
                    .build());

            // Skill practice reminder
            reminders.add(MeowlChatResponse.MeowlReminder.builder()
                    .type("skill")
                    .title(language.equals("vi") ? "Luyện tập kỹ năng" : "Practice Skills")
                    .description(getReminderMessage("skill", "practice", language,
                            Collections.singletonMap("skillName", language.equals("vi") ? "Lập trình" : "Programming")))
                    .actionUrl("/skills")
                    .emoji("🎯")
                    .build());

            // Roadmap progress reminder
            reminders.add(MeowlChatResponse.MeowlReminder.builder()
                    .type("roadmap")
                    .title(language.equals("vi") ? "Tiếp tục lộ trình" : "Continue Roadmap")
                    .description(getReminderMessage("roadmap", "continue", language,
                            Collections.singletonMap("goalName",
                                    language.equals("vi") ? "Fullstack Developer" : "Fullstack Developer")))
                    .actionUrl("/roadmap")
                    .emoji("🗺️")
                    .build());

        } catch (Exception e) {
            log.error("Error getting reminders for user {}: ", userId, e);
        }

        return reminders;
    }

    /**
     * Get general notifications for the user
     */
    public List<MeowlChatResponse.MeowlNotification> getNotifications(Long userId, String language) {
        List<MeowlChatResponse.MeowlNotification> notifications = new ArrayList<>();

        try {
            // Daily motivation
            notifications.add(MeowlChatResponse.MeowlNotification.builder()
                    .type("motivation")
                    .message(getMotivationalMessage(language))
                    .emoji("💪")
                    .createdAt(LocalDateTime.now())
                    .build());

            // Learning tip
            notifications.add(MeowlChatResponse.MeowlNotification.builder()
                    .type("tip")
                    .message(getLearningTip(language))
                    .emoji("💡")
                    .createdAt(LocalDateTime.now())
                    .build());

        } catch (Exception e) {
            log.error("Error getting notifications for user {}: ", userId, e);
        }

        return notifications;
    }

    /**
     * Get a formatted reminder message
     */
    private String getReminderMessage(String type, String subtype, String language, Map<String, String> params) {
        String key = type + "_" + language;
        Map<String, String> templates = REMINDER_TEMPLATES.get(key);

        if (templates == null || !templates.containsKey(subtype)) {
            return language.equals("vi")
                    ? "Meo! 🐱 Đừng quên học tập hôm nay nhé! ✨"
                    : "Meow! 🐱 Don't forget to learn today! ✨";
        }

        String message = templates.get(subtype);

        // Replace placeholders
        if (params != null) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                message = message.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }

        return message;
    }

    /**
     * Get a random motivational message
     */
    private String getMotivationalMessage(String language) {
        String[] messagesEn = {
                "You're doing amazing! Keep up the great work! 🌟",
                "Every step forward is progress! You got this! 🚀",
                "Learning is a journey, not a race. Enjoy it! 🌈",
                "You're becoming better every day! So proud! 💖",
                "Small progress is still progress! Keep going! ✨"
        };

        String[] messagesVi = {
                "Bạn làm tuyệt lắm! Tiếp tục phát huy nhé! 🌟",
                "Mỗi bước tiến là tiến bộ! Bạn làm được mà! 🚀",
                "Học tập là hành trình, không phải cuộc đua. Tận hưởng nó! 🌈",
                "Bạn ngày càng tiến bộ! Tự hào quá! 💖",
                "Tiến bộ nhỏ vẫn là tiến bộ! Cố lên! ✨"
        };

        String[] messages = language.equals("vi") ? messagesVi : messagesEn;
        Random random = new Random();
        return messages[random.nextInt(messages.length)];
    }

    /**
     * Get a random learning tip
     */
    private String getLearningTip(String language) {
        String[] tipsEn = {
                "Try teaching what you learn to others - it helps you understand better! 📖",
                "Take regular breaks to let your brain process new information! 🧠",
                "Practice a little every day - consistency is key! 🔑",
                "Don't be afraid to make mistakes - that's how we learn! 🌱",
                "Set small, achievable goals to stay motivated! 🎯"
        };

        String[] tipsVi = {
                "Hãy thử dạy lại những gì bạn học cho người khác - nó giúp bạn hiểu sâu hơn! 📖",
                "Nghỉ giải lao thường xuyên để não bộ xử lý thông tin mới! 🧠",
                "Luyện tập một chút mỗi ngày - sự kiên trì là chìa khóa! 🔑",
                "Đừng sợ mắc lỗi - đó là cách chúng ta học! 🌱",
                "Đặt mục tiêu nhỏ, dễ đạt được để giữ động lực! 🎯"
        };

        String[] tips = language.equals("vi") ? tipsVi : tipsEn;
        Random random = new Random();
        return tips[random.nextInt(tips.length)];
    }

    /**
     * Scheduled task to send daily reminders (runs at 9 AM every day)
     */
    @Scheduled(cron = "0 0 9 * * *")
    public void sendDailyReminders() {
        log.info("Sending daily learning reminders...");

        try {
            // TODO: Get all active users and send reminders
            // For now, just log
            log.info("Daily reminders would be sent to all users here");

        } catch (Exception e) {
            log.error("Error sending daily reminders: ", e);
        }
    }

    /**
     * Scheduled task to check for incomplete courses (runs every 3 hours)
     */
    @Scheduled(cron = "0 0 */3 * * *")
    public void checkIncompleteCourses() {
        log.info("Checking for incomplete courses...");

        try {
            // TODO: Query database for users with incomplete courses
            // Send gentle reminders via notification
            log.info("Incomplete course check completed");

        } catch (Exception e) {
            log.error("Error checking incomplete courses: ", e);
        }
    }
}
