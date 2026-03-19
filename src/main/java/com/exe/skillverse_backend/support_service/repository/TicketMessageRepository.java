package com.exe.skillverse_backend.support_service.repository;

import com.exe.skillverse_backend.support_service.entity.TicketMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface TicketMessageRepository extends JpaRepository<TicketMessage, Long> {
    
    List<TicketMessage> findByTicketIdOrderByCreatedAtAsc(Long ticketId);
    
    List<TicketMessage> findByTicketTicketCodeOrderByCreatedAtAsc(String ticketCode);
    
    @Query("SELECT COUNT(m) FROM TicketMessage m WHERE m.ticket.id = :ticketId AND m.isRead = false AND m.senderType = :senderType")
    Long countUnreadMessages(@Param("ticketId") Long ticketId, @Param("senderType") TicketMessage.SenderType senderType);
    
    @Modifying
    @Query("UPDATE TicketMessage m SET m.isRead = true WHERE m.ticket.id = :ticketId AND m.senderType = :senderType")
    void markMessagesAsRead(@Param("ticketId") Long ticketId, @Param("senderType") TicketMessage.SenderType senderType);

    @Modifying
    @Transactional
    @Query("DELETE FROM TicketMessage m WHERE m.ticket.id = :ticketId")
    void deleteByTicketId(@Param("ticketId") Long ticketId);
}
