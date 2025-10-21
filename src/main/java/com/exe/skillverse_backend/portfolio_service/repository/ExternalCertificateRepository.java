package com.exe.skillverse_backend.portfolio_service.repository;

import com.exe.skillverse_backend.portfolio_service.entity.ExternalCertificate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExternalCertificateRepository extends JpaRepository<ExternalCertificate, Long> {
    
    List<ExternalCertificate> findByUserIdOrderByIssueDateDesc(Long userId);
    
    List<ExternalCertificate> findByUserIdAndCategoryOrderByIssueDateDesc(Long userId, ExternalCertificate.CertificateCategory category);
    
    long countByUserId(Long userId);
    
    long countByUserIdAndIsVerifiedTrue(Long userId);
}
