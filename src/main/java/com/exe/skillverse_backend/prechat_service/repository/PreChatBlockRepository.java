package com.exe.skillverse_backend.prechat_service.repository;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.prechat_service.entity.PreChatBlock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PreChatBlockRepository extends JpaRepository<PreChatBlock, Long> {
    Optional<PreChatBlock> findByMentorAndLearner(User mentor, User learner);
    boolean existsByMentorAndLearner(User mentor, User learner);
}

