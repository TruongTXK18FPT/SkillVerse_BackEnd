package com.exe.skillverse_backend.meowl_chat_service.repository;

import com.exe.skillverse_backend.meowl_chat_service.entity.MeowlChatMessage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MeowlChatMessageRepository extends JpaRepository<MeowlChatMessage, UUID> {
    
    List<MeowlChatMessage> findByUserIdOrderByCreatedAtAsc(Long userId);
    
    List<MeowlChatMessage> findTop50ByUserIdOrderByCreatedAtDesc(Long userId);

    void deleteByUserId(Long userId);
}
