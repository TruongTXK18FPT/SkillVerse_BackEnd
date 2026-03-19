package com.exe.skillverse_backend.chat_service.repository;

import com.exe.skillverse_backend.chat_service.entity.GroupChatMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GroupChatMessageRepository extends JpaRepository<GroupChatMessage, Long> {
    List<GroupChatMessage> findByGroupIdOrderByTimestampAsc(Long groupId);
}
