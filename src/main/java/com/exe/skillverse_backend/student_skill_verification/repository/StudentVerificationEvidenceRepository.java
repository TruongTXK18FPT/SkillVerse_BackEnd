package com.exe.skillverse_backend.student_skill_verification.repository;

import com.exe.skillverse_backend.student_skill_verification.entity.StudentVerificationEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentVerificationEvidenceRepository extends JpaRepository<StudentVerificationEvidence, Long> {
}
