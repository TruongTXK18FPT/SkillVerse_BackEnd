package com.exe.skillverse_backend.portfolio_service.controller;

import com.exe.skillverse_backend.portfolio_service.dto.request.ProductRequestDto;
import com.exe.skillverse_backend.portfolio_service.dto.request.PortfolioQueryDto;
import com.exe.skillverse_backend.portfolio_service.dto.response.ProductResponseDto;
import com.exe.skillverse_backend.portfolio_service.dto.response.PortfolioCountResponseDto;
import com.exe.skillverse_backend.portfolio_service.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/portfolio/products")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Product Management", description = "APIs for managing user products in portfolio")
@CrossOrigin(origins = "*")
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @Operation(summary = "Create a new product", description = "Creates a new product for the authenticated user")
    public ResponseEntity<ProductResponseDto> createProduct(
            @Parameter(description = "User ID", required = true) @RequestParam Long userId,
            @Valid @RequestBody ProductRequestDto requestDto) {

        log.info("Creating product for user: {}", userId);
        ProductResponseDto responseDto = productService.createProduct(userId, requestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
    }

    @PutMapping("/{productId}")
    @Operation(summary = "Update a product", description = "Updates an existing product by ID")
    public ResponseEntity<ProductResponseDto> updateProduct(
            @Parameter(description = "User ID", required = true) @RequestParam Long userId,
            @Parameter(description = "Product ID", required = true) @PathVariable Long productId,
            @Valid @RequestBody ProductRequestDto requestDto) {

        log.info("Updating product: {} for user: {}", productId, userId);
        ProductResponseDto responseDto = productService.updateProduct(userId, productId, requestDto);
        return ResponseEntity.ok(responseDto);
    }

    @DeleteMapping("/{productId}")
    @Operation(summary = "Delete a product", description = "Deletes a product by ID")
    public ResponseEntity<Void> deleteProduct(
            @Parameter(description = "User ID", required = true) @RequestParam Long userId,
            @Parameter(description = "Product ID", required = true) @PathVariable Long productId) {

        log.info("Deleting product: {} for user: {}", productId, userId);
        productService.deleteProduct(userId, productId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{productId}")
    @Operation(summary = "Get product by ID", description = "Retrieves a specific product by ID")
    public ResponseEntity<ProductResponseDto> getProductById(
            @Parameter(description = "User ID", required = true) @RequestParam Long userId,
            @Parameter(description = "Product ID", required = true) @PathVariable Long productId) {

        log.debug("Fetching product: {} for user: {}", productId, userId);
        ProductResponseDto responseDto = productService.getProductById(userId, productId);
        return ResponseEntity.ok(responseDto);
    }

    @GetMapping
    @Operation(summary = "Get products with flexible filtering", description = "Retrieves products with optional filtering by search, categories, date range, and pagination")
    public ResponseEntity<?> getProducts(
            @Parameter(description = "User ID", required = true) @RequestParam Long userId,
            @Parameter(description = "Search term for title/description/category") @RequestParam(required = false) String search,
            @Parameter(description = "Comma-separated list of categories") @RequestParam(required = false) List<String> categories,
            @Parameter(description = "Start date (YYYY-MM-DD)") @RequestParam(required = false) String startDate,
            @Parameter(description = "End date (YYYY-MM-DD)") @RequestParam(required = false) String endDate,
            @Parameter(description = "Include only public products") @RequestParam(required = false) Boolean isPublic,
            @Parameter(description = "Page number (0-based)") @RequestParam(required = false, defaultValue = "0") Integer page,
            @Parameter(description = "Page size") @RequestParam(required = false, defaultValue = "10") Integer size,
            @Parameter(description = "Sort field (title, category, createdAt, releaseDate)") @RequestParam(required = false, defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction (ASC, DESC)") @RequestParam(required = false, defaultValue = "DESC") String sortDirection,
            @Parameter(description = "Return count only") @RequestParam(required = false, defaultValue = "false") Boolean countOnly,
            @Parameter(description = "Return distinct categories") @RequestParam(required = false, defaultValue = "false") Boolean distinctCategories) {

        log.debug("Fetching products for user: {} with filters", userId);

        // Handle distinct categories request
        if (distinctCategories) {
            List<String> categoryList = productService.getDistinctCategoriesByUserId(userId);
            return ResponseEntity.ok(categoryList);
        }

        // Build query DTO from parameters
        PortfolioQueryDto queryDto = PortfolioQueryDto.builder()
                .search(search)
                .categories(categories)
                .startDate(startDate != null ? java.time.LocalDate.parse(startDate) : null)
                .endDate(endDate != null ? java.time.LocalDate.parse(endDate) : null)
                .isPublic(isPublic)
                .page(page)
                .size(size)
                .sortBy(sortBy)
                .sortDirection(sortDirection)
                .build();

        if (countOnly) {
            // Return count only
            long count = productService.getProductsCount(userId, queryDto);
            PortfolioCountResponseDto countResponse = PortfolioCountResponseDto.builder()
                    .totalCount(count)
                    .projectsCount(0)
                    .productsCount(count)
                    .certificatesCount(0)
                    .build();
            return ResponseEntity.ok(countResponse);
        }

        // Create Pageable from parameters
        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        if (size == Integer.MAX_VALUE) {
            // Return all results as list (for backwards compatibility)
            List<ProductResponseDto> products = productService.getProducts(userId, queryDto);
            return ResponseEntity.ok(products);
        } else {
            // Return paginated results
            Page<ProductResponseDto> products = productService.getProducts(userId, queryDto, pageable);
            return ResponseEntity.ok(products);
        }
    }
}