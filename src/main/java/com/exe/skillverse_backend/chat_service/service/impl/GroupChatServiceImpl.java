package com.exe.skillverse_backend.chat_service.service.impl;

import com.exe.skillverse_backend.chat_service.dto.GroupChatCreateRequest;
import com.exe.skillverse_backend.chat_service.dto.GroupChatMessageDTO;
import com.exe.skillverse_backend.chat_service.dto.GroupChatResponse;
import com.exe.skillverse_backend.chat_service.dto.GroupMemberDTO;
import com.exe.skillverse_backend.chat_service.entity.GroupChat;
import com.exe.skillverse_backend.chat_service.entity.GroupChatMember;
import com.exe.skillverse_backend.chat_service.entity.GroupChatMessage;
import com.exe.skillverse_backend.chat_service.repository.GroupChatMemberRepository;
import com.exe.skillverse_backend.chat_service.repository.GroupChatMessageRepository;
import com.exe.skillverse_backend.chat_service.repository.GroupChatRepository;
import com.exe.skillverse_backend.chat_service.service.GroupChatService;
import com.exe.skillverse_backend.course_service.repository.CoursePurchaseRepository;
import com.exe.skillverse_backend.course_service.repository.CourseRepository;
import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.auth_service.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GroupChatServiceImpl implements GroupChatService {

    private final GroupChatRepository groupChatRepository;
    private final GroupChatMemberRepository groupChatMemberRepository;
    private final GroupChatMessageRepository groupChatMessageRepository;
    private final CourseRepository courseRepository;
    private final CoursePurchaseRepository coursePurchaseRepository;
    private final UserRepository userRepository;

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Override
    @Transactional
    public GroupChatResponse createGroup(GroupChatCreateRequest request, Long mentorId) {
        Course course = courseRepository.findById(request.getCourseId())
                .orElseThrow(() -> new IllegalArgumentException("Course not found"));

        if (!course.getAuthor().getId().equals(mentorId)) {
            throw new AccessDeniedException("Only the course mentor can create a group chat");
        }

        if (groupChatRepository.existsByCourseId(request.getCourseId())) {
            throw new IllegalArgumentException("Group chat already exists for this course");
        }

        GroupChat groupChat = GroupChat.builder()
                .courseId(request.getCourseId())
                .mentorId(mentorId)
                .name(request.getName())
                .avatarUrl(request.getAvatarUrl())
                .createdAt(LocalDateTime.now(VN_ZONE))
                .build();

        GroupChat saved = groupChatRepository.save(groupChat);

        // Add mentor as member
        GroupChatMember member = GroupChatMember.builder()
                .groupId(saved.getId())
                .userId(mentorId)
                .role(GroupChatMember.Role.MENTOR)
                .joinedAt(LocalDateTime.now(VN_ZONE))
                .build();
        groupChatMemberRepository.save(member);

        return mapToResponse(saved, true);
    }

    @Override
    @Transactional
    public GroupChatResponse updateGroup(Long groupId, GroupChatCreateRequest request, Long mentorId) {
        GroupChat group = groupChatRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));

        if (!group.getMentorId().equals(mentorId)) {
            throw new AccessDeniedException("Only the mentor can update this group");
        }

        if (request.getName() != null && !request.getName().isEmpty()) {
            group.setName(request.getName());
        }
        if (request.getAvatarUrl() != null) {
            group.setAvatarUrl(request.getAvatarUrl());
        }

        return mapToResponse(groupChatRepository.save(group), true);
    }

    @Override
    @Transactional
    public void joinGroup(Long groupId, Long userId) {
        GroupChat group = groupChatRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));

        if (groupChatMemberRepository.existsByGroupIdAndUserId(groupId, userId)) {
            return; // Already joined
        }

        // Check if user is the mentor
        if (group.getMentorId().equals(userId)) {
             GroupChatMember member = GroupChatMember.builder()
                .groupId(groupId)
                .userId(userId)
                .role(GroupChatMember.Role.MENTOR)
                .joinedAt(LocalDateTime.now(VN_ZONE))
                .build();
            groupChatMemberRepository.save(member);
            return;
        }

        // Check purchase
        boolean hasPurchased = coursePurchaseRepository.hasUserPurchasedCourse(userId, group.getCourseId());
        if (!hasPurchased) {
            throw new AccessDeniedException("User must purchase the course to join group");
        }

        GroupChatMember member = GroupChatMember.builder()
                .groupId(groupId)
                .userId(userId)
                .role(GroupChatMember.Role.STUDENT)
                .joinedAt(LocalDateTime.now(VN_ZONE))
                .build();
        groupChatMemberRepository.save(member);
    }

    @Override
    @Transactional
    public void leaveGroup(Long groupId, Long userId) {
        GroupChatMember member = groupChatMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found"));
        groupChatMemberRepository.delete(member);
    }

    @Override
    @Transactional
    public void kickMember(Long groupId, Long userId, Long mentorId) {
        GroupChat group = groupChatRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));

        if (!group.getMentorId().equals(mentorId)) {
            throw new AccessDeniedException("Only mentor can kick members");
        }

        GroupChatMember member = groupChatMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found"));

        if (member.getRole() == GroupChatMember.Role.MENTOR) {
            throw new IllegalArgumentException("Cannot kick mentor");
        }

        groupChatMemberRepository.delete(member);
    }

    @Override
    @Transactional
    public GroupChatMessageDTO saveMessage(GroupChatMessageDTO message) {
        // Normalize timestamp
        message.setTimestamp(LocalDateTime.now(VN_ZONE));
        
        // Set default message type if not provided
        if (message.getMessageType() == null || message.getMessageType().isEmpty()) {
            message.setMessageType("TEXT");
        }

        GroupChatMessage entity = GroupChatMessage.builder()
                .groupId(message.getGroupId())
                .senderId(message.getSenderId())
                .senderName(message.getSenderName())
                .content(message.getContent())
                .messageType(message.getMessageType())
                .gifUrl(message.getGifUrl())
                .imageUrl(message.getImageUrl())
                .emojiCode(message.getEmojiCode())
                .senderAvatarUrl(message.getSenderAvatarUrl())
                .timestamp(message.getTimestamp())
                .build();

        GroupChatMessage saved = groupChatMessageRepository.save(entity);
        message.setId(saved.getId());
        return message;
    }

    @Override
    public List<GroupChatMessageDTO> getGroupMessages(Long groupId, Long userId) {
        if (!groupChatMemberRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw new AccessDeniedException("Not a member of this group");
        }

        return groupChatMessageRepository.findByGroupIdOrderByTimestampAsc(groupId).stream()
                .map(entity -> GroupChatMessageDTO.builder()
                        .id(entity.getId())
                        .groupId(entity.getGroupId())
                        .senderId(entity.getSenderId())
                        .senderName(entity.getSenderName())
                        .content(entity.getContent())
                        .messageType(entity.getMessageType())
                        .gifUrl(entity.getGifUrl())
                        .imageUrl(entity.getImageUrl())
                        .emojiCode(entity.getEmojiCode())
                        .senderAvatarUrl(entity.getSenderAvatarUrl())
                        .timestamp(entity.getTimestamp())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public List<GroupChatResponse> getMyGroups(Long userId) {
        List<GroupChatMember> memberships = groupChatMemberRepository.findByUserId(userId);
        return memberships.stream()
                .map(member -> {
                    GroupChat group = groupChatRepository.findById(member.getGroupId()).orElse(null);
                    if (group == null) return null;
                    return mapToResponse(group, true);
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public GroupChatResponse getGroupByCourseId(Long courseId, Long userId) {
        return groupChatRepository.findByCourseId(courseId)
                .map(group -> mapToResponse(group, groupChatMemberRepository.existsByGroupIdAndUserId(group.getId(), userId)))
                .orElse(null);
    }


    @Override
    public GroupChatResponse getGroupDetail(Long groupId, Long userId) {
        GroupChat group = groupChatRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));
        
        boolean isMember = groupChatMemberRepository.existsByGroupIdAndUserId(groupId, userId);
        GroupChatResponse response = mapToResponse(group, isMember);
        
        // Include members list if user is a member
        if (isMember) {
            response.setMembers(getGroupMembers(groupId, userId));
        }
        
        return response;
    }

    @Override
    public List<GroupMemberDTO> getGroupMembers(Long groupId, Long userId) {
        if (!groupChatMemberRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw new AccessDeniedException("Not a member of this group");
        }
        
        List<GroupChatMember> members = groupChatMemberRepository.findByGroupId(groupId);
        return members.stream()
                .map(member -> {
                    User user = userRepository.findById(member.getUserId()).orElse(null);
                    if (user == null) return null;
                    
                    return GroupMemberDTO.builder()
                            .userId(user.getId())
                            .userName(user.getFullName())
                            .email(user.getEmail())
                            .avatarUrl(user.getAvatarUrl())
                            .role(member.getRole().name())
                            .joinedAt(member.getJoinedAt())
                            .isOnline(false) // Can be implemented with presence tracking
                            .build();
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public int getGroupMemberCount(Long groupId) {
        return groupChatMemberRepository.countByGroupId(groupId);
    }

    private GroupChatResponse mapToResponse(GroupChat group, boolean isMember) {
        int memberCount = groupChatMemberRepository.countByGroupId(group.getId());
        String mentorName = userRepository.findById(group.getMentorId())
                .map(User::getFullName)
                .orElse("Unknown Mentor");
        
        return GroupChatResponse.builder()
                .id(group.getId())
                .courseId(group.getCourseId())
                .mentorId(group.getMentorId())
                .mentorName(mentorName)
                .name(group.getName())
                .avatarUrl(group.getAvatarUrl())
                .createdAt(group.getCreatedAt())
                .isMember(isMember)
                .memberCount(memberCount)
                .build();
    }
}
