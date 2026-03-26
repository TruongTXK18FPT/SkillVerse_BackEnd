package com.exe.skillverse_backend.mentor_booking_service.repository;

import com.exe.skillverse_backend.mentor_booking_service.entity.BookingDispute;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookingDisputeRepository extends JpaRepository<BookingDispute, Long> {

    Optional<BookingDispute> findByBooking_Id(Long bookingId);

    boolean existsByBooking_Id(Long bookingId);

    Page<BookingDispute> findByStatusIn(List<BookingDispute.DisputeStatus> statuses, Pageable pageable);

    List<BookingDispute> findByInitiatorIdOrRespondentId(Long initiatorId, Long respondentId);

    @Query("SELECT d FROM BookingDispute d WHERE d.status = :status")
    Page<BookingDispute> findByStatus(@Param("status") BookingDispute.DisputeStatus status, Pageable pageable);
}
