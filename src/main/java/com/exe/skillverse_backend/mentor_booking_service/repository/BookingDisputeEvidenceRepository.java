package com.exe.skillverse_backend.mentor_booking_service.repository;

import com.exe.skillverse_backend.mentor_booking_service.entity.BookingDisputeEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingDisputeEvidenceRepository extends JpaRepository<BookingDisputeEvidence, Long> {

    List<BookingDisputeEvidence> findByDispute_IdOrderByCreatedAtAsc(Long disputeId);
}
