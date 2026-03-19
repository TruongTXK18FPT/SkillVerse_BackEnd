package com.exe.skillverse_backend.seminar_service.repository;

import com.exe.skillverse_backend.seminar_service.entity.SeminarTicket;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SeminarTicketRepository extends JpaRepository<SeminarTicket, Long> {
    List<SeminarTicket> findByUserId(String userId);

    Page<SeminarTicket> findByUserId(String userId, Pageable pageable);

    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM SeminarTicket t WHERE t.userId = :userId AND t.seminar.id = :seminarId")
    boolean existsByUserIdAndSeminar_Id(@Param("userId") String userId, @Param("seminarId") Long seminarId);

    boolean existsBySeminar_Id(Long seminarId);

    @Query("SELECT t FROM SeminarTicket t WHERE t.userId = :userId AND t.seminar.id = :seminarId")
    Optional<SeminarTicket> findByUserIdAndSeminarId(@Param("userId") String userId,
            @Param("seminarId") Long seminarId);

    // Find all tickets for a specific seminar (for revenue report)
    @Query("SELECT t FROM SeminarTicket t JOIN FETCH t.seminar WHERE t.seminar.id = :seminarId ORDER BY t.purchasedAt DESC")
    List<SeminarTicket> findAllBySeminarId(@Param("seminarId") Long seminarId);

    // Count tickets sold for a seminar
    @Query("SELECT COUNT(t) FROM SeminarTicket t WHERE t.seminar.id = :seminarId")
    Long countBySeminarId(@Param("seminarId") Long seminarId);
}
