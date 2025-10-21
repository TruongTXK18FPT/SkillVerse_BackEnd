package com.exe.skillverse_backend.portfolio_service.repository;

import com.exe.skillverse_backend.portfolio_service.entity.GeneratedCV;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GeneratedCVRepository extends JpaRepository<GeneratedCV, Long> {
    
    List<GeneratedCV> findByUserIdOrderByVersionDesc(Long userId);
    
    Optional<GeneratedCV> findByUserIdAndIsActiveTrue(Long userId);
    
    long countByUserId(Long userId);
}
