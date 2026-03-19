package com.exe.skillverse_backend.seminar_service.repository;

import com.exe.skillverse_backend.seminar_service.entity.Seminar;
import com.exe.skillverse_backend.seminar_service.entity.SeminarStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SeminarRepository extends JpaRepository<Seminar, Long> {
        Page<Seminar> findByStatus(SeminarStatus status, Pageable pageable);

        Page<Seminar> findByStatusIn(List<SeminarStatus> statuses, Pageable pageable);

        Page<Seminar> findByCreatorId(String creatorId, Pageable pageable);

        List<Seminar> findByStatus(SeminarStatus status);

        @Modifying
        @Query("UPDATE Seminar s SET s.status = :status WHERE s.endTime < :now AND s.status IN ('OPEN', 'ACCEPTED')")
        void updateStatusForExpiredSeminars(@Param("status") SeminarStatus status, @Param("now") LocalDateTime now);

        @Modifying
        @Query("UPDATE Seminar s SET s.status = :newStatus WHERE s.startTime <= :now AND s.endTime > :now AND s.status = :currentStatus")
        void updateStatusForStartedSeminars(
                        @Param("newStatus") SeminarStatus newStatus,
                        @Param("currentStatus") SeminarStatus currentStatus,
                        @Param("now") LocalDateTime now);

        // === Pessimistic Locking Methods for Ticket Purchase ===

        /**
         * Find seminar by ID with pessimistic write lock.
         * Use this when purchasing tickets to prevent race conditions.
         */
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT s FROM Seminar s WHERE s.id = :id")
        Optional<Seminar> findByIdWithLock(@Param("id") Long id);

        /**
         * Atomic increment of tickets_sold with capacity check.
         * Returns number of rows updated (1 if success, 0 if capacity exceeded).
         * Uses database-level atomic operation to prevent race conditions.
         */
        @Modifying
        @Query("""
                        UPDATE Seminar s
                        SET s.ticketsSold = s.ticketsSold + 1, s.version = s.version + 1
                        WHERE s.id = :id
                          AND (s.maxCapacity IS NULL OR s.ticketsSold < s.maxCapacity)
                        """)
        int incrementTicketsSoldIfAvailable(@Param("id") Long id);

        /**
         * Decrement tickets_sold (for refunds or rollbacks).
         */
        @Modifying
        @Query("""
                        UPDATE Seminar s
                        SET s.ticketsSold = CASE WHEN s.ticketsSold > 0 THEN s.ticketsSold - 1 ELSE 0 END,
                            s.version = s.version + 1
                        WHERE s.id = :id
                        """)
        int decrementTicketsSold(@Param("id") Long id);

        /**
         * Check if seminar has available capacity.
         */
        @Query("""
                        SELECT CASE
                            WHEN s.maxCapacity IS NULL THEN true
                            WHEN s.ticketsSold < s.maxCapacity THEN true
                            ELSE false
                        END
                        FROM Seminar s WHERE s.id = :id
                        """)
        boolean hasAvailableCapacity(@Param("id") Long id);

        // === Analytics Queries ===

        /**
         * Count seminars by status list.
         * Used for analytics: total, active, completed counts.
         */
        @Query("SELECT COUNT(s) FROM Seminar s WHERE s.status IN :statuses")
        Long countByStatusIn(@Param("statuses") List<SeminarStatus> statuses);

        /**
         * Get top speakers by total tickets sold across all their seminars.
         * Returns [creatorId, ticketCount] ordered by ticket count DESC.
         * Only includes seminars with status ACCEPTED, OPEN, or CLOSED.
         * Use Pageable to limit results (e.g., top 4).
         */
        @Query(value = """
                        SELECT s.creator_id, COUNT(st.id) as ticket_count
                        FROM seminars s
                        LEFT JOIN seminar_tickets st ON st.seminar_id = s.id
                        WHERE s.status IN ('ACCEPTED', 'OPEN', 'CLOSED')
                        GROUP BY s.creator_id
                        ORDER BY ticket_count DESC
                        """, nativeQuery = true)
        List<Object[]> findTopSpeakersByTicketsSold(Pageable pageable);
}
