package com.exe.skillverse_backend.chat_service.controller;

import com.exe.skillverse_backend.chat_service.dto.GroupChatCreateRequest;
import com.exe.skillverse_backend.chat_service.dto.GroupChatMessageDTO;
import com.exe.skillverse_backend.chat_service.dto.GroupChatResponse;
import com.exe.skillverse_backend.chat_service.dto.GroupMemberDTO;
import com.exe.skillverse_backend.chat_service.service.GroupChatService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/group-chats")
@RequiredArgsConstructor
@Slf4j
public class GroupChatController {

    private final GroupChatService groupChatService;
    private final SimpMessagingTemplate messagingTemplate;

    @PostMapping
    @PreAuthorize("hasRole('MENTOR')")
    public ResponseEntity<GroupChatResponse> createGroup(
            @RequestParam Long mentorId,
            @RequestBody GroupChatCreateRequest request) {
        return ResponseEntity.ok(groupChatService.createGroup(request, mentorId));
    }

    @PutMapping("/{groupId}")
    @PreAuthorize("hasRole('MENTOR')")
    public ResponseEntity<GroupChatResponse> updateGroup(
            @PathVariable Long groupId,
            @RequestParam Long mentorId,
            @RequestBody GroupChatCreateRequest request) {
        return ResponseEntity.ok(groupChatService.updateGroup(groupId, request, mentorId));
    }

    @PostMapping("/{groupId}/join")
    @PreAuthorize("hasAnyRole('USER', 'MENTOR')")
    public ResponseEntity<Void> joinGroup(
            @PathVariable Long groupId,
            @RequestParam Long userId) {
        groupChatService.joinGroup(groupId, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{groupId}/leave")
    @PreAuthorize("hasAnyRole('USER', 'MENTOR')")
    public ResponseEntity<Void> leaveGroup(
            @PathVariable Long groupId,
            @RequestParam Long userId) {
        groupChatService.leaveGroup(groupId, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{groupId}/kick")
    @PreAuthorize("hasRole('MENTOR')")
    public ResponseEntity<Void> kickMember(
            @PathVariable Long groupId,
            @RequestParam Long mentorId,
            @RequestParam Long targetUserId) {
        groupChatService.kickMember(groupId, targetUserId, mentorId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{groupId}/messages")
    @PreAuthorize("hasAnyRole('USER', 'MENTOR')")
    public ResponseEntity<List<GroupChatMessageDTO>> getMessages(
            @PathVariable Long groupId,
            @RequestParam Long userId) {
        return ResponseEntity.ok(groupChatService.getGroupMessages(groupId, userId));
    }

    @PostMapping("/{groupId}/messages")
    @PreAuthorize("hasAnyRole('USER', 'MENTOR')")
    public ResponseEntity<GroupChatMessageDTO> sendMessage(
            @PathVariable Long groupId,
            @RequestBody GroupChatMessageDTO message) {
        log.info("Received REST message for group {}", groupId);
        message.setGroupId(groupId);
        GroupChatMessageDTO saved = groupChatService.saveMessage(message);
        
        // Broadcast to WebSocket topic
        messagingTemplate.convertAndSend(
                "/topic/group." + groupId,
                saved
        );
        
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/my-groups")
    @PreAuthorize("hasAnyRole('USER', 'MENTOR')")
    public ResponseEntity<List<GroupChatResponse>> getMyGroups(
            @RequestParam Long userId) {
        return ResponseEntity.ok(groupChatService.getMyGroups(userId));
    }

    @GetMapping("/course/{courseId}")
    public ResponseEntity<GroupChatResponse> getGroupByCourse(
            @PathVariable Long courseId,
            @RequestParam(required = false) Long userId) {
        // userId can be null if user is not logged in, but ideally we check membership if logged in
        return ResponseEntity.ok(groupChatService.getGroupByCourseId(courseId, userId));
    }

    /**
     * Get detailed group info including members list and member count
     */
    @GetMapping("/{groupId}/detail")
    @PreAuthorize("hasAnyRole('USER', 'MENTOR')")
    public ResponseEntity<GroupChatResponse> getGroupDetail(
            @PathVariable Long groupId,
            @RequestParam Long userId) {
        return ResponseEntity.ok(groupChatService.getGroupDetail(groupId, userId));
    }

    /**
     * Get list of members in a group
     */
    @GetMapping("/{groupId}/members")
    @PreAuthorize("hasAnyRole('USER', 'MENTOR')")
    public ResponseEntity<List<GroupMemberDTO>> getGroupMembers(
            @PathVariable Long groupId,
            @RequestParam Long userId) {
        return ResponseEntity.ok(groupChatService.getGroupMembers(groupId, userId));
    }

    /**
     * Get member count for a group
     */
    @GetMapping("/{groupId}/member-count")
    @PreAuthorize("hasAnyRole('USER', 'MENTOR')")
    public ResponseEntity<Integer> getMemberCount(@PathVariable Long groupId) {
        return ResponseEntity.ok(groupChatService.getGroupMemberCount(groupId));
    }

    @MessageMapping("/group.chat")
    public void processGroupMessage(@Payload GroupChatMessageDTO message) {
        log.info("Received group message for group {}", message.getGroupId());
        GroupChatMessageDTO saved = groupChatService.saveMessage(message);
        
        // Broadcast to group topic
        messagingTemplate.convertAndSend(
                "/topic/group." + message.getGroupId(),
                saved
        );
    }
}
