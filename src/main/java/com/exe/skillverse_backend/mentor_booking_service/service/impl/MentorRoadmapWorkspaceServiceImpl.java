package com.exe.skillverse_backend.mentor_booking_service.service.impl;

import com.exe.skillverse_backend.ai_service.dto.response.RoadmapResponse;
import com.exe.skillverse_backend.ai_service.entity.RoadmapSession;
import com.exe.skillverse_backend.ai_service.repository.RoadmapSessionRepository;
import com.exe.skillverse_backend.journey_service.entity.Journey;
import com.exe.skillverse_backend.journey_service.repository.JourneyRepository;
import com.exe.skillverse_backend.mentor_booking_service.dto.request.RoadmapMentorNodeReorderRequest;
import com.exe.skillverse_backend.mentor_booking_service.dto.request.RoadmapMentorNodeUpsertRequest;
import com.exe.skillverse_backend.mentor_booking_service.dto.request.RoadmapMentorOverviewUpdateRequest;
import com.exe.skillverse_backend.mentor_booking_service.dto.response.BookingResponse;
import com.exe.skillverse_backend.mentor_booking_service.dto.response.RoadmapFollowUpMeetingDTO;
import com.exe.skillverse_backend.mentor_booking_service.dto.response.RoadmapMentorWorkspaceResponse;
import com.exe.skillverse_backend.mentor_booking_service.entity.Booking;
import com.exe.skillverse_backend.mentor_booking_service.entity.BookingStatus;
import com.exe.skillverse_backend.mentor_booking_service.entity.RoadmapFollowUpMeeting;
import com.exe.skillverse_backend.mentor_booking_service.repository.BookingRepository;
import com.exe.skillverse_backend.mentor_booking_service.repository.RoadmapFollowUpMeetingRepository;
import com.exe.skillverse_backend.mentor_booking_service.service.BookingService;
import com.exe.skillverse_backend.mentor_booking_service.service.MentorRoadmapWorkspaceService;
import com.exe.skillverse_backend.shared.exception.ApiException;
import com.exe.skillverse_backend.shared.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MentorRoadmapWorkspaceServiceImpl implements MentorRoadmapWorkspaceService {

    /**
     * Booking statuses that allow the mentor to modify roadmap content.
     * - CONFIRMED: mentor has approved booking, can start preparing the roadmap
     *   (for non-ROADMAP_MENTORING bookings that go through CONFIRMED state)
     * - MENTORING_ACTIVE: the primary mentoring state for ROADMAP_MENTORING bookings
     * - PENDING_COMPLETION: mentoring is wrapping up but still allows final edits
     */
    private static final Set<BookingStatus> WRITABLE_STATUSES = Set.of(
            BookingStatus.CONFIRMED,
            BookingStatus.MENTORING_ACTIVE,
            BookingStatus.PENDING_COMPLETION);

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final JourneyRepository journeyRepository;
    private final RoadmapSessionRepository roadmapSessionRepository;
    private final RoadmapFollowUpMeetingRepository followUpMeetingRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> getMentorRoadmapBookings(Long mentorId) {
        return bookingRepository.findRoadmapMentoringBookingsForMentor(
                        mentorId,
                        List.of(
                                BookingStatus.PENDING,
                                BookingStatus.MENTORING_ACTIVE,
                                BookingStatus.PENDING_COMPLETION,
                                BookingStatus.COMPLETED,
                                BookingStatus.REJECTED,
                                BookingStatus.CANCELLED,
                                BookingStatus.REFUNDED))
                .stream()
                .map(booking -> bookingService.getBookingDetail(mentorId, booking.getId()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RoadmapMentorWorkspaceResponse getWorkspace(Long callerId, Long bookingId) {
        Booking booking = getRoadmapBookingOrThrow(bookingId);
        ensureReadAccess(callerId, booking);

        Journey journey = getJourneyForBooking(booking);
        List<RoadmapFollowUpMeetingDTO> meetings = followUpMeetingRepository
                .findByBookingIdOrderByScheduledAtAsc(bookingId)
                .stream()
                .map(RoadmapFollowUpMeetingDTO::from)
                .toList();

        // Journey exists but roadmap not generated yet → return workspace with empty roadmap
        if (journey.getRoadmapSessionId() == null) {
            log.info("Workspace for booking {} — journey {} has no roadmap session yet",
                    bookingId, journey.getId());
            return RoadmapMentorWorkspaceResponse.builder()
                    .booking(bookingService.getBookingDetail(callerId, bookingId))
                    .journeyId(journey.getId())
                    .roadmapSessionId(null)
                    .roadmap(RoadmapResponse.builder()
                            .roadmap(List.of())
                            .statistics(RoadmapResponse.RoadmapStatistics.builder()
                                    .totalNodes(0).mainNodes(0).sideNodes(0)
                                    .totalEstimatedHours(0.0).build())
                            .build())
                    .followUpMeetings(meetings)
                    .build();
        }

        RoadmapSession session = getRoadmapSession(journey.getRoadmapSessionId());
        RoadmapResponse roadmap = parseRoadmap(session);

        return RoadmapMentorWorkspaceResponse.builder()
                .booking(bookingService.getBookingDetail(callerId, bookingId))
                .journeyId(journey.getId())
                .roadmapSessionId(session.getId())
                .roadmap(roadmap)
                .followUpMeetings(meetings)
                .build();
    }

    @Override
    @Transactional
    public RoadmapMentorWorkspaceResponse updateOverview(
            Long mentorId,
            Long bookingId,
            RoadmapMentorOverviewUpdateRequest request) {
        Booking booking = getRoadmapBookingOrThrow(bookingId);
        ensureWriteAccess(mentorId, booking);
        Journey journey = getJourneyWithRoadmap(booking);
        RoadmapSession session = getRoadmapSession(journey.getRoadmapSessionId());
        RoadmapResponse roadmap = parseRoadmap(session);

        RoadmapResponse.Overview currentOverview = roadmap.getOverview() != null
                ? roadmap.getOverview()
                : RoadmapResponse.Overview.builder().build();

        currentOverview.setPurpose(request.getPurpose());
        currentOverview.setAudience(request.getAudience());
        currentOverview.setPostRoadmapState(request.getPostRoadmapState());
        roadmap.setOverview(currentOverview);
        roadmap.setStructure(request.getStructure() != null ? request.getStructure() : List.of());
        roadmap.setThinkingProgression(request.getThinkingProgression() != null ? request.getThinkingProgression() : List.of());
        roadmap.setNextSteps(request.getNextSteps());

        saveRoadmap(session, roadmap);
        return getWorkspace(mentorId, bookingId);
    }

    @Override
    @Transactional
    public RoadmapMentorWorkspaceResponse updateNode(
            Long mentorId,
            Long bookingId,
            String nodeId,
            RoadmapMentorNodeUpsertRequest request) {
        Booking booking = getRoadmapBookingOrThrow(bookingId);
        ensureWriteAccess(mentorId, booking);
        Journey journey = getJourneyWithRoadmap(booking);
        RoadmapSession session = getRoadmapSession(journey.getRoadmapSessionId());
        RoadmapResponse roadmap = parseRoadmap(session);

        List<RoadmapResponse.RoadmapNode> nodes = new ArrayList<>(roadmap.getRoadmap() != null ? roadmap.getRoadmap() : List.of());
        RoadmapResponse.RoadmapNode target = nodes.stream()
                .filter(node -> Objects.equals(node.getId(), nodeId))
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Không tìm thấy roadmap node"));

        applyNodeFields(target, request, false);
        roadmap.setRoadmap(canonicalizeNodes(nodes));
        updateRoadmapStatistics(roadmap);
        saveRoadmap(session, roadmap);

        return getWorkspace(mentorId, bookingId);
    }

    @Override
    @Transactional
    public RoadmapMentorWorkspaceResponse createNode(
            Long mentorId,
            Long bookingId,
            RoadmapMentorNodeUpsertRequest request) {
        Booking booking = getRoadmapBookingOrThrow(bookingId);
        ensureWriteAccess(mentorId, booking);
        Journey journey = getJourneyWithRoadmap(booking);
        RoadmapSession session = getRoadmapSession(journey.getRoadmapSessionId());
        RoadmapResponse roadmap = parseRoadmap(session);

        List<RoadmapResponse.RoadmapNode> nodes = new ArrayList<>(roadmap.getRoadmap() != null ? roadmap.getRoadmap() : List.of());
        String parentId = normalizeNodeId(request.getParentId());
        if (parentId != null && nodes.stream().noneMatch(node -> Objects.equals(node.getId(), parentId))) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "parentId không tồn tại trong roadmap");
        }

        String newNodeId = normalizeNodeId(request.getNodeId());
        if (newNodeId == null) {
            newNodeId = "mentor-node-" + System.currentTimeMillis();
        }
        final String generatedNodeId = newNodeId;
        if (nodes.stream().anyMatch(node -> Objects.equals(node.getId(), generatedNodeId))) {
            throw new ApiException(ErrorCode.CONFLICT, "nodeId đã tồn tại trong roadmap");
        }

        RoadmapResponse.RoadmapNode newNode = RoadmapResponse.RoadmapNode.builder()
                .id(newNodeId)
                .parentId(parentId)
                .children(new ArrayList<>())
                .nodeStatus("AVAILABLE")
                .type(request.getType() != null ? request.getType() : RoadmapResponse.RoadmapNode.NodeType.SIDE)
                .isCore(Boolean.TRUE.equals(request.getIsCore()))
                .build();
        applyNodeFields(newNode, request, true);
        nodes.add(newNode);

        roadmap.setRoadmap(canonicalizeNodes(nodes));
        updateRoadmapStatistics(roadmap);
        saveRoadmap(session, roadmap);

        return getWorkspace(mentorId, bookingId);
    }

    @Override
    @Transactional
    public RoadmapMentorWorkspaceResponse reorderNodes(
            Long mentorId,
            Long bookingId,
            RoadmapMentorNodeReorderRequest request) {
        Booking booking = getRoadmapBookingOrThrow(bookingId);
        ensureWriteAccess(mentorId, booking);
        Journey journey = getJourneyWithRoadmap(booking);
        RoadmapSession session = getRoadmapSession(journey.getRoadmapSessionId());
        RoadmapResponse roadmap = parseRoadmap(session);

        List<RoadmapResponse.RoadmapNode> nodes = new ArrayList<>(roadmap.getRoadmap() != null ? roadmap.getRoadmap() : List.of());
        String parentId = normalizeNodeId(request.getParentId());
        List<String> orderedNodeIds = request.getOrderedNodeIds() == null
                ? List.of()
                : request.getOrderedNodeIds().stream()
                        .map(this::normalizeNodeId)
                        .filter(Objects::nonNull)
                        .toList();

        List<RoadmapResponse.RoadmapNode> siblings = nodes.stream()
                .filter(node -> Objects.equals(normalizeNodeId(node.getParentId()), parentId))
                .toList();

        if (orderedNodeIds.isEmpty() || siblings.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Danh sách node cần sắp xếp không hợp lệ");
        }

        Set<String> siblingIds = siblings.stream().map(RoadmapResponse.RoadmapNode::getId).collect(Collectors.toCollection(LinkedHashSet::new));
        if (!siblingIds.containsAll(orderedNodeIds)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "orderedNodeIds chứa node không cùng parent");
        }

        List<String> finalOrder = new ArrayList<>(orderedNodeIds);
        siblingIds.stream().filter(id -> !finalOrder.contains(id)).forEach(finalOrder::add);
        Map<String, Integer> rank = new LinkedHashMap<>();
        for (int i = 0; i < finalOrder.size(); i++) {
            rank.put(finalOrder.get(i), i);
        }

        nodes.sort((left, right) -> {
            Integer leftRank = rank.get(left.getId());
            Integer rightRank = rank.get(right.getId());
            if (leftRank == null || rightRank == null) {
                return 0;
            }
            return Integer.compare(leftRank, rightRank);
        });

        if (parentId != null) {
            nodes.stream()
                    .filter(node -> Objects.equals(node.getId(), parentId))
                    .findFirst()
                    .ifPresent(parent -> parent.setChildren(finalOrder));
        }

        roadmap.setRoadmap(canonicalizeNodes(nodes));
        saveRoadmap(session, roadmap);
        return getWorkspace(mentorId, bookingId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoadmapFollowUpMeetingDTO> getFollowUps(Long callerId, Long bookingId) {
        Booking booking = getRoadmapBookingOrThrow(bookingId);
        ensureReadAccess(callerId, booking);
        return followUpMeetingRepository.findByBookingIdOrderByScheduledAtAsc(bookingId)
                .stream()
                .map(RoadmapFollowUpMeetingDTO::from)
                .toList();
    }

    @Override
    @Transactional
    public RoadmapFollowUpMeetingDTO createFollowUp(
            Long callerId,
            Long bookingId,
            RoadmapFollowUpMeetingDTO request) {
        Booking booking = getRoadmapBookingOrThrow(bookingId);
        ensureReadAccess(callerId, booking);

        if (request.getScheduledAt() == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "scheduledAt là bắt buộc");
        }
        String purpose = request.getPurpose();
        if (purpose == null || purpose.isBlank()) {
            purpose = request.getAgenda();
        }
        if (purpose == null || purpose.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "purpose (mục đích buổi họp) là bắt buộc");
        }

        boolean isMentor = Objects.equals(callerId, booking.getMentor().getId());
        String creatorRole = isMentor ? "MENTOR" : "LEARNER";
        // Creator đã tự động "accept" phía mình → chờ bên còn lại.
        String initialStatus = isMentor ? "PENDING_LEARNER" : "PENDING_MENTOR";

        RoadmapFollowUpMeeting entity = RoadmapFollowUpMeeting.builder()
                .bookingId(bookingId)
                .journeyId(booking.getJourneyId())
                .mentorId(booking.getMentor().getId())
                .learnerId(booking.getLearner().getId())
                .title(request.getTitle() == null || request.getTitle().isBlank() ? "Buổi họp roadmap" : request.getTitle())
                .agenda(request.getAgenda())
                .purpose(purpose)
                .scheduledAt(request.getScheduledAt())
                .durationMinutes(request.getDurationMinutes() == null || request.getDurationMinutes() <= 0 ? 30 : request.getDurationMinutes())
                .meetingLink(request.getMeetingLink())
                .status(initialStatus)
                .notes(request.getNotes())
                .createdByRole(creatorRole)
                .createdByUserId(callerId)
                .build();

        RoadmapFollowUpMeeting saved = followUpMeetingRepository.save(entity);
        // Auto-gen Jitsi nếu chưa có link.
        if (saved.getMeetingLink() == null || saved.getMeetingLink().isBlank()) {
            saved.setMeetingLink(generateJitsiLink(bookingId, saved.getId()));
            saved = followUpMeetingRepository.save(saved);
        }
        return RoadmapFollowUpMeetingDTO.from(saved);
    }

    @Override
    @Transactional
    public RoadmapFollowUpMeetingDTO updateFollowUp(
            Long callerId,
            Long bookingId,
            Long meetingId,
            RoadmapFollowUpMeetingDTO request) {
        Booking booking = getRoadmapBookingOrThrow(bookingId);
        ensureReadAccess(callerId, booking);

        RoadmapFollowUpMeeting entity = followUpMeetingRepository.findByIdAndBookingId(meetingId, bookingId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Không tìm thấy follow-up meeting"));

        // Chỉ creator được phép sửa, và không thể sửa khi đã ACCEPTED/COMPLETED.
        if (entity.getCreatedByUserId() != null && !Objects.equals(callerId, entity.getCreatedByUserId())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Chỉ người tạo meeting mới được chỉnh sửa");
        }
        if ("ACCEPTED".equalsIgnoreCase(entity.getStatus()) || "COMPLETED".equalsIgnoreCase(entity.getStatus())) {
            throw new ApiException(ErrorCode.CONFLICT, "Không thể sửa meeting đã được chấp nhận hoặc đã hoàn tất");
        }

        if (request.getTitle() != null) {
            entity.setTitle(request.getTitle());
        }
        if (request.getAgenda() != null) {
            entity.setAgenda(request.getAgenda());
        }
        if (request.getPurpose() != null && !request.getPurpose().isBlank()) {
            entity.setPurpose(request.getPurpose());
        }
        if (request.getScheduledAt() != null) {
            entity.setScheduledAt(request.getScheduledAt());
        }
        if (request.getDurationMinutes() != null && request.getDurationMinutes() > 0) {
            entity.setDurationMinutes(request.getDurationMinutes());
        }
        if (request.getMeetingLink() != null) {
            entity.setMeetingLink(request.getMeetingLink());
        }
        if (request.getNotes() != null) {
            entity.setNotes(request.getNotes());
        }

        return RoadmapFollowUpMeetingDTO.from(followUpMeetingRepository.save(entity));
    }

    @Override
    @Transactional
    public void deleteFollowUp(Long callerId, Long bookingId, Long meetingId) {
        Booking booking = getRoadmapBookingOrThrow(bookingId);
        ensureReadAccess(callerId, booking);
        RoadmapFollowUpMeeting entity = followUpMeetingRepository.findByIdAndBookingId(meetingId, bookingId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Không tìm thấy follow-up meeting"));
        boolean isMentor = Objects.equals(callerId, booking.getMentor().getId());
        boolean isCreator = entity.getCreatedByUserId() != null
                && Objects.equals(callerId, entity.getCreatedByUserId());
        if (!isMentor && !isCreator) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Chỉ mentor hoặc người tạo meeting mới được xóa");
        }
        followUpMeetingRepository.delete(entity);
    }

    @Override
    @Transactional
    public RoadmapFollowUpMeetingDTO acceptFollowUp(Long callerId, Long bookingId, Long meetingId) {
        Booking booking = getRoadmapBookingOrThrow(bookingId);
        ensureReadAccess(callerId, booking);

        RoadmapFollowUpMeeting entity = followUpMeetingRepository.findByIdAndBookingId(meetingId, bookingId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Không tìm thấy follow-up meeting"));

        boolean isMentor = Objects.equals(callerId, booking.getMentor().getId());
        String status = entity.getStatus() == null ? "" : entity.getStatus().toUpperCase();
        boolean callerCanAccept = (isMentor && "PENDING_MENTOR".equals(status))
                || (!isMentor && "PENDING_LEARNER".equals(status))
                // Back-compat: meeting cũ có status SCHEDULED — cho phép phía đối diện creator accept
                || ("SCHEDULED".equals(status)
                        && entity.getCreatedByUserId() != null
                        && !Objects.equals(callerId, entity.getCreatedByUserId()));
        if (!callerCanAccept) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Bạn không phải phía cần chấp nhận meeting này (trạng thái hiện tại: " + entity.getStatus() + ")");
        }
        entity.setStatus("ACCEPTED");
        entity.setAcceptedAt(LocalDateTime.now());
        if (entity.getMeetingLink() == null || entity.getMeetingLink().isBlank()) {
            entity.setMeetingLink(generateJitsiLink(bookingId, entity.getId()));
        }
        return RoadmapFollowUpMeetingDTO.from(followUpMeetingRepository.save(entity));
    }

    @Override
    @Transactional
    public RoadmapFollowUpMeetingDTO rejectFollowUp(Long callerId, Long bookingId, Long meetingId, String reason) {
        Booking booking = getRoadmapBookingOrThrow(bookingId);
        ensureReadAccess(callerId, booking);

        RoadmapFollowUpMeeting entity = followUpMeetingRepository.findByIdAndBookingId(meetingId, bookingId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Không tìm thấy follow-up meeting"));

        if ("ACCEPTED".equalsIgnoreCase(entity.getStatus()) || "COMPLETED".equalsIgnoreCase(entity.getStatus())) {
            throw new ApiException(ErrorCode.CONFLICT, "Không thể từ chối meeting đã chấp nhận hoặc đã hoàn tất");
        }
        entity.setStatus("REJECTED");
        entity.setRejectedAt(LocalDateTime.now());
        if (reason != null && !reason.isBlank()) {
            entity.setRejectReason(reason.length() > 500 ? reason.substring(0, 500) : reason);
        }
        return RoadmapFollowUpMeetingDTO.from(followUpMeetingRepository.save(entity));
    }

    private String generateJitsiLink(Long bookingId, Long meetingId) {
        // Room name ngẫu nhiên nhưng deterministic dựa trên booking + meeting + hash ngắn.
        String base = "skillverse-roadmap-" + bookingId + "-" + meetingId;
        String hash;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(base.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 4 && i < digest.length; i++) {
                sb.append(String.format("%02x", digest[i]));
            }
            hash = sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            hash = Long.toHexString(System.nanoTime() & 0xFFFFFF);
        }
        return "https://meet.jit.si/" + base + "-" + hash;
    }

    private Booking getRoadmapBookingOrThrow(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "Không tìm thấy booking #" + bookingId));
        if (!"ROADMAP_MENTORING".equals(booking.getBookingType())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Booking #" + bookingId + " không thuộc loại ROADMAP_MENTORING (hiện tại: "
                            + booking.getBookingType() + ")");
        }
        return booking;
    }

    /**
     * Get the Journey entity for a booking. Does NOT require roadmapSessionId to exist
     * (the session may not have been generated yet for new bookings).
     */
    private Journey getJourneyForBooking(Booking booking) {
        if (booking.getJourneyId() == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Booking #" + booking.getId() + " chưa gắn với journey. "
                            + "Học viên cần tạo booking với journeyId hợp lệ.");
        }

        return journeyRepository.findById(booking.getJourneyId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "Không tìm thấy journey #" + booking.getJourneyId()
                                + " (có thể đã bị xóa)"));
    }

    /**
     * Get the Journey entity for a booking AND require a valid roadmap session.
     * Used for write operations that modify roadmap content.
     */
    private Journey getJourneyWithRoadmap(Booking booking) {
        Journey journey = getJourneyForBooking(booking);

        if (journey.getRoadmapSessionId() == null) {
            throw new ApiException(ErrorCode.NOT_FOUND,
                    "Journey #" + journey.getId() + " chưa có roadmap session. "
                            + "Học viên cần hoàn thành assessment để hệ thống tạo roadmap trước khi mentor có thể chỉnh sửa.");
        }

        return journey;
    }

    private RoadmapSession getRoadmapSession(Long roadmapSessionId) {
        return roadmapSessionRepository.findById(roadmapSessionId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "Không tìm thấy roadmap session #" + roadmapSessionId));
    }

    private void ensureReadAccess(Long callerId, Booking booking) {
        if (!Objects.equals(callerId, booking.getMentor().getId())
                && !Objects.equals(callerId, booking.getLearner().getId())) {
            throw new ApiException(ErrorCode.FORBIDDEN,
                    "Bạn (userId=" + callerId + ") không có quyền xem workspace roadmap này. "
                            + "Chỉ mentor hoặc learner của booking mới có quyền.");
        }
    }

    private void ensureWriteAccess(Long mentorId, Booking booking) {
        if (!Objects.equals(mentorId, booking.getMentor().getId())) {
            throw new ApiException(ErrorCode.FORBIDDEN,
                    "Chỉ mentor của booking mới được chỉnh sửa roadmap này");
        }
        if (!WRITABLE_STATUSES.contains(booking.getStatus())) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Roadmap chỉ có thể chỉnh sửa khi booking ở trạng thái: "
                            + WRITABLE_STATUSES + " (hiện tại: " + booking.getStatus() + ")");
        }
    }

    private RoadmapResponse parseRoadmap(RoadmapSession session) {
        try {
            ObjectMapper snakeCaseMapper = objectMapper.copy()
                    .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
            com.fasterxml.jackson.databind.JsonNode root = snakeCaseMapper.readTree(session.getRoadmapJson());

            List<RoadmapResponse.RoadmapNode> nodes = root.path("roadmap").isArray()
                    ? new ArrayList<>(Arrays.asList(
                            snakeCaseMapper.treeToValue(root.path("roadmap"), RoadmapResponse.RoadmapNode[].class)))
                    : new ArrayList<>();

            RoadmapResponse roadmap = RoadmapResponse.builder()
                    .sessionId(session.getId())
                    .roadmapStatus(session.getStatus() != null ? session.getStatus().name() : "ACTIVE")
                    .metadata(root.has("roadmap_metadata")
                            ? snakeCaseMapper.treeToValue(root.path("roadmap_metadata"), RoadmapResponse.RoadmapMetadata.class)
                            : null)
                    .roadmap(canonicalizeNodes(nodes))
                    .statistics(root.has("roadmap_statistics")
                            ? snakeCaseMapper.treeToValue(root.path("roadmap_statistics"), RoadmapResponse.RoadmapStatistics.class)
                            : null)
                    .learningTips(root.has("learning_tips")
                            ? Arrays.asList(snakeCaseMapper.treeToValue(root.path("learning_tips"), String[].class))
                            : List.of())
                    .overview(root.has("overview")
                            ? snakeCaseMapper.treeToValue(root.path("overview"), RoadmapResponse.Overview.class)
                            : null)
                    .structure(root.has("structure")
                            ? Arrays.asList(snakeCaseMapper.treeToValue(root.path("structure"), RoadmapResponse.StructurePhase[].class))
                            : List.of())
                    .thinkingProgression(root.has("thinking_progression")
                            ? Arrays.asList(snakeCaseMapper.treeToValue(root.path("thinking_progression"), String[].class))
                            : List.of())
                    .projectsEvidence(root.has("projects_evidence")
                            ? Arrays.asList(snakeCaseMapper.treeToValue(root.path("projects_evidence"), RoadmapResponse.ProjectEvidence[].class))
                            : List.of())
                    .nextSteps(root.has("next_steps")
                            ? snakeCaseMapper.treeToValue(root.path("next_steps"), RoadmapResponse.NextSteps.class)
                            : null)
                    .skillDependencies(root.has("skill_dependencies")
                            ? Arrays.asList(snakeCaseMapper.treeToValue(root.path("skill_dependencies"), RoadmapResponse.SkillDependency[].class))
                            : List.of())
                    .createdAt(session.getCreatedAt())
                    .build();

            updateRoadmapStatistics(roadmap);
            return roadmap;
        } catch (Exception ex) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Không thể đọc dữ liệu roadmap để refactor");
        }
    }

    private void saveRoadmap(RoadmapSession session, RoadmapResponse roadmap) {
        try {
            ObjectMapper snakeCaseMapper = objectMapper.copy()
                    .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("roadmap_metadata", roadmap.getMetadata());
            root.put("overview", roadmap.getOverview());
            root.put("structure", roadmap.getStructure() != null ? roadmap.getStructure() : List.of());
            root.put("thinking_progression", roadmap.getThinkingProgression() != null ? roadmap.getThinkingProgression() : List.of());
            root.put("projects_evidence", roadmap.getProjectsEvidence() != null ? roadmap.getProjectsEvidence() : List.of());
            root.put("next_steps", roadmap.getNextSteps());
            root.put("skill_dependencies", roadmap.getSkillDependencies() != null ? roadmap.getSkillDependencies() : List.of());
            root.put("roadmap", roadmap.getRoadmap() != null ? roadmap.getRoadmap() : List.of());
            root.put("roadmap_statistics", roadmap.getStatistics());
            root.put("learning_tips", roadmap.getLearningTips() != null ? roadmap.getLearningTips() : List.of());
            session.setRoadmapJson(snakeCaseMapper.writeValueAsString(root));
            session.setTotalNodes(roadmap.getRoadmap() != null ? roadmap.getRoadmap().size() : 0);
            roadmapSessionRepository.save(session);
        } catch (JsonProcessingException ex) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Không thể lưu thay đổi roadmap");
        }
    }

    private void applyNodeFields(
            RoadmapResponse.RoadmapNode target,
            RoadmapMentorNodeUpsertRequest request,
            boolean isCreate) {
        if (request.getParentId() != null) {
            target.setParentId(normalizeNodeId(request.getParentId()));
        }
        if (request.getTitle() != null) {
            target.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            target.setDescription(request.getDescription());
        }
        if (request.getEstimatedTimeMinutes() != null) {
            target.setEstimatedTimeMinutes(request.getEstimatedTimeMinutes());
        }
        if (request.getType() != null) {
            target.setType(request.getType());
        } else if (isCreate && target.getType() == null) {
            target.setType(RoadmapResponse.RoadmapNode.NodeType.SIDE);
        }
        if (request.getDifficulty() != null) {
            target.setDifficulty(request.getDifficulty());
        }
        if (request.getIsCore() != null) {
            target.setIsCore(request.getIsCore());
        }
        if (request.getLearningObjectives() != null) {
            target.setLearningObjectives(new ArrayList<>(request.getLearningObjectives()));
        }
        if (request.getKeyConcepts() != null) {
            target.setKeyConcepts(new ArrayList<>(request.getKeyConcepts()));
        }
        if (request.getPracticalExercises() != null) {
            target.setPracticalExercises(new ArrayList<>(request.getPracticalExercises()));
        }
        if (request.getSuggestedResources() != null) {
            target.setSuggestedResources(new ArrayList<>(request.getSuggestedResources()));
        }
        if (request.getSuccessCriteria() != null) {
            target.setSuccessCriteria(new ArrayList<>(request.getSuccessCriteria()));
        }
        if (request.getPrerequisites() != null) {
            target.setPrerequisites(new ArrayList<>(request.getPrerequisites()));
        }
        if (request.getSuggestedCourseIds() != null) {
            target.setSuggestedCourseIds(new ArrayList<>(request.getSuggestedCourseIds()));
        }
        if (request.getSuggestedModuleIds() != null) {
            target.setSuggestedModuleIds(new ArrayList<>(request.getSuggestedModuleIds()));
        }

        if (target.getChildren() == null) {
            target.setChildren(new ArrayList<>());
        }
    }

    private List<RoadmapResponse.RoadmapNode> canonicalizeNodes(List<RoadmapResponse.RoadmapNode> rawNodes) {
        Map<String, RoadmapResponse.RoadmapNode> byId = new LinkedHashMap<>();
        List<RoadmapResponse.RoadmapNode> normalized = new ArrayList<>();

        for (RoadmapResponse.RoadmapNode node : rawNodes) {
            if (node.getId() == null || node.getId().isBlank()) {
                continue;
            }
            if (node.getChildren() == null) {
                node.setChildren(new ArrayList<>());
            }
            node.setParentId(normalizeNodeId(node.getParentId()));
            byId.put(node.getId(), node);
            normalized.add(node);
        }

        normalized.forEach(node -> node.setChildren(new ArrayList<>()));

        for (RoadmapResponse.RoadmapNode node : normalized) {
            String parentId = normalizeNodeId(node.getParentId());
            if (parentId == null) {
                continue;
            }
            if (Objects.equals(parentId, node.getId()) || !byId.containsKey(parentId)) {
                node.setParentId(null);
                continue;
            }
            RoadmapResponse.RoadmapNode parent = byId.get(parentId);
            if (!parent.getChildren().contains(node.getId())) {
                parent.getChildren().add(node.getId());
            }
        }

        normalized.forEach(node -> node.setChildren(node.getChildren().stream()
                .filter(byId::containsKey)
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new))));

        return normalized;
    }

    private void updateRoadmapStatistics(RoadmapResponse roadmap) {
        List<RoadmapResponse.RoadmapNode> nodes = roadmap.getRoadmap() != null ? roadmap.getRoadmap() : List.of();
        long mainNodes = nodes.stream()
                .filter(node -> node.getType() == RoadmapResponse.RoadmapNode.NodeType.MAIN || Boolean.TRUE.equals(node.getIsCore()))
                .count();
        double totalEstimatedHours = nodes.stream()
                .map(RoadmapResponse.RoadmapNode::getEstimatedTimeMinutes)
                .filter(Objects::nonNull)
                .mapToDouble(minutes -> minutes / 60.0d)
                .sum();
        Map<String, Integer> difficultyDistribution = new LinkedHashMap<>();
        nodes.stream()
                .map(RoadmapResponse.RoadmapNode::getDifficulty)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .forEach(value -> difficultyDistribution.merge(value.toLowerCase(), 1, Integer::sum));

        roadmap.setStatistics(RoadmapResponse.RoadmapStatistics.builder()
                .totalNodes(nodes.size())
                .mainNodes((int) mainNodes)
                .sideNodes(Math.max(0, nodes.size() - (int) mainNodes))
                .totalEstimatedHours(totalEstimatedHours)
                .difficultyDistribution(difficultyDistribution)
                .build());
    }

    private String normalizeNodeId(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
