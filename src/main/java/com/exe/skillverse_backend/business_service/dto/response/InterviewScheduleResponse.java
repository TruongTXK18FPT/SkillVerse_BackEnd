package com.exe.skillverse_backend.business_service.dto.response;

import com.exe.skillverse_backend.business_service.entity.InterviewSchedule.InterviewStatus;
import com.exe.skillverse_backend.business_service.entity.InterviewSchedule.MeetingType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewScheduleResponse {

    private Long id;
    private Long applicationId;
    private String candidateName;
    private String candidateEmail;
    private String candidateAvatarUrl;
    private String jobTitle;
    private LocalDateTime scheduledAt;
    private Integer durationMinutes;
    private MeetingType meetingType;
    private String meetingLink;
    private String skillverseRoomId;
    private String location;
    private String interviewerName;
    private String interviewNotes;
    private InterviewStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}