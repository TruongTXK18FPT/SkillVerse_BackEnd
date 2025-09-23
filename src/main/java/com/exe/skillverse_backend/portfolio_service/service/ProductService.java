package com.exe.skillverse_backend.portfolio_service.service;

import com.exe.skillverse_backend.portfolio_service.dto.request.ProductRequestDto;
import com.exe.skillverse_backend.portfolio_service.dto.request.PortfolioQueryDto;
import com.exe.skillverse_backend.portfolio_service.dto.response.ProductResponseDto;
import com.exe.skillverse_backend.portfolio_service.entity.Product;
import com.exe.skillverse_backend.portfolio_service.exception.PortfolioNotFoundException;
import com.exe.skillverse_backend.portfolio_service.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductQueryBuilderService productQueryBuilderService;

    public ProductResponseDto createProduct(Long userId, ProductRequestDto requestDto) {
        log.info("Creating product for user: {}", userId);

        Product product = Product.builder()
                .userId(userId)
                .name(requestDto.getName())
                .description(requestDto.getDescription())
                .category(requestDto.getCategory())
                .productUrl(requestDto.getProductUrl())
                .mediaId(requestDto.getMediaId())
                .releaseDate(requestDto.getReleaseDate())
                .build();

        Product savedProduct = productRepository.save(product);
        log.info("Product created successfully with id: {}", savedProduct.getId());

        return convertToResponseDto(savedProduct);
    }

    public ProductResponseDto updateProduct(Long userId, Long productId, ProductRequestDto requestDto) {
        log.info("Updating product: {} for user: {}", productId, userId);

        Product product = productRepository.findByIdAndUserId(productId, userId)
                .orElseThrow(() -> new PortfolioNotFoundException("Product not found or access denied"));

        product.setName(requestDto.getName());
        product.setDescription(requestDto.getDescription());
        product.setCategory(requestDto.getCategory());
        product.setProductUrl(requestDto.getProductUrl());
        product.setMediaId(requestDto.getMediaId());
        product.setReleaseDate(requestDto.getReleaseDate());

        Product updatedProduct = productRepository.save(product);
        log.info("Product updated successfully: {}", productId);

        return convertToResponseDto(updatedProduct);
    }

    public void deleteProduct(Long userId, Long productId) {
        log.info("Deleting product: {} for user: {}", productId, userId);

        Product product = productRepository.findByIdAndUserId(productId, userId)
                .orElseThrow(() -> new PortfolioNotFoundException("Product not found or access denied"));

        productRepository.delete(product);
        log.info("Product deleted successfully: {}", productId);
    }

    @Transactional(readOnly = true)
    public ProductResponseDto getProductById(Long userId, Long productId) {
        log.debug("Fetching product: {} for user: {}", productId, userId);

        Product product = productRepository.findByIdAndUserId(productId, userId)
                .orElseThrow(() -> new PortfolioNotFoundException("Product not found or access denied"));

        return convertToResponseDto(product);
    }

    @Transactional(readOnly = true)
    public List<ProductResponseDto> getAllProductsByUserId(Long userId) {
        log.debug("Fetching all products for user: {}", userId);

        List<Product> products = productRepository.findByUserIdWithMedia(userId);
        return products.stream()
                .map(this::convertToResponseDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<ProductResponseDto> getProductsByUserId(Long userId, Pageable pageable) {
        log.debug("Fetching products with pagination for user: {}", userId);

        Page<Product> products = productRepository.findByUserId(userId, pageable);
        return products.map(this::convertToResponseDto);
    }

    @Transactional(readOnly = true)
    public List<ProductResponseDto> getProductsByCategory(Long userId, String category) {
        log.debug("Fetching products by category: {} for user: {}", category, userId);

        List<Product> products = productRepository.findByUserIdAndCategoryIgnoreCase(userId, category);
        return products.stream()
                .map(this::convertToResponseDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProductResponseDto> getProductsByDateRange(Long userId, LocalDate startDate, LocalDate endDate) {
        log.debug("Fetching products by date range for user: {}", userId);

        List<Product> products = productRepository.findByUserIdAndReleaseDateBetween(userId, startDate, endDate);
        return products.stream()
                .map(this::convertToResponseDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<String> getDistinctCategoriesByUserId(Long userId) {
        log.debug("Fetching distinct categories for user: {}", userId);
        return productRepository.findDistinctCategoriesByUserId(userId);
    }

    @Transactional(readOnly = true)
    public long getProductCountByUserId(Long userId) {
        return productRepository.countByUserId(userId);
    }

    private ProductResponseDto convertToResponseDto(Product product) {
        return ProductResponseDto.builder()
                .id(product.getId())
                .userId(product.getUserId())
                .name(product.getName())
                .description(product.getDescription())
                .category(product.getCategory())
                .productUrl(product.getProductUrl())
                .mediaId(product.getMediaId())
                .mediaUrl(product.getMedia() != null ? product.getMedia().getUrl() : null)
                .releaseDate(product.getReleaseDate())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }

    // New flexible query methods using ProductQueryBuilderService
    public List<ProductResponseDto> getProducts(Long userId, PortfolioQueryDto queryDto) {
        log.debug("Getting products for user: {} with query filters", userId);
        List<Product> products = productQueryBuilderService.findProductsByQuery(userId, queryDto);
        return products.stream()
                .map(this::convertToResponseDto)
                .collect(Collectors.toList());
    }

    public Page<ProductResponseDto> getProducts(Long userId, PortfolioQueryDto queryDto, Pageable pageable) {
        log.debug("Getting products with pagination for user: {} with query filters", userId);
        Page<Product> products = productQueryBuilderService.findProductsByQuery(userId, queryDto, pageable);
        return products.map(this::convertToResponseDto);
    }

    public long getProductsCount(Long userId, PortfolioQueryDto queryDto) {
        log.debug("Getting product count for user: {} with query filters", userId);
        return productQueryBuilderService.countProductsByQuery(userId, queryDto);
    }
}