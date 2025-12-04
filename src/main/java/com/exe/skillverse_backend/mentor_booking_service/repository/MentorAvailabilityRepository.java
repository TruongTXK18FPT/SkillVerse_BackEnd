package com.exe.skillverse_backend.mentor_booking_service.repository;

import com.exe.skillverse_backend.mentor_booking_service.entity.MentorAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MentorAvailabilityRepository extends JpaRepository<MentorAvailability, Long> {
    
    @Query("SELECT m FROM MentorAvailability m WHERE m.mentorId = :mentorId AND " +
           "((m.startTime BETWEEN :from AND :to) OR (m.endTime BETWEEN :from AND :to) OR " +
           "(m.startTime <= :from AND m.endTime >= :to))")
    List<MentorAvailability> findByMentorIdAndDateRange(@Param("mentorId") Long mentorId, 
                                                        @Param("from") LocalDateTime from, 
                                                        @Param("to") LocalDateTime to);

    List<MentorAvailability> findByMentorId(Long mentorId);
}
