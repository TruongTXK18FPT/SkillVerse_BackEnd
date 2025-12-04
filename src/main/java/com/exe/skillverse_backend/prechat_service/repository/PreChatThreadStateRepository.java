package com.exe.skillverse_backend.prechat_service.repository;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.prechat_service.entity.PreChatThreadState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PreChatThreadStateRepository extends JpaRepository<PreChatThreadState, Long> {
    Optional<PreChatThreadState> findByMentorAndLearner(User mentor, User learner);
}

