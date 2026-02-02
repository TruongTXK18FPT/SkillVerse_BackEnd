package com.exe.skillverse_backend.meowl_chat_service.repository;

import com.exe.skillverse_backend.meowl_chat_service.entity.MeowlChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MeowlChatMessageRepository extends JpaRepository<MeowlChatMessage, UUID> {
    
    List<MeowlChatMessage> findByUserIdOrderByCreatedAtAsc(Long userId);
    
    List<MeowlChatMessage> findTop50ByUserIdOrderByCreatedAtDesc(Long userId);

    void deleteByUserId(Long userId);
}
