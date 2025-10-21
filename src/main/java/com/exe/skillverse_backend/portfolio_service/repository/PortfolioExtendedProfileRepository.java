package com.exe.skillverse_backend.portfolio_service.repository;

import com.exe.skillverse_backend.portfolio_service.entity.PortfolioExtendedProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PortfolioExtendedProfileRepository extends JpaRepository<PortfolioExtendedProfile, Long> {

    Optional<PortfolioExtendedProfile> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    Optional<PortfolioExtendedProfile> findByCustomUrlSlug(String customUrlSlug);

    boolean existsByCustomUrlSlug(String customUrlSlug);

    @Query("SELECT p FROM PortfolioExtendedProfile p WHERE p.isPublic = true ORDER BY p.portfolioViews DESC")
    List<PortfolioExtendedProfile> findTopPublicPortfoliosByViews(@Param("limit") int limit);

    @Query("SELECT p FROM PortfolioExtendedProfile p WHERE p.isPublic = true AND p.location LIKE %:location%")
    List<PortfolioExtendedProfile> findPublicPortfoliosByLocation(@Param("location") String location);

    @Query("SELECT p FROM PortfolioExtendedProfile p WHERE p.isPublic = true AND p.allowJobOffers = true")
    List<PortfolioExtendedProfile> findPortfoliosOpenToOffers();

    @Query("SELECT COUNT(p) FROM PortfolioExtendedProfile p WHERE p.userId = :userId")
    long countByUserId(@Param("userId") Long userId);
}
