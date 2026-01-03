package com.exe.skillverse_backend.chat_service.repository;

import com.exe.skillverse_backend.chat_service.entity.GroupChat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface GroupChatRepository extends JpaRepository<GroupChat, Long> {
    Optional<GroupChat> findByCourseId(Long courseId);
    boolean existsByCourseId(Long courseId);
}
