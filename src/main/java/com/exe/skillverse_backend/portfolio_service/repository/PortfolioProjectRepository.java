package com.exe.skillverse_backend.portfolio_service.repository;

import com.exe.skillverse_backend.portfolio_service.entity.PortfolioProject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PortfolioProjectRepository extends JpaRepository<PortfolioProject, Long> {
    
    List<PortfolioProject> findByUserIdOrderByCompletionDateDesc(Long userId);
    
    List<PortfolioProject> findByUserIdAndIsFeaturedTrueOrderByCompletionDateDesc(Long userId);
    
    @Query("SELECT p FROM PortfolioProject p WHERE p.user.id = :userId AND p.projectType = :type ORDER BY p.completionDate DESC")
    List<PortfolioProject> findByUserIdAndProjectType(Long userId, PortfolioProject.ProjectType type);
    
    long countByUserId(Long userId);
}
