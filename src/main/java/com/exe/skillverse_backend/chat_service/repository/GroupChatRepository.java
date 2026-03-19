package com.exe.skillverse_backend.chat_service.repository;

import com.exe.skillverse_backend.chat_service.entity.GroupChat;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GroupChatRepository extends JpaRepository<GroupChat, Long> {
    Optional<GroupChat> findByCourseId(Long courseId);
    boolean existsByCourseId(Long courseId);
}
