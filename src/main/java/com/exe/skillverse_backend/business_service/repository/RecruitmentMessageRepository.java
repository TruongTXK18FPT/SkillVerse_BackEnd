package com.exe.skillverse_backend.business_service.repository;

import com.exe.skillverse_backend.business_service.entity.RecruitmentMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecruitmentMessageRepository extends JpaRepository<RecruitmentMessage, Long> {

    /**
     * Lấy tin nhắn của một session với phân trang
     */
    @Query("SELECT rm FROM RecruitmentMessage rm " +
            "WHERE rm.session.id = :sessionId " +
            "ORDER BY rm.createdAt DESC")
    Page<RecruitmentMessage> findBySessionIdOrderByCreatedAtDesc(
            @Param("sessionId") Long sessionId,
            Pageable pageable);

    /**
     * Lấy tin nhắn của một session (mới nhất trước)
     */
    @Query("SELECT rm FROM RecruitmentMessage rm " +
            "WHERE rm.session.id = :sessionId " +
            "ORDER BY rm.createdAt ASC")
    List<RecruitmentMessage> findBySessionId(
            @Param("sessionId") Long sessionId);

    /**
     * Đếm tin nhắn chưa đọc trong một session cho một user
     */
    @Query("SELECT COUNT(rm) FROM RecruitmentMessage rm " +
            "WHERE rm.session.id = :sessionId " +
            "AND rm.sender.id <> :userId " +
            "AND rm.isRead = false")
    long countUnreadBySessionIdAndUserId(
            @Param("sessionId") Long sessionId,
            @Param("userId") Long userId);

    /**
     * Đánh dấu tất cả tin nhắn trong session là đã đọc
     */
    @Modifying
    @Query("UPDATE RecruitmentMessage rm SET rm.isRead = true, rm.readAt = CURRENT_TIMESTAMP " +
            "WHERE rm.session.id = :sessionId " +
            "AND rm.sender.id <> :userId " +
            "AND rm.isRead = false")
    void markAllAsReadBySessionIdAndUserId(
            @Param("sessionId") Long sessionId,
            @Param("userId") Long userId);

    /**
     * Lấy tin nhắn mới nhất của một session
     */
    @Query("SELECT rm FROM RecruitmentMessage rm " +
            "WHERE rm.session.id = :sessionId " +
            "ORDER BY rm.createdAt DESC " +
            "LIMIT 1")
    RecruitmentMessage findTopBySessionIdOrderByCreatedAtDesc(
            @Param("sessionId") Long sessionId);

    /**
     * Tìm tin nhắn action trong session
     */
    @Query("SELECT rm FROM RecruitmentMessage rm " +
            "WHERE rm.session.id = :sessionId " +
            "AND rm.actionType IS NOT NULL " +
            "ORDER BY rm.createdAt DESC")
    List<RecruitmentMessage> findActionMessagesBySessionId(
            @Param("sessionId") Long sessionId);

    /**
     * Đếm tổng tin nhắn trong session
     */
    long countBySessionId(Long sessionId);
}
