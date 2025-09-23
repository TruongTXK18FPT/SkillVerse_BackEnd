package com.exe.skillverse_backend.portfolio_service.service;

import com.exe.skillverse_backend.portfolio_service.dto.request.PortfolioQueryDto;
import com.exe.skillverse_backend.portfolio_service.entity.Product;
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
 * Service for building complex Product queries using Criteria API
 * This replaces long @Query annotations in ProductRepository with programmatic
 * query building
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductQueryBuilderService {

    private final EntityManager entityManager;

    /**
     * Build and execute a flexible product search query
     * 
     * @param userId   User ID to filter by
     * @param queryDto Query parameters
     * @return List of matching products
     */
    public List<Product> findProductsByQuery(Long userId, PortfolioQueryDto queryDto) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Product> query = cb.createQuery(Product.class);
        Root<Product> product = query.from(Product.class);

        // Build WHERE clause
        List<Predicate> predicates = buildPredicates(cb, product, userId, queryDto);
        query.where(cb.and(predicates.toArray(new Predicate[0])));

        // Build ORDER BY clause
        Order orderBy = buildOrderBy(cb, product, queryDto.getSortBy(), queryDto.getSortDirection());
        query.orderBy(orderBy);

        TypedQuery<Product> typedQuery = entityManager.createQuery(query);

        log.debug("Executing product search query for userId: {} with criteria: {}", userId, queryDto);
        return typedQuery.getResultList();
    }

    /**
     * Build and execute a paginated product search query
     * 
     * @param userId   User ID to filter by
     * @param queryDto Query parameters
     * @param pageable Pagination parameters
     * @return Page of matching products
     */
    public Page<Product> findProductsByQuery(Long userId, PortfolioQueryDto queryDto, Pageable pageable) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Product> query = cb.createQuery(Product.class);
        Root<Product> product = query.from(Product.class);

        // Build WHERE clause
        List<Predicate> predicates = buildPredicates(cb, product, userId, queryDto);
        query.where(cb.and(predicates.toArray(new Predicate[0])));

        // Build ORDER BY clause
        Order orderBy = buildOrderBy(cb, product, queryDto.getSortBy(), queryDto.getSortDirection());
        query.orderBy(orderBy);

        TypedQuery<Product> typedQuery = entityManager.createQuery(query);

        // Apply pagination
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());

        List<Product> results = typedQuery.getResultList();
        long total = countProductsByQuery(userId, queryDto);

        log.debug("Executing paginated product search query for userId: {} with criteria: {}, page: {}, size: {}",
                userId, queryDto, pageable.getPageNumber(), pageable.getPageSize());

        return new PageImpl<>(results, pageable, total);
    }

    /**
     * Count products matching the query criteria
     * 
     * @param userId   User ID to filter by
     * @param queryDto Query parameters
     * @return Count of matching products
     */
    public long countProductsByQuery(Long userId, PortfolioQueryDto queryDto) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<Product> product = query.from(Product.class);

        // Build WHERE clause
        List<Predicate> predicates = buildPredicates(cb, product, userId, queryDto);
        query.where(cb.and(predicates.toArray(new Predicate[0])));

        query.select(cb.count(product));

        TypedQuery<Long> typedQuery = entityManager.createQuery(query);
        return typedQuery.getSingleResult();
    }

    /**
     * Build WHERE clause predicates for product queries
     * 
     * @param cb       CriteriaBuilder
     * @param product  Root entity
     * @param userId   User ID to filter by
     * @param queryDto Query parameters
     * @return List of predicates
     */
    private List<Predicate> buildPredicates(CriteriaBuilder cb, Root<Product> product, Long userId,
            PortfolioQueryDto queryDto) {
        List<Predicate> predicates = new ArrayList<>();

        // Always filter by user ID
        predicates.add(cb.equal(product.get("userId"), userId));

        // Search in name, description, or category
        if (queryDto.getSearch() != null && !queryDto.getSearch().trim().isEmpty()) {
            String searchPattern = "%" + queryDto.getSearch().toLowerCase() + "%";
            Predicate nameSearch = cb.like(cb.lower(product.get("name")), searchPattern);
            Predicate descriptionSearch = cb.like(cb.lower(product.get("description")), searchPattern);
            Predicate categorySearch = cb.like(cb.lower(product.get("category")), searchPattern);

            predicates.add(cb.or(nameSearch, descriptionSearch, categorySearch));
        }

        // Filter by category
        if (queryDto.getCategories() != null && !queryDto.getCategories().isEmpty()) {
            // Handle multiple categories (OR condition)
            List<Predicate> categoryPredicates = new ArrayList<>();
            for (String category : queryDto.getCategories()) {
                if (category != null && !category.trim().isEmpty()) {
                    String categoryPattern = "%" + category.toLowerCase() + "%";
                    categoryPredicates.add(cb.like(cb.lower(product.get("category")), categoryPattern));
                }
            }
            if (!categoryPredicates.isEmpty()) {
                predicates.add(cb.or(categoryPredicates.toArray(new Predicate[0])));
            }
        }

        // Filter by date range
        if (queryDto.getStartDate() != null) {
            predicates.add(cb.greaterThanOrEqualTo(product.get("releaseDate"), queryDto.getStartDate()));
        }
        if (queryDto.getEndDate() != null) {
            predicates.add(cb.lessThanOrEqualTo(product.get("releaseDate"), queryDto.getEndDate()));
        }

        return predicates;
    }

    /**
     * Build ORDER BY clause based on sort parameters
     * 
     * @param cb            CriteriaBuilder
     * @param product       Root entity
     * @param sortBy        Sort field
     * @param sortDirection Sort direction
     * @return Order clause
     */
    private Order buildOrderBy(CriteriaBuilder cb, Root<Product> product, String sortBy, String sortDirection) {
        boolean isAscending = "ASC".equalsIgnoreCase(sortDirection);

        Expression<?> sortExpression;
        switch (sortBy != null ? sortBy.toLowerCase() : "createdat") {
            case "name":
                sortExpression = product.get("name");
                break;
            case "category":
                sortExpression = product.get("category");
                break;
            case "releasedate":
                sortExpression = product.get("releaseDate");
                break;
            case "createdat":
            default:
                sortExpression = product.get("createdAt");
                break;
        }

        return isAscending ? cb.asc(sortExpression) : cb.desc(sortExpression);
    }
}