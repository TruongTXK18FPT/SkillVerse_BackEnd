package com.exe.skillverse_backend.chat_service.repository;

import com.exe.skillverse_backend.chat_service.entity.GroupChatMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupChatMemberRepository extends JpaRepository<GroupChatMember, Long> {
    Optional<GroupChatMember> findByGroupIdAndUserId(Long groupId, Long userId);
    boolean existsByGroupIdAndUserId(Long groupId, Long userId);
    List<GroupChatMember> findByUserId(Long userId);
    List<GroupChatMember> findByGroupId(Long groupId);
    int countByGroupId(Long groupId);
}
