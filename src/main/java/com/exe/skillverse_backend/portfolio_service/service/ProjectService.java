package com.exe.skillverse_backend.portfolio_service.service;

import com.exe.skillverse_backend.portfolio_service.dto.request.ProjectRequestDto;
import com.exe.skillverse_backend.portfolio_service.dto.request.PortfolioQueryDto;
import com.exe.skillverse_backend.portfolio_service.dto.response.ProjectResponseDto;
import com.exe.skillverse_backend.portfolio_service.entity.Project;
import com.exe.skillverse_backend.portfolio_service.exception.PortfolioNotFoundException;
import com.exe.skillverse_backend.portfolio_service.repository.ProjectRepository;
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
public class ProjectService {

    private final ProjectRepository projectRepository;

    public ProjectResponseDto createProject(Long userId, ProjectRequestDto requestDto) {
        log.info("Creating project for user: {}", userId);

        Project project = Project.builder()
                .userId(userId)
                .title(requestDto.getTitle())
                .description(requestDto.getDescription())
                .techStack(requestDto.getTechStack())
                .projectUrl(requestDto.getProjectUrl())
                .mediaId(requestDto.getMediaId())
                .completedDate(requestDto.getCompletedDate())
                .build();

        Project savedProject = projectRepository.save(project);
        log.info("Project created successfully with id: {}", savedProject.getId());

        return convertToResponseDto(savedProject);
    }

    public ProjectResponseDto updateProject(Long userId, Long projectId, ProjectRequestDto requestDto) {
        log.info("Updating project: {} for user: {}", projectId, userId);

        Project project = projectRepository.findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new PortfolioNotFoundException("Project not found or access denied"));

        project.setTitle(requestDto.getTitle());
        project.setDescription(requestDto.getDescription());
        project.setTechStack(requestDto.getTechStack());
        project.setProjectUrl(requestDto.getProjectUrl());
        project.setMediaId(requestDto.getMediaId());
        project.setCompletedDate(requestDto.getCompletedDate());

        Project updatedProject = projectRepository.save(project);
        log.info("Project updated successfully: {}", projectId);

        return convertToResponseDto(updatedProject);
    }

    public void deleteProject(Long userId, Long projectId) {
        log.info("Deleting project: {} for user: {}", projectId, userId);

        Project project = projectRepository.findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new PortfolioNotFoundException("Project not found or access denied"));

        projectRepository.delete(project);
        log.info("Project deleted successfully: {}", projectId);
    }

    @Transactional(readOnly = true)
    public ProjectResponseDto getProjectById(Long userId, Long projectId) {
        log.debug("Fetching project: {} for user: {}", projectId, userId);

        Project project = projectRepository.findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new PortfolioNotFoundException("Project not found or access denied"));

        return convertToResponseDto(project);
    }

    @Transactional(readOnly = true)
    public List<ProjectResponseDto> getAllProjectsByUserId(Long userId) {
        log.debug("Fetching all projects for user: {}", userId);

        List<Project> projects = projectRepository.findByUserIdWithMedia(userId);
        return projects.stream()
                .map(this::convertToResponseDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<ProjectResponseDto> getProjectsByUserId(Long userId, Pageable pageable) {
        log.debug("Fetching projects with pagination for user: {}", userId);

        Page<Project> projects = projectRepository.findByUserId(userId, pageable);
        return projects.map(this::convertToResponseDto);
    }

    @Transactional(readOnly = true)
    public List<ProjectResponseDto> getProjectsByTechnology(Long userId, String technology) {
        log.debug("Fetching projects by technology: {} for user: {}", technology, userId);

        List<Project> projects = projectRepository.findByUserIdAndTechStackContainingIgnoreCase(userId, technology);
        return projects.stream()
                .map(this::convertToResponseDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProjectResponseDto> getProjectsByDateRange(Long userId, LocalDate startDate, LocalDate endDate) {
        log.debug("Fetching projects by date range for user: {}", userId);

        List<Project> projects = projectRepository.findByUserIdAndCompletedDateBetween(userId, startDate, endDate);
        return projects.stream()
                .map(this::convertToResponseDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long getProjectCountByUserId(Long userId) {
        return projectRepository.countByUserId(userId);
    }

    private ProjectResponseDto convertToResponseDto(Project project) {
        return ProjectResponseDto.builder()
                .id(project.getId())
                .userId(project.getUserId())
                .title(project.getTitle())
                .description(project.getDescription())
                .techStack(project.getTechStack())
                .projectUrl(project.getProjectUrl())
                .mediaId(project.getMediaId())
                .mediaUrl(project.getMedia() != null ? project.getMedia().getUrl() : null)
                .completedDate(project.getCompletedDate())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }

    // New flexible query methods
    public List<ProjectResponseDto> getProjects(Long userId, PortfolioQueryDto queryDto) {
        log.debug("Getting projects for user: {} with query filters", userId);
        List<Project> projects = projectRepository.findProjectsByQuery(userId, queryDto);
        return projects.stream()
                .map(this::convertToResponseDto)
                .collect(Collectors.toList());
    }

    public Page<ProjectResponseDto> getProjects(Long userId, PortfolioQueryDto queryDto, Pageable pageable) {
        log.debug("Getting projects with pagination for user: {} with query filters", userId);
        Page<Project> projects = projectRepository.findProjectsByQuery(userId, queryDto, pageable);
        return projects.map(this::convertToResponseDto);
    }

    public long getProjectsCount(Long userId, PortfolioQueryDto queryDto) {
        log.debug("Getting project count for user: {} with query filters", userId);
        return projectRepository.countProjectsByQuery(userId, queryDto);
    }
}