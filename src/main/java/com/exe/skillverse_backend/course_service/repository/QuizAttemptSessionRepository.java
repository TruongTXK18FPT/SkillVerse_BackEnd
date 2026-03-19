package com.exe.skillverse_backend.course_service.repository;

import com.exe.skillverse_backend.course_service.entity.QuizAttemptSession;
import com.exe.skillverse_backend.course_service.entity.enums.QuizAttemptSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface QuizAttemptSessionRepository extends JpaRepository<QuizAttemptSession, Long> {

    @Transactional(readOnly = true)
    @Query("""
            SELECT qas
            FROM QuizAttemptSession qas
            WHERE qas.quiz.id = :quizId
              AND qas.userId = :userId
              AND qas.status = :status
              AND qas.expiresAt > :now
            ORDER BY qas.lastSeenAt DESC
            """)
    Optional<QuizAttemptSession> findLatestActiveSession(
            @Param("quizId") Long quizId,
            @Param("userId") Long userId,
            @Param("status") QuizAttemptSessionStatus status,
            @Param("now") Instant now
    );

    @Transactional(readOnly = true)
    @Query("""
            SELECT qas
            FROM QuizAttemptSession qas
            WHERE qas.quiz.id = :quizId
              AND qas.userId = :userId
              AND qas.sessionToken = :sessionToken
              AND qas.status = :status
              AND qas.expiresAt > :now
            """)
    Optional<QuizAttemptSession> findActiveSessionByToken(
            @Param("quizId") Long quizId,
            @Param("userId") Long userId,
            @Param("sessionToken") String sessionToken,
            @Param("status") QuizAttemptSessionStatus status,
            @Param("now") Instant now
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE QuizAttemptSession qas
            SET qas.status = :expiredStatus,
                qas.lastSeenAt = :now
            WHERE qas.quiz.id = :quizId
              AND qas.userId = :userId
              AND qas.status = :inProgressStatus
              AND qas.expiresAt <= :now
            """)
    int expireStaleSessions(
            @Param("quizId") Long quizId,
            @Param("userId") Long userId,
            @Param("inProgressStatus") QuizAttemptSessionStatus inProgressStatus,
            @Param("expiredStatus") QuizAttemptSessionStatus expiredStatus,
            @Param("now") Instant now
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE QuizAttemptSession qas
            SET qas.lastSeenAt = :now,
                qas.expiresAt = :expiresAt
            WHERE qas.id = :sessionId
            """)
    int touchSession(
            @Param("sessionId") Long sessionId,
            @Param("now") Instant now,
            @Param("expiresAt") Instant expiresAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE QuizAttemptSession qas
            SET qas.status = :submittedStatus,
                qas.submittedAt = :submittedAt,
                qas.lastSeenAt = :submittedAt,
                qas.expiresAt = :submittedAt
            WHERE qas.quiz.id = :quizId
              AND qas.userId = :userId
              AND qas.status = :inProgressStatus
            """)
    int markActiveSessionsSubmitted(
            @Param("quizId") Long quizId,
            @Param("userId") Long userId,
            @Param("inProgressStatus") QuizAttemptSessionStatus inProgressStatus,
            @Param("submittedStatus") QuizAttemptSessionStatus submittedStatus,
            @Param("submittedAt") Instant submittedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE QuizAttemptSession qas
            SET qas.status = :submittedStatus,
                qas.submittedAt = :submittedAt,
                qas.lastSeenAt = :submittedAt,
                qas.expiresAt = :submittedAt
            WHERE qas.quiz.id = :quizId
              AND qas.userId = :userId
              AND qas.sessionToken = :sessionToken
              AND qas.status = :inProgressStatus
            """)
    int markSessionSubmitted(
            @Param("quizId") Long quizId,
            @Param("userId") Long userId,
            @Param("sessionToken") String sessionToken,
            @Param("inProgressStatus") QuizAttemptSessionStatus inProgressStatus,
            @Param("submittedStatus") QuizAttemptSessionStatus submittedStatus,
            @Param("submittedAt") Instant submittedAt
    );

    @Transactional(readOnly = true)
    @Query("""
            SELECT CASE WHEN COUNT(qas) > 0 THEN true ELSE false END
            FROM QuizAttemptSession qas
            JOIN qas.quiz q
            JOIN q.module m
            WHERE m.course.id = :courseId
              AND qas.userId = :userId
              AND qas.status = :inProgressStatus
              AND qas.expiresAt > CURRENT_TIMESTAMP
            """)
    boolean existsActiveSessionByCourseAndUser(
            @Param("courseId") Long courseId,
            @Param("userId") Long userId,
            @Param("inProgressStatus") QuizAttemptSessionStatus inProgressStatus
    );
}
