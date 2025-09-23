package com.exe.skillverse_backend.portfolio_service.service;

import com.exe.skillverse_backend.portfolio_service.dto.request.PortfolioQueryDto;
import com.exe.skillverse_backend.portfolio_service.entity.Project;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for building complex Project queries using Criteria API
 * This replaces long @Query annotations in ProjectRepository with programmatic
 * query building
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectQueryBuilderService {

    private final EntityManager entityManager;

    /**
     * Build and execute a flexible project search query
     * 
     * @param userId   User ID to filter by
     * @param queryDto Query parameters
     * @return List of matching projects
     */
    public List<Project> findProjectsByQuery(Long userId, PortfolioQueryDto queryDto) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Project> query = cb.createQuery(Project.class);
        Root<Project> project = query.from(Project.class);

        // Build WHERE clause
        List<Predicate> predicates = buildPredicates(cb, project, userId, queryDto);
        query.where(cb.and(predicates.toArray(new Predicate[0])));

        // Build ORDER BY clause
        Order orderBy = buildOrderBy(cb, project, queryDto.getSortBy(), queryDto.getSortDirection());
        query.orderBy(orderBy);

        TypedQuery<Project> typedQuery = entityManager.createQuery(query);

        log.debug("Executing project search query for userId: {} with criteria: {}", userId, queryDto);
        return typedQuery.getResultList();
    }

    /**
     * Build and execute a paginated project search query
     * 
     * @param userId   User ID to filter by
     * @param queryDto Query parameters
     * @param pageable Pagination parameters
     * @return Page of matching projects
     */
    public Page<Project> findProjectsByQuery(Long userId, PortfolioQueryDto queryDto, Pageable pageable) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Project> query = cb.createQuery(Project.class);
        Root<Project> project = query.from(Project.class);

        // Build WHERE clause
        List<Predicate> predicates = buildPredicates(cb, project, userId, queryDto);
        query.where(cb.and(predicates.toArray(new Predicate[0])));

        // Build ORDER BY clause
        Order orderBy = buildOrderBy(cb, project, queryDto.getSortBy(), queryDto.getSortDirection());
        query.orderBy(orderBy);

        TypedQuery<Project> typedQuery = entityManager.createQuery(query);

        // Apply pagination
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());

        List<Project> results = typedQuery.getResultList();
        long total = countProjectsByQuery(userId, queryDto);

        log.debug("Executing paginated project search query for userId: {} with criteria: {}, page: {}, size: {}",
                userId, queryDto, pageable.getPageNumber(), pageable.getPageSize());

        return new PageImpl<>(results, pageable, total);
    }

    /**
     * Count projects matching the query criteria
     * 
     * @param userId   User ID to filter by
     * @param queryDto Query parameters
     * @return Count of matching projects
     */
    public long countProjectsByQuery(Long userId, PortfolioQueryDto queryDto) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<Project> project = query.from(Project.class);

        // Build WHERE clause
        List<Predicate> predicates = buildPredicates(cb, project, userId, queryDto);
        query.where(cb.and(predicates.toArray(new Predicate[0])));

        query.select(cb.count(project));

        TypedQuery<Long> typedQuery = entityManager.createQuery(query);
        return typedQuery.getSingleResult();
    }

    /**
     * Build WHERE clause predicates for project queries
     * 
     * @param cb       CriteriaBuilder
     * @param project  Root entity
     * @param userId   User ID to filter by
     * @param queryDto Query parameters
     * @return List of predicates
     */
    private List<Predicate> buildPredicates(CriteriaBuilder cb, Root<Project> project, Long userId,
            PortfolioQueryDto queryDto) {
        List<Predicate> predicates = new ArrayList<>();

        // Always filter by user ID
        predicates.add(cb.equal(project.get("userId"), userId));

        // Search in title, description, or tech stack
        if (queryDto.getSearch() != null && !queryDto.getSearch().trim().isEmpty()) {
            String searchPattern = "%" + queryDto.getSearch().toLowerCase() + "%";
            Predicate titleSearch = cb.like(cb.lower(project.get("title")), searchPattern);
            Predicate descriptionSearch = cb.like(cb.lower(project.get("description")), searchPattern);
            Predicate techStackSearch = cb.like(cb.lower(project.get("techStack")), searchPattern);

            predicates.add(cb.or(titleSearch, descriptionSearch, techStackSearch));
        }

        // Filter by technologies (tech stack)
        if (queryDto.getTechnologies() != null && !queryDto.getTechnologies().isEmpty()) {
            List<Predicate> techPredicates = new ArrayList<>();
            for (String tech : queryDto.getTechnologies()) {
                if (tech != null && !tech.trim().isEmpty()) {
                    String techPattern = "%" + tech.toLowerCase() + "%";
                    techPredicates.add(cb.like(cb.lower(project.get("techStack")), techPattern));
                }
            }
            if (!techPredicates.isEmpty()) {
                predicates.add(cb.or(techPredicates.toArray(new Predicate[0])));
            }
        }

        // Filter by date range (completedDate)
        if (queryDto.getStartDate() != null) {
            predicates.add(cb.greaterThanOrEqualTo(project.get("completedDate"), queryDto.getStartDate()));
        }
        if (queryDto.getEndDate() != null) {
            predicates.add(cb.lessThanOrEqualTo(project.get("completedDate"), queryDto.getEndDate()));
        }

        return predicates;
    }

    /**
     * Build ORDER BY clause based on sort parameters
     * 
     * @param cb            CriteriaBuilder
     * @param project       Root entity
     * @param sortBy        Sort field
     * @param sortDirection Sort direction
     * @return Order clause
     */
    private Order buildOrderBy(CriteriaBuilder cb, Root<Project> project, String sortBy, String sortDirection) {
        boolean isAscending = "ASC".equalsIgnoreCase(sortDirection);

        Expression<?> sortExpression;
        switch (sortBy != null ? sortBy.toLowerCase() : "createdat") {
            case "title":
                sortExpression = project.get("title");
                break;
            case "completeddate":
                sortExpression = project.get("completedDate");
                break;
            case "createdat":
            default:
                sortExpression = project.get("createdAt");
                break;
        }

        return isAscending ? cb.asc(sortExpression) : cb.desc(sortExpression);
    }
}