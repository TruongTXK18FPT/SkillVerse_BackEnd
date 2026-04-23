package com.exe.skillverse_backend.mentor_booking_service.dto.response;

import com.exe.skillverse_backend.mentor_booking_service.entity.RoadmapFollowUpMeeting;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoadmapFollowUpMeetingDTO {
    private Long id;
    private Long bookingId;
    private Long journeyId;
    private Long mentorId;
    private Long learnerId;
    private String title;
    private String agenda;
    private LocalDateTime scheduledAt;
    private Integer durationMinutes;
    private String meetingLink;
    private String status;
    private String notes;

    public static RoadmapFollowUpMeetingDTO from(RoadmapFollowUpMeeting entity) {
        return RoadmapFollowUpMeetingDTO.builder()
                .id(entity.getId())
                .bookingId(entity.getBookingId())
                .journeyId(entity.getJourneyId())
                .mentorId(entity.getMentorId())
                .learnerId(entity.getLearnerId())
                .title(entity.getTitle())
                .agenda(entity.getAgenda())
                .scheduledAt(entity.getScheduledAt())
                .durationMinutes(entity.getDurationMinutes())
                .meetingLink(entity.getMeetingLink())
                .status(entity.getStatus())
                .notes(entity.getNotes())
                .build();
    }
}
