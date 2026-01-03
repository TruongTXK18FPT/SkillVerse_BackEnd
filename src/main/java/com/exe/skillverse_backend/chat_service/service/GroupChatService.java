package com.exe.skillverse_backend.chat_service.service;

import com.exe.skillverse_backend.chat_service.dto.GroupChatCreateRequest;
import com.exe.skillverse_backend.chat_service.dto.GroupChatMessageDTO;
import com.exe.skillverse_backend.chat_service.dto.GroupChatResponse;
import com.exe.skillverse_backend.chat_service.dto.GroupMemberDTO;

import java.util.List;

public interface GroupChatService {
    GroupChatResponse createGroup(GroupChatCreateRequest request, Long mentorId);
    GroupChatResponse updateGroup(Long groupId, GroupChatCreateRequest request, Long mentorId);
    void joinGroup(Long groupId, Long userId);
    void leaveGroup(Long groupId, Long userId);
    void kickMember(Long groupId, Long userId, Long mentorId);
    GroupChatMessageDTO saveMessage(GroupChatMessageDTO message);
    List<GroupChatMessageDTO> getGroupMessages(Long groupId, Long userId);
    List<GroupChatResponse> getMyGroups(Long userId);
    GroupChatResponse getGroupByCourseId(Long courseId, Long userId);
    
    /**
     * Get detailed group info including member list
     */
    GroupChatResponse getGroupDetail(Long groupId, Long userId);
    
    /**
     * Get list of members in a group
     */
    List<GroupMemberDTO> getGroupMembers(Long groupId, Long userId);
    
    /**
     * Get member count for a group
     */
    int getGroupMemberCount(Long groupId);
}
