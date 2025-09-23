package com.exe.skillverse_backend.portfolio_service.repository;

import com.exe.skillverse_backend.portfolio_service.dto.request.PortfolioQueryDto;
import com.exe.skillverse_backend.portfolio_service.entity.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    // Find all projects by user ID
    List<Project> findByUserIdOrderByCreatedAtDesc(Long userId);

    // Find projects by user ID with pagination
    Page<Project> findByUserId(Long userId, Pageable pageable);

    // Find project by ID and user ID (for security)
    Optional<Project> findByIdAndUserId(Long id, Long userId);

    // Find projects by tech stack containing specific technology
    List<Project> findByUserIdAndTechStackContainingIgnoreCase(Long userId, String technology);

    // Find projects completed within a date range
    @Query("SELECT p FROM Project p WHERE p.userId = :userId AND p.completedDate BETWEEN :startDate AND :endDate ORDER BY p.completedDate DESC")
    List<Project> findByUserIdAndCompletedDateBetween(@Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    // Count projects by user
    long countByUserId(Long userId);

    // Find projects with media
    @Query("SELECT p FROM Project p LEFT JOIN FETCH p.media WHERE p.userId = :userId ORDER BY p.createdAt DESC")
    List<Project> findByUserIdWithMedia(@Param("userId") Long userId);

    // Flexible query methods
    @Query("SELECT p FROM Project p WHERE p.userId = :userId " +
            "AND (:search IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "     OR LOWER(p.description) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "     OR LOWER(p.techStack) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:techStack IS NULL OR LOWER(p.techStack) LIKE LOWER(CONCAT('%', :techStack, '%'))) " +
            "AND (:startDate IS NULL OR p.completedDate >= :startDate) " +
            "AND (:endDate IS NULL OR p.completedDate <= :endDate) " +
            "ORDER BY " +
            "CASE WHEN :sortBy = 'title' AND :sortDirection = 'ASC' THEN p.title END ASC, " +
            "CASE WHEN :sortBy = 'title' AND :sortDirection = 'DESC' THEN p.title END DESC, " +
            "CASE WHEN :sortBy = 'completedDate' AND :sortDirection = 'ASC' THEN p.completedDate END ASC, " +
            "CASE WHEN :sortBy = 'completedDate' AND :sortDirection = 'DESC' THEN p.completedDate END DESC, " +
            "CASE WHEN (:sortBy = 'createdAt' OR :sortBy IS NULL) AND :sortDirection = 'ASC' THEN p.createdAt END ASC, "
            +
            "CASE WHEN (:sortBy = 'createdAt' OR :sortBy IS NULL) AND (:sortDirection = 'DESC' OR :sortDirection IS NULL) THEN p.createdAt END DESC")
    List<Project> findProjectsByQuery(@Param("userId") Long userId,
            @Param("search") String search,
            @Param("techStack") String techStack,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("sortBy") String sortBy,
            @Param("sortDirection") String sortDirection);

    @Query("SELECT p FROM Project p WHERE p.userId = :userId " +
            "AND (:search IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "     OR LOWER(p.description) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "     OR LOWER(p.techStack) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:techStack IS NULL OR LOWER(p.techStack) LIKE LOWER(CONCAT('%', :techStack, '%'))) " +
            "AND (:startDate IS NULL OR p.completedDate >= :startDate) " +
            "AND (:endDate IS NULL OR p.completedDate <= :endDate)")
    Page<Project> findProjectsByQuery(@Param("userId") Long userId,
            @Param("search") String search,
            @Param("techStack") String techStack,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable);

    @Query("SELECT COUNT(p) FROM Project p WHERE p.userId = :userId " +
            "AND (:search IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "     OR LOWER(p.description) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "     OR LOWER(p.techStack) LIKE LOWER(CONCAT('%', :techStack, '%'))) " +
            "AND (:techStack IS NULL OR LOWER(p.techStack) LIKE LOWER(CONCAT('%', :techStack, '%'))) " +
            "AND (:startDate IS NULL OR p.completedDate >= :startDate) " +
            "AND (:endDate IS NULL OR p.completedDate <= :endDate)")
    long countProjectsByQuery(@Param("userId") Long userId,
            @Param("search") String search,
            @Param("techStack") String techStack,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    // Convenience methods that accept PortfolioQueryDto
    default List<Project> findProjectsByQuery(Long userId, PortfolioQueryDto queryDto) {
        String techStack = queryDto.getTechnologies() != null && !queryDto.getTechnologies().isEmpty()
                ? String.join(",", queryDto.getTechnologies())
                : null;
        return findProjectsByQuery(userId,
                queryDto.getSearch(),
                techStack,
                queryDto.getStartDate(),
                queryDto.getEndDate(),
                queryDto.getSortBy(),
                queryDto.getSortDirection());
    }

    default Page<Project> findProjectsByQuery(Long userId, PortfolioQueryDto queryDto, Pageable pageable) {
        String techStack = queryDto.getTechnologies() != null && !queryDto.getTechnologies().isEmpty()
                ? String.join(",", queryDto.getTechnologies())
                : null;
        return findProjectsByQuery(userId,
                queryDto.getSearch(),
                techStack,
                queryDto.getStartDate(),
                queryDto.getEndDate(),
                pageable);
    }

    default long countProjectsByQuery(Long userId, PortfolioQueryDto queryDto) {
        String techStack = queryDto.getTechnologies() != null && !queryDto.getTechnologies().isEmpty()
                ? String.join(",", queryDto.getTechnologies())
                : null;
        return countProjectsByQuery(userId,
                queryDto.getSearch(),
                techStack,
                queryDto.getStartDate(),
                queryDto.getEndDate());
    }
}