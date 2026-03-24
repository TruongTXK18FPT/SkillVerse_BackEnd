package com.exe.skillverse_backend.business_service.repository;

import com.exe.skillverse_backend.business_service.entity.TrustScore;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TrustScoreRepository extends JpaRepository<TrustScore, Long> {
    Optional<TrustScore> findByUserId(Long userId);
    boolean existsByUserId(Long userId);
    void deleteByUserId(Long userId);

    @Query("SELECT AVG(t.totalScore) FROM TrustScore t")
    Double getAverageScore();

    @Query("SELECT COUNT(t) FROM TrustScore t WHERE t.trustTier = :tier")
    long countByTrustTier(@Param("tier") TrustScore.TrustTier tier);
}
