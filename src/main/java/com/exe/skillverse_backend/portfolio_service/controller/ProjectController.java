package com.exe.skillverse_backend.portfolio_service.controller;

import com.exe.skillverse_backend.portfolio_service.dto.request.ProjectRequestDto;
import com.exe.skillverse_backend.portfolio_service.dto.request.PortfolioQueryDto;
import com.exe.skillverse_backend.portfolio_service.dto.response.ProjectResponseDto;
import com.exe.skillverse_backend.portfolio_service.dto.response.PortfolioCountResponseDto;
import com.exe.skillverse_backend.portfolio_service.service.ProjectService;
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
@RequestMapping("/api/portfolio/projects")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Project Management", description = "APIs for managing user projects in portfolio")
@CrossOrigin(origins = "*")
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    @Operation(summary = "Create a new project", description = "Creates a new project for the authenticated user")
    public ResponseEntity<ProjectResponseDto> createProject(
            @Parameter(description = "User ID", required = true) @RequestParam Long userId,
            @Valid @RequestBody ProjectRequestDto requestDto) {

        log.info("Creating project for user: {}", userId);
        ProjectResponseDto responseDto = projectService.createProject(userId, requestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
    }

    @PutMapping("/{projectId}")
    @Operation(summary = "Update a project", description = "Updates an existing project by ID")
    public ResponseEntity<ProjectResponseDto> updateProject(
            @Parameter(description = "User ID", required = true) @RequestParam Long userId,
            @Parameter(description = "Project ID", required = true) @PathVariable Long projectId,
            @Valid @RequestBody ProjectRequestDto requestDto) {

        log.info("Updating project: {} for user: {}", projectId, userId);
        ProjectResponseDto responseDto = projectService.updateProject(userId, projectId, requestDto);
        return ResponseEntity.ok(responseDto);
    }

    @DeleteMapping("/{projectId}")
    @Operation(summary = "Delete a project", description = "Deletes a project by ID")
    public ResponseEntity<Void> deleteProject(
            @Parameter(description = "User ID", required = true) @RequestParam Long userId,
            @Parameter(description = "Project ID", required = true) @PathVariable Long projectId) {

        log.info("Deleting project: {} for user: {}", projectId, userId);
        projectService.deleteProject(userId, projectId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{projectId}")
    @Operation(summary = "Get project by ID", description = "Retrieves a specific project by ID")
    public ResponseEntity<ProjectResponseDto> getProjectById(
            @Parameter(description = "User ID", required = true) @RequestParam Long userId,
            @Parameter(description = "Project ID", required = true) @PathVariable Long projectId) {

        log.debug("Fetching project: {} for user: {}", projectId, userId);
        ProjectResponseDto responseDto = projectService.getProjectById(userId, projectId);
        return ResponseEntity.ok(responseDto);
    }

    @GetMapping
    @Operation(summary = "Get projects with flexible filtering", description = "Retrieves projects with optional filtering by search, technologies, date range, and pagination")
    public ResponseEntity<?> getProjects(
            @Parameter(description = "User ID", required = true) @RequestParam Long userId,
            @Parameter(description = "Search term for title/description/tech stack") @RequestParam(required = false) String search,
            @Parameter(description = "Comma-separated list of technologies") @RequestParam(required = false) List<String> technologies,
            @Parameter(description = "Start date (YYYY-MM-DD)") @RequestParam(required = false) String startDate,
            @Parameter(description = "End date (YYYY-MM-DD)") @RequestParam(required = false) String endDate,
            @Parameter(description = "Include only public projects") @RequestParam(required = false) Boolean isPublic,
            @Parameter(description = "Page number (0-based)") @RequestParam(required = false, defaultValue = "0") Integer page,
            @Parameter(description = "Page size") @RequestParam(required = false, defaultValue = "10") Integer size,
            @Parameter(description = "Sort field (title, createdAt, completedDate)") @RequestParam(required = false, defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction (ASC, DESC)") @RequestParam(required = false, defaultValue = "DESC") String sortDirection,
            @Parameter(description = "Return count only") @RequestParam(required = false, defaultValue = "false") Boolean countOnly) {

        log.debug("Fetching projects for user: {} with filters", userId);

        // Build query DTO from parameters
        PortfolioQueryDto queryDto = PortfolioQueryDto.builder()
                .search(search)
                .technologies(technologies)
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
            long count = projectService.getProjectsCount(userId, queryDto);
            PortfolioCountResponseDto countResponse = PortfolioCountResponseDto.builder()
                    .totalCount(count)
                    .projectsCount(count)
                    .productsCount(0)
                    .certificatesCount(0)
                    .build();
            return ResponseEntity.ok(countResponse);
        }

        // Create Pageable from parameters
        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        if (size == Integer.MAX_VALUE) {
            // Return all results as list (for backwards compatibility)
            List<ProjectResponseDto> projects = projectService.getProjects(userId, queryDto);
            return ResponseEntity.ok(projects);
        } else {
            // Return paginated results
            Page<ProjectResponseDto> projects = projectService.getProjects(userId, queryDto, pageable);
            return ResponseEntity.ok(projects);
        }
    }
}