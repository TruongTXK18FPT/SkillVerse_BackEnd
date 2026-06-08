package com.exe.skillverse_backend.journey_service.repository;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.journey_service.entity.Journey;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface JourneyRepository extends JpaRepository<Journey, Long> {

    /**
     * Find all journeys for a user
     */
    List<Journey> findByUser(User user);

    /**
     * Find all journeys for a user with pagination
     */
    Page<Journey> findByUser(User user, Pageable pageable);

    /**
     * Find journey by ID and user
     */
    Optional<Journey> findByIdAndUser(Long id, User user);

    /**
     * Find active journey for a user (non-terminal journey statuses only).
     * Terminal statuses: COMPLETED, CANCELLED, COMPLETED_UNVERIFIED, COMPLETED_VERIFIED.
     */
    @Query("""
            SELECT j FROM Journey j
            WHERE j.user = :user
              AND j.status NOT IN (
                  com.exe.skillverse_backend.journey_service.entity.Journey.JourneyStatus.COMPLETED,
                  com.exe.skillverse_backend.journey_service.entity.Journey.JourneyStatus.CANCELLED,
                  com.exe.skillverse_backend.journey_service.entity.Journey.JourneyStatus.COMPLETED_UNVERIFIED,
                  com.exe.skillverse_backend.journey_service.entity.Journey.JourneyStatus.COMPLETED_VERIFIED,
                  com.exe.skillverse_backend.journey_service.entity.Journey.JourneyStatus.AWAITING_VERIFICATION
              )
            ORDER BY j.lastActivityAt DESC
            """)
    List<Journey> findActiveJourneysByUser(@Param("user") User user);

    /**
     * Find journey by roadmap session ID
     */
    Optional<Journey> findByRoadmapSessionId(Long roadmapSessionId);

    /**
     * Count journeys by user
     */
    long countByUser(User user);

    /**
     * Find journeys by status
     */
    List<Journey> findByUserAndStatus(User user, Journey.JourneyStatus status);

    /**
     * Count journeys by user id and status
     */
    long countByUserIdAndStatus(Long userId, Journey.JourneyStatus status);

    /**
     * Find journeys that need attention (no activity for X days)
     */
    @Query("SELECT j FROM Journey j WHERE j.user = :user AND j.status IN ('ACTIVE', 'STUDY_PLAN_IN_PROGRESS') AND j.lastActivityAt < :since")
    List<Journey> findInactiveJourneys(@Param("user") User user, @Param("since") Instant since);

    @Modifying
    @Query("UPDATE Journey j SET j.roadmapSessionId = null WHERE j.roadmapSessionId = :roadmapSessionId")
    int clearRoadmapSessionId(@Param("roadmapSessionId") Long roadmapSessionId);

    /**
     * Count a user's currently learning journeys.
     * Paused and terminal journeys do not consume concurrent learning slots.
     * Used to enforce the concurrent journey limit.
     */
    @Query("""
            SELECT COUNT(j) FROM Journey j
            WHERE j.user = :user
              AND j.status NOT IN (
                  com.exe.skillverse_backend.journey_service.entity.Journey.JourneyStatus.COMPLETED,
                  com.exe.skillverse_backend.journey_service.entity.Journey.JourneyStatus.PAUSED,
                  com.exe.skillverse_backend.journey_service.entity.Journey.JourneyStatus.CANCELLED,
                  com.exe.skillverse_backend.journey_service.entity.Journey.JourneyStatus.COMPLETED_UNVERIFIED,
                  com.exe.skillverse_backend.journey_service.entity.Journey.JourneyStatus.COMPLETED_VERIFIED,
                  com.exe.skillverse_backend.journey_service.entity.Journey.JourneyStatus.AWAITING_VERIFICATION
              )
            """)
    long countConcurrentLearningJourneys(@Param("user") User user);
}
