package com.exe.skillverse_backend.mentor_booking_service.repository;

import com.exe.skillverse_backend.mentor_booking_service.entity.BookingDisputeResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingDisputeResponseRepository extends JpaRepository<BookingDisputeResponse, Long> {

    List<BookingDisputeResponse> findByEvidence_IdOrderByCreatedAtAsc(Long evidenceId);
}
