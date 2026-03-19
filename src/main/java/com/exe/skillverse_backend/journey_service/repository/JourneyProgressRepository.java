package com.exe.skillverse_backend.journey_service.repository;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.journey_service.entity.Journey;
import com.exe.skillverse_backend.journey_service.entity.JourneyProgress;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface JourneyProgressRepository extends JpaRepository<JourneyProgress, Long> {

    /**
     * Find all progress records for a journey
     */
    List<JourneyProgress> findByJourney(Journey journey);

    /**
     * Find all progress records for a user
     */
    List<JourneyProgress> findByUser(User user);

    /**
     * Find progress by journey and milestone
     */
    Optional<JourneyProgress> findByJourneyAndMilestone(Journey journey, JourneyProgress.Milestone milestone);

    /**
     * Find completed milestones for a journey
     */
    @Query("SELECT jp FROM JourneyProgress jp WHERE jp.journey = :journey AND jp.isCompleted = true")
    List<JourneyProgress> findCompletedMilestones(@Param("journey") Journey journey);

    /**
     * Check if a milestone is completed for a journey
     */
    @Query("SELECT CASE WHEN COUNT(jp) > 0 THEN true ELSE false END FROM JourneyProgress jp WHERE jp.journey = :journey AND jp.milestone = :milestone AND jp.isCompleted = true")
    boolean isMilestoneCompleted(@Param("journey") Journey journey, @Param("milestone") JourneyProgress.Milestone milestone);

    /**
     * Find latest progress for a journey
     */
    Optional<JourneyProgress> findTopByJourneyOrderByCreatedAtDesc(Journey journey);
}
