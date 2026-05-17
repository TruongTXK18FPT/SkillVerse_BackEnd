package com.exe.skillverse_backend.career_taxonomy_service.service;

import com.exe.skillverse_backend.career_taxonomy_service.dto.DomainDto;
import com.exe.skillverse_backend.career_taxonomy_service.dto.JobPositionDto;
import com.exe.skillverse_backend.career_taxonomy_service.dto.JobPositionTrackDto;
import com.exe.skillverse_backend.career_taxonomy_service.dto.JobPositionTrackSkillDto;

import java.util.List;

public interface TaxonomyService {

    // Read APIs for Dev B / Users
    List<DomainDto> listActiveDomains();
    List<JobPositionDto> listActiveJobPositions(Long domainId);
    List<JobPositionTrackDto> listActiveTracks(Long jobPositionId);
    List<JobPositionTrackSkillDto> listTrackSkills(Long trackId);
    List<JobPositionDto> searchJobPositionsBySkill(Long skillId);

    // Read APIs for Admin (includes inactive)
    List<DomainDto> listAllDomains();
    List<JobPositionDto> listAllJobPositions(Long domainId);
    List<JobPositionTrackDto> listAllTracks(Long jobPositionId);

    // Write APIs for Admin
    DomainDto createDomain(DomainDto dto);
    DomainDto updateDomain(Long id, DomainDto dto);
    void deactivateDomain(Long id);
    void reactivateDomain(Long id);
    void hardDeleteDomain(Long id);

    JobPositionDto createJobPosition(JobPositionDto dto);
    JobPositionDto updateJobPosition(Long id, JobPositionDto dto);
    void deactivateJobPosition(Long id);
    void reactivateJobPosition(Long id);
    void hardDeleteJobPosition(Long id);

    JobPositionTrackDto createTrack(JobPositionTrackDto dto);
    JobPositionTrackDto updateTrack(Long id, JobPositionTrackDto dto);
    void deactivateTrack(Long id);
    void reactivateTrack(Long id);
    void hardDeleteTrack(Long id);

    List<JobPositionTrackSkillDto> updateTrackSkills(Long trackId, List<JobPositionTrackSkillDto> skillDtos);
}
