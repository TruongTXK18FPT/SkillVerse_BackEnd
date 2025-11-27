package com.exe.skillverse_backend.support_service.repository;

import com.exe.skillverse_backend.support_service.entity.SupportTicket;
import com.exe.skillverse_backend.support_service.entity.SupportTicket.TicketStatus;
import com.exe.skillverse_backend.support_service.entity.SupportTicket.TicketCategory;
import com.exe.skillverse_backend.support_service.entity.SupportTicket.TicketPriority;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for SupportTicket entity
 */
@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

    Optional<SupportTicket> findByTicketCode(String ticketCode);

    List<SupportTicket> findByUserIdOrderByCreatedAtDesc(Long userId);

    Page<SupportTicket> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<SupportTicket> findByEmailOrderByCreatedAtDesc(String email);

    Page<SupportTicket> findByStatus(TicketStatus status, Pageable pageable);

    Page<SupportTicket> findByCategory(TicketCategory category, Pageable pageable);

    Page<SupportTicket> findByPriority(TicketPriority priority, Pageable pageable);

    Page<SupportTicket> findByAssignedToId(Long adminId, Pageable pageable);

    @Query("SELECT t FROM SupportTicket t WHERE t.status IN :statuses ORDER BY "
            + "CASE t.priority WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 ELSE 3 END, "
            + "t.createdAt ASC")
    Page<SupportTicket> findOpenTicketsByPriority(
            @Param("statuses") List<TicketStatus> statuses,
            Pageable pageable);

    @Query("SELECT COUNT(t) FROM SupportTicket t WHERE t.status = :status")
    long countByStatus(@Param("status") TicketStatus status);

    @Query("SELECT t.category, COUNT(t) FROM SupportTicket t GROUP BY t.category")
    List<Object[]> countByCategory();

    @Query("SELECT t FROM SupportTicket t WHERE "
            + "(:status IS NULL OR t.status = :status) AND "
            + "(:category IS NULL OR t.category = :category) AND "
            + "(:priority IS NULL OR t.priority = :priority) "
            + "ORDER BY t.createdAt DESC")
    Page<SupportTicket> findWithFilters(
            @Param("status") TicketStatus status,
            @Param("category") TicketCategory category,
            @Param("priority") TicketPriority priority,
            Pageable pageable);
}
