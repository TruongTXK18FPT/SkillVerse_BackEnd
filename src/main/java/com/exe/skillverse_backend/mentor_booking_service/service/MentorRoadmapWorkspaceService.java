package com.exe.skillverse_backend.mentor_booking_service.service;

import com.exe.skillverse_backend.mentor_booking_service.dto.request.RoadmapMentorNodeReorderRequest;
import com.exe.skillverse_backend.mentor_booking_service.dto.request.RoadmapMentorNodeUpsertRequest;
import com.exe.skillverse_backend.mentor_booking_service.dto.request.RoadmapMentorOverviewUpdateRequest;
import com.exe.skillverse_backend.mentor_booking_service.dto.response.BookingResponse;
import com.exe.skillverse_backend.mentor_booking_service.dto.response.RoadmapFollowUpMeetingDTO;
import com.exe.skillverse_backend.mentor_booking_service.dto.response.RoadmapMentorWorkspaceResponse;
import java.util.List;

public interface MentorRoadmapWorkspaceService {

    List<BookingResponse> getMentorRoadmapBookings(Long mentorId);

    RoadmapMentorWorkspaceResponse getWorkspace(Long callerId, Long bookingId);

    RoadmapMentorWorkspaceResponse updateOverview(
            Long mentorId,
            Long bookingId,
            RoadmapMentorOverviewUpdateRequest request);

    RoadmapMentorWorkspaceResponse updateNode(
            Long mentorId,
            Long bookingId,
            String nodeId,
            RoadmapMentorNodeUpsertRequest request);

    RoadmapMentorWorkspaceResponse createNode(
            Long mentorId,
            Long bookingId,
            RoadmapMentorNodeUpsertRequest request);

    RoadmapMentorWorkspaceResponse reorderNodes(
            Long mentorId,
            Long bookingId,
            RoadmapMentorNodeReorderRequest request);

    List<RoadmapFollowUpMeetingDTO> getFollowUps(Long callerId, Long bookingId);

    RoadmapFollowUpMeetingDTO createFollowUp(
            Long callerId,
            Long bookingId,
            RoadmapFollowUpMeetingDTO request);

    RoadmapFollowUpMeetingDTO updateFollowUp(
            Long callerId,
            Long bookingId,
            Long meetingId,
            RoadmapFollowUpMeetingDTO request);

    void deleteFollowUp(Long callerId, Long bookingId, Long meetingId);

    RoadmapFollowUpMeetingDTO acceptFollowUp(Long callerId, Long bookingId, Long meetingId);

    RoadmapFollowUpMeetingDTO rejectFollowUp(Long callerId, Long bookingId, Long meetingId, String reason);

    RoadmapFollowUpMeetingDTO completeFollowUp(Long callerId, Long bookingId, Long meetingId);
}
