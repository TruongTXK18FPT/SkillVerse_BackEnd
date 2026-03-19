package com.exe.skillverse_backend.chat_service.repository;

import com.exe.skillverse_backend.chat_service.entity.UserChatMessageEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserChatMessageRepository extends JpaRepository<UserChatMessageEntity, Long> {
    List<UserChatMessageEntity> findBySenderIdAndRecipientId(Long senderId, Long recipientId);
}
