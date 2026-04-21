package com.exe.skillverse_backend.mentor_verification_service.repository;

import com.exe.skillverse_backend.mentor_verification_service.entity.MentorVerificationEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MentorVerificationEvidenceRepository extends JpaRepository<MentorVerificationEvidence, Long> {

    List<MentorVerificationEvidence> findByVerificationRequestId(Long requestId);
}
