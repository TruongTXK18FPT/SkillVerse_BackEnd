package com.exe.skillverse_backend.chat_service.repository;

import com.exe.skillverse_backend.chat_service.entity.GroupChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GroupChatMessageRepository extends JpaRepository<GroupChatMessage, Long> {
    List<GroupChatMessage> findByGroupIdOrderByTimestampAsc(Long groupId);
}
