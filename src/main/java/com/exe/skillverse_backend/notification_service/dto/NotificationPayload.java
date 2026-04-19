package com.exe.skillverse_backend.notification_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPayload {
    private Action action;
    private Resource resource;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Action {
        private String key;
        private String path;
        private String anchor;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Resource {
        private Long courseId;
        private Long assignmentId;
        private Long submissionId;
    }

    public static NotificationPayload forCoursePurchase(Long courseId) {
        return NotificationPayload.builder()
                .action(Action.builder()
                        .key("COURSE_PURCHASE_SUCCESS")
                        .path("/dashboard")
                        .anchor("modules-section")
                        .build())
                .resource(Resource.builder()
                        .courseId(courseId)
                        .build())
                .build();
    }

    public static NotificationPayload forAssignmentGraded(Long assignmentId, Long submissionId) {
        String actionPath = assignmentId != null ? "/assignment/" + assignmentId : null;
        return NotificationPayload.builder()
                .action(Action.builder()
                        .key("ASSIGNMENT_GRADED")
                        .path(actionPath)
                        .anchor(null)
                        .build())
                .resource(Resource.builder()
                        .assignmentId(assignmentId)
                        .submissionId(submissionId)
                        .build())
                .build();
    }
}
