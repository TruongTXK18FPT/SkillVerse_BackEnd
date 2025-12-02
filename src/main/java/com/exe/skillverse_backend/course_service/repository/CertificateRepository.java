package com.exe.skillverse_backend.course_service.repository;

import com.exe.skillverse_backend.course_service.entity.Certificate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface CertificateRepository extends JpaRepository<Certificate, Long> {

    /**
     * Find certificate by serial number
     */
    @Transactional(readOnly = true)
    Optional<Certificate> findBySerial(String serial);

    /**
     * Find certificates by user ID
     */
    @Transactional(readOnly = true)
    @Query("SELECT c FROM Certificate c WHERE c.user.id = :userId")
    List<Certificate> findByUserId(@Param("userId") Long userId);

    /**
     * Check if certificate exists for user and course
     */
    @Transactional(readOnly = true)
    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END " +
            "FROM Certificate c WHERE c.course.id = :courseId AND c.user.id = :userId")
    boolean existsByCourseIdAndUserId(@Param("courseId") Long courseId, @Param("userId") Long userId);

    /**
     * Find certificates by course ID
     */
    @Transactional(readOnly = true)
    @Query("SELECT c FROM Certificate c WHERE c.course.id = :courseId")
    List<Certificate> findByCourseId(@Param("courseId") Long courseId);

    /**
     * Find active (non-revoked) certificates by user
     */
    @Transactional(readOnly = true)
    @Query("SELECT c FROM Certificate c WHERE c.user.id = :userId AND c.revokedAt IS NULL")
    List<Certificate> findActiveByUserId(@Param("userId") Long userId);

    /**
     * Find certificate by user and course
     */
    @Transactional(readOnly = true)
    @Query("SELECT c FROM Certificate c WHERE c.user.id = :userId AND c.course.id = :courseId")
    Optional<Certificate> findByUserIdAndCourseId(@Param("userId") Long userId, @Param("courseId") Long courseId);

    /**
     * Count certificates issued for a course
     */
    @Transactional(readOnly = true)
    @Query("SELECT COUNT(c) FROM Certificate c WHERE c.course.id = :courseId")
    long countByCourseId(@Param("courseId") Long courseId);

    /**
     * Find revoked certificates
     */
    @Transactional(readOnly = true)
    @Query("SELECT c FROM Certificate c WHERE c.revokedAt IS NOT NULL")
    List<Certificate> findRevokedCertificates();

    @Transactional(readOnly = true)
    @Query("SELECT COUNT(c) FROM Certificate c WHERE c.user.id = :userId")
    long countByUserId(@Param("userId") Long userId);
}
