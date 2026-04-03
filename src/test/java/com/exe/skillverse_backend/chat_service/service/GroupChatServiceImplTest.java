package com.exe.skillverse_backend.chat_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.chat_service.dto.GroupChatCreateRequest;
import com.exe.skillverse_backend.chat_service.dto.GroupChatMessageDTO;
import com.exe.skillverse_backend.chat_service.entity.GroupChat;
import com.exe.skillverse_backend.chat_service.entity.GroupChatMember;
import com.exe.skillverse_backend.chat_service.entity.GroupChatMessage;
import com.exe.skillverse_backend.chat_service.repository.GroupChatMemberRepository;
import com.exe.skillverse_backend.chat_service.repository.GroupChatMessageRepository;
import com.exe.skillverse_backend.chat_service.repository.GroupChatRepository;
import com.exe.skillverse_backend.chat_service.service.impl.GroupChatServiceImpl;
import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.course_service.repository.CoursePurchaseRepository;
import com.exe.skillverse_backend.course_service.repository.CourseRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupChatServiceImplTest {

    @Mock
    private GroupChatRepository groupChatRepository;

    @Mock
    private GroupChatMemberRepository groupChatMemberRepository;

    @Mock
    private GroupChatMessageRepository groupChatMessageRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CoursePurchaseRepository coursePurchaseRepository;

    @Mock
    private UserRepository userRepository;

    private GroupChatServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new GroupChatServiceImpl(
                groupChatRepository,
                groupChatMemberRepository,
                groupChatMessageRepository,
                courseRepository,
                coursePurchaseRepository,
                userRepository);

        lenient().when(groupChatRepository.save(any(GroupChat.class))).thenAnswer(invocation -> {
            GroupChat group = invocation.getArgument(0);
            if (group.getId() == null) {
                group.setId(10L);
            }
            return group;
        });
        lenient().when(groupChatMessageRepository.save(any(GroupChatMessage.class))).thenAnswer(invocation -> {
            GroupChatMessage message = invocation.getArgument(0);
            if (message.getId() == null) {
                message.setId(77L);
            }
            return message;
        });
    }

    @Test
    @DisplayName("createGroup should reject users who do not own the course")
    void createGroup_ShouldRejectUsersWhoDoNotOwnTheCourse() {
        Course course = Course.builder()
                .id(5L)
                .title("Spring Boot")
                .author(User.builder().id(99L).build())
                .createdAt(Instant.now())
                .build();
        when(courseRepository.findById(5L)).thenReturn(Optional.of(course));

        assertThrows(AccessDeniedException.class, () -> service.createGroup(
                GroupChatCreateRequest.builder().courseId(5L).name("General").build(),
                1L));
    }

    @Test
    @DisplayName("joinGroup should require a purchased course for students")
    void joinGroup_ShouldRequirePurchasedCourseForStudents() {
        GroupChat group = GroupChat.builder()
                .id(10L)
                .courseId(5L)
                .mentorId(1L)
                .name("General")
                .build();
        when(groupChatRepository.findById(10L)).thenReturn(Optional.of(group));
        when(groupChatMemberRepository.existsByGroupIdAndUserId(10L, 2L)).thenReturn(false);
        when(coursePurchaseRepository.hasUserPurchasedCourse(2L, 5L)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> service.joinGroup(10L, 2L));
        verify(groupChatMemberRepository, never()).save(any(GroupChatMember.class));
    }

    @Test
    @DisplayName("getGroupMessages should backfill sender information for members")
    void getGroupMessages_ShouldBackfillSenderInformationForMembers() {
        GroupChatMessage message = GroupChatMessage.builder()
                .id(77L)
                .groupId(10L)
                .senderId(2L)
                .content("Hello group")
                .messageType("TEXT")
                .timestamp(LocalDateTime.now())
                .build();

        when(groupChatMemberRepository.existsByGroupIdAndUserId(10L, 2L)).thenReturn(true);
        when(groupChatMessageRepository.findByGroupIdOrderByTimestampAsc(10L)).thenReturn(List.of(message));
        when(userRepository.findById(2L)).thenReturn(Optional.of(User.builder()
                .id(2L)
                .email("student@skillverse.vn")
                .firstName("Student")
                .lastName("One")
                .avatarUrl("avatar.png")
                .build()));

        List<GroupChatMessageDTO> messages = service.getGroupMessages(10L, 2L);

        assertEquals(1, messages.size());
        assertEquals("Student One", messages.get(0).getSenderName());
        assertEquals("avatar.png", messages.get(0).getSenderAvatarUrl());
        assertTrue(messages.get(0).getTimestamp() != null);
    }

    @Test
    @DisplayName("saveMessage should default the message type to TEXT")
    void saveMessage_ShouldDefaultTheMessageTypeToText() {
        GroupChatMessageDTO saved = service.saveMessage(GroupChatMessageDTO.builder()
                .groupId(10L)
                .senderId(2L)
                .senderName("Student")
                .content("Hi")
                .build());

        assertEquals(77L, saved.getId());
        assertEquals("TEXT", saved.getMessageType());
        assertTrue(saved.getTimestamp() != null);
    }
}
