package com.exe.skillverse_backend.prechat_service.repository;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.prechat_service.entity.PreChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PreChatMessageRepository extends JpaRepository<PreChatMessage, Long> {
    Page<PreChatMessage> findByMentorAndLearnerOrderByCreatedAtAsc(User mentor, User learner, Pageable pageable);
    long countByMentorAndLearner(User mentor, User learner);

    long countByMentorAndLearnerAndSenderAndReadByMentorFalse(User mentor, User learner, User sender);
    long countByMentorAndLearnerAndSenderAndReadByLearnerFalse(User mentor, User learner, User sender);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update PreChatMessage m set m.readByMentor=true where m.mentor=?1 and m.learner=?2 and m.sender=?2 and m.readByMentor=false")
    int markMentorRead(User mentor, User learner);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update PreChatMessage m set m.readByLearner=true where m.mentor=?1 and m.learner=?2 and m.sender=?1 and m.readByLearner=false")
    int markLearnerRead(User mentor, User learner);

    @org.springframework.data.jpa.repository.Query("SELECT m FROM PreChatMessage m WHERE (m.mentor.id = :userId1 AND m.learner.id = :userId2) OR (m.mentor.id = :userId2 AND m.learner.id = :userId1) ORDER BY m.createdAt ASC")
    Page<PreChatMessage> findConversation(@org.springframework.data.repository.query.Param("userId1") Long userId1, @org.springframework.data.repository.query.Param("userId2") Long userId2, Pageable pageable);

    @org.springframework.data.jpa.repository.Query(value = "select m.* from prechat_messages m join (select mentor_id, learner_id, max(created_at) last_created from prechat_messages where mentor_id=?1 or learner_id=?1 group by mentor_id, learner_id) t on m.mentor_id=t.mentor_id and m.learner_id=t.learner_id and m.created_at=t.last_created order by m.created_at desc", nativeQuery = true)
    java.util.List<PreChatMessage> findLastThreads(Long userId);
}
