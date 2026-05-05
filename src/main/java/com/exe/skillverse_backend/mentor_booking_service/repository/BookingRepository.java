package com.exe.skillverse_backend.mentor_booking_service.repository;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.mentor_booking_service.entity.Booking;
import com.exe.skillverse_backend.mentor_booking_service.entity.BookingStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    Page<Booking> findByLearnerAndStatusInOrderByStartTimeDesc(User learner, List<BookingStatus> statuses, Pageable pageable);
    Page<Booking> findByMentorAndStatusInOrderByStartTimeDesc(User mentor, List<BookingStatus> statuses, Pageable pageable);
    Page<Booking> findByStatusOrderByStartTimeDesc(BookingStatus status, Pageable pageable);
    Page<Booking> findByStartTimeBetweenOrderByStartTimeDesc(LocalDateTime from, LocalDateTime to, Pageable pageable);
    Page<Booking> findByStatusAndStartTimeBetweenOrderByStartTimeDesc(BookingStatus status, LocalDateTime from, LocalDateTime to, Pageable pageable);
    boolean existsByMentorAndStatusInAndStartTimeLessThanEqualAndEndTimeGreaterThanEqual(User mentor, List<BookingStatus> statuses, LocalDateTime start, LocalDateTime end);
    boolean existsByLearnerAndStatusInAndStartTimeLessThanEqualAndEndTimeGreaterThanEqual(User learner, List<BookingStatus> statuses, LocalDateTime start, LocalDateTime end);
    List<Booking> findByStatusAndCreatedAtBefore(BookingStatus status, LocalDateTime before);
    List<Booking> findByStatusAndStartTimeLessThanEqual(BookingStatus status, LocalDateTime time);
    Booking findTopByMentorAndLearnerOrderByStartTimeDesc(User mentor, User learner);
    Booking findTopByMentorAndLearnerAndStatusNotInOrderByStartTimeDesc(User mentor, User learner, Collection<BookingStatus> statuses);
    long countByMentorAndStatus(User mentor, BookingStatus status);
    boolean existsByPaymentReference(String paymentReference);
    Optional<Booking> findByPaymentReference(String paymentReference);
    List<Booking> findByStatusAndMentorCompletedAtBefore(BookingStatus status, LocalDateTime deadline);
    List<Booking> findByStatusAndCompletionDeadlineBefore(BookingStatus status, LocalDateTime deadline);
    List<Booking> findByMentorAndStatusInAndStartTimeBetween(User mentor, List<BookingStatus> statuses, LocalDateTime from, LocalDateTime to);

    @Query("""
            select b from Booking b
            where (b.mentor.id = :userId or b.learner.id = :userId)
              and b.status in :statuses
              and (
                  b.status = com.exe.skillverse_backend.mentor_booking_service.entity.BookingStatus.MENTORING_ACTIVE
                  or b.endTime > :now
              )
            order by b.startTime desc
            """)
    List<Booking> findChatEligibleBookings(
            @Param("userId") Long userId,
            @Param("statuses") Collection<BookingStatus> statuses,
            @Param("now") LocalDateTime now);

    @Query("""
            select b from Booking b
            where b.id = :bookingId
              and (b.mentor.id = :userId or b.learner.id = :userId)
            """)
    Optional<Booking> findAccessibleBooking(@Param("bookingId") Long bookingId, @Param("userId") Long userId);

    // ─── V3 Phase 1: node mentoring authorization ──────────────────────────────
    // A mentor is authorized to review/verify a given (journey, node) only if they
    // have at least one booking in one of the active statuses passed in.
    @Query("""
            select case when count(b) > 0 then true else false end from Booking b
            where b.mentor.id = :mentorId
              and b.journeyId = :journeyId
              and b.nodeId = :nodeId
              and b.status in :statuses
            """)
    boolean existsActiveNodeBookingForMentor(
            @Param("mentorId") Long mentorId,
            @Param("journeyId") Long journeyId,
            @Param("nodeId") String nodeId,
            @Param("statuses") Collection<BookingStatus> statuses);

    // Journey-level variant: used by FinalVerificationGateService to authorise a
    // mentor submitting a journey completion report / assessing the final output.
    @Query("""
            select case when count(b) > 0 then true else false end from Booking b
            where b.mentor.id = :mentorId
              and b.journeyId = :journeyId
              and b.status in :statuses
            """)
    boolean existsActiveJourneyBookingForMentor(
            @Param("mentorId") Long mentorId,
            @Param("journeyId") Long journeyId,
            @Param("statuses") Collection<BookingStatus> statuses);

    // Learner-side check: does this journey have ANY active mentor booking?
    // Used to guard output assessment submission — only allowed when learner has booked a mentor.
    @Query("""
            select case when count(b) > 0 then true else false end from Booking b
            where b.journeyId = :journeyId
              and b.status in :statuses
            """)
    boolean existsActiveJourneyBookingForAnyMentor(
            @Param("journeyId") Long journeyId,
            @Param("statuses") Collection<BookingStatus> statuses);

    // V3 Phase 2: find the active ROADMAP_MENTORING booking for a journey
    @Query("""
            select b from Booking b
            where b.journeyId = :journeyId
              and b.bookingType = 'ROADMAP_MENTORING'
              and b.status = com.exe.skillverse_backend.mentor_booking_service.entity.BookingStatus.MENTORING_ACTIVE
            order by b.createdAt desc
            """)
    Optional<Booking> findActiveRoadmapMentoringBooking(@Param("journeyId") Long journeyId);

    @Query("""
            select case when count(b) > 0 then true else false end from Booking b
            where b.journeyId = :journeyId
              and b.bookingType = 'ROADMAP_MENTORING'
              and b.status in :statuses
            """)
    boolean existsRoadmapMentoringBookingForJourney(
            @Param("journeyId") Long journeyId,
            @Param("statuses") Collection<BookingStatus> statuses);

    @Query("""
            select b from Booking b
            where b.mentor.id = :mentorId
              and b.bookingType = 'ROADMAP_MENTORING'
              and b.status in :statuses
            order by b.updatedAt desc, b.createdAt desc
            """)
    List<Booking> findRoadmapMentoringBookingsForMentor(
            @Param("mentorId") Long mentorId,
            @Param("statuses") Collection<BookingStatus> statuses);

    /**
     * Check if a journey has ANY non-terminal booking.
     * Non-terminal = PENDING, CONFIRMED, ONGOING, MENTORING_ACTIVE, PENDING_COMPLETION, DISPUTED.
     * Used to prevent deletion of a journey that has active mentor engagements.
     */
    @Query("""
            select case when count(b) > 0 then true else false end from Booking b
            where b.journeyId = :journeyId
              and b.status in (
                  com.exe.skillverse_backend.mentor_booking_service.entity.BookingStatus.PENDING,
                  com.exe.skillverse_backend.mentor_booking_service.entity.BookingStatus.CONFIRMED,
                  com.exe.skillverse_backend.mentor_booking_service.entity.BookingStatus.ONGOING,
                  com.exe.skillverse_backend.mentor_booking_service.entity.BookingStatus.MENTORING_ACTIVE,
                  com.exe.skillverse_backend.mentor_booking_service.entity.BookingStatus.PENDING_COMPLETION,
                  com.exe.skillverse_backend.mentor_booking_service.entity.BookingStatus.DISPUTED
              )
            """)
    boolean hasActiveBookingsForJourney(@Param("journeyId") Long journeyId);

    // Learner-side node coverage check: true if any active booking covers this node.
    // Matches NODE_MENTORING (nodeId = :nodeId) AND ROADMAP_MENTORING (nodeId IS NULL, covers all nodes).
    @Query("""
            select case when count(b) > 0 then true else false end from Booking b
            where b.journeyId = :journeyId
              and (b.nodeId = :nodeId or b.nodeId is null)
              and b.status in :statuses
            """)
    boolean existsActiveBookingCoveringNode(
            @Param("journeyId") Long journeyId,
            @Param("nodeId") String nodeId,
            @Param("statuses") Collection<BookingStatus> statuses);
}
