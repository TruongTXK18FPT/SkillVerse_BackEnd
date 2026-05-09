package com.exe.skillverse_backend.career_taxonomy_service.service.impl;

import com.exe.skillverse_backend.career_taxonomy_service.dto.DomainDto;
import com.exe.skillverse_backend.career_taxonomy_service.dto.JobPositionDto;
import com.exe.skillverse_backend.career_taxonomy_service.dto.JobPositionTrackDto;
import com.exe.skillverse_backend.career_taxonomy_service.dto.JobPositionTrackSkillDto;
import com.exe.skillverse_backend.career_taxonomy_service.entity.Domain;
import com.exe.skillverse_backend.career_taxonomy_service.entity.JobPosition;
import com.exe.skillverse_backend.career_taxonomy_service.entity.JobPositionTrack;
import com.exe.skillverse_backend.career_taxonomy_service.entity.JobPositionTrackSkill;
import com.exe.skillverse_backend.career_taxonomy_service.enums.TaxonomyStatus;
import com.exe.skillverse_backend.career_taxonomy_service.mapper.TaxonomyMapper;
import com.exe.skillverse_backend.career_taxonomy_service.repository.DomainRepository;
import com.exe.skillverse_backend.career_taxonomy_service.repository.JobPositionRepository;
import com.exe.skillverse_backend.career_taxonomy_service.repository.JobPositionTrackRepository;
import com.exe.skillverse_backend.career_taxonomy_service.repository.JobPositionTrackSkillRepository;
import com.exe.skillverse_backend.career_taxonomy_service.service.TaxonomyService;
import com.exe.skillverse_backend.shared.entity.Skill;
import com.exe.skillverse_backend.shared.exception.BadRequestException;
import com.exe.skillverse_backend.shared.exception.ConflictException;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import com.exe.skillverse_backend.shared.repository.SkillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaxonomyServiceImpl implements TaxonomyService {

    private final DomainRepository domainRepository;
    private final JobPositionRepository jobPositionRepository;
    private final JobPositionTrackRepository trackRepository;
    private final JobPositionTrackSkillRepository trackSkillRepository;
    private final SkillRepository skillRepository;
    private final TaxonomyMapper mapper;

    // --- READ APIs ---

    @Override
    @Transactional(readOnly = true)
    public List<DomainDto> listActiveDomains() {
        return mapper.toDomainDtos(domainRepository.findByStatus(TaxonomyStatus.ACTIVE));
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobPositionDto> listActiveJobPositions(Long domainId) {
        if (domainId != null) {
            // Guard: domain itself must be ACTIVE
            Domain domain = domainRepository.findById(domainId)
                    .orElseThrow(() -> new NotFoundException("DOMAIN_NOT_FOUND"));
            if (domain.getStatus() != TaxonomyStatus.ACTIVE) {
                throw new BadRequestException("DOMAIN_NOT_ACTIVE");
            }
            return mapper.toJobPositionDtos(
                    jobPositionRepository.findByDomainIdAndStatus(domainId, TaxonomyStatus.ACTIVE));
        }
        // Single JOIN query — no N+1
        return mapper.toJobPositionDtos(
                jobPositionRepository.findAllActiveWithActiveDomain(TaxonomyStatus.ACTIVE));
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobPositionTrackDto> listActiveTracks(Long jobPositionId) {
        if (jobPositionId != null) {
            // Guard: job position must be ACTIVE and its parent domain must be ACTIVE
            JobPosition jp = jobPositionRepository.findById(jobPositionId)
                    .orElseThrow(() -> new NotFoundException("JOB_POSITION_NOT_FOUND"));
            if (jp.getStatus() != TaxonomyStatus.ACTIVE) {
                throw new BadRequestException("JOB_POSITION_NOT_ACTIVE");
            }
            domainRepository.findById(jp.getDomainId()).ifPresent(d -> {
                if (d.getStatus() != TaxonomyStatus.ACTIVE) {
                    throw new BadRequestException("DOMAIN_NOT_ACTIVE");
                }
            });
            return mapper.toJobPositionTrackDtos(
                    trackRepository.findByJobPositionIdAndStatus(jobPositionId, TaxonomyStatus.ACTIVE));
        }
        // Single JOIN query — no N+1
        return mapper.toJobPositionTrackDtos(
                trackRepository.findAllActiveWithActiveParentChain(TaxonomyStatus.ACTIVE));
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobPositionTrackSkillDto> listTrackSkills(Long trackId) {
        JobPositionTrack track = trackRepository.findById(trackId)
                .orElseThrow(() -> new NotFoundException("TRACK_NOT_FOUND"));
        if (track.getStatus() != TaxonomyStatus.ACTIVE) {
            throw new BadRequestException("TRACK_NOT_ACTIVE");
        }
        // Guard parent chain
        JobPosition jp = jobPositionRepository.findById(track.getJobPositionId())
                .orElseThrow(() -> new NotFoundException("JOB_POSITION_NOT_FOUND"));
        if (jp.getStatus() != TaxonomyStatus.ACTIVE) {
            throw new BadRequestException("JOB_POSITION_NOT_ACTIVE");
        }
        domainRepository.findById(jp.getDomainId()).ifPresent(d -> {
            if (d.getStatus() != TaxonomyStatus.ACTIVE) {
                throw new BadRequestException("DOMAIN_NOT_ACTIVE");
            }
        });
        // Single JOIN query — only ACTIVE skills, no post-fetch filter, no N+1
        return mapper.toTrackSkillDtos(
                trackSkillRepository.findActiveSkillsByTrackId(
                        trackId, com.exe.skillverse_backend.shared.enums.SkillStatus.ACTIVE));
    }

    // --- READ APIs FOR ADMIN (ALL) ---

    @Override
    @Transactional(readOnly = true)
    public List<DomainDto> listAllDomains() {
        return mapper.toDomainDtos(domainRepository.findAll());
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobPositionDto> listAllJobPositions(Long domainId) {
        if (domainId != null) {
            return mapper.toJobPositionDtos(jobPositionRepository.findByDomainId(domainId));
        }
        return mapper.toJobPositionDtos(jobPositionRepository.findAll());
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobPositionTrackDto> listAllTracks(Long jobPositionId) {
        if (jobPositionId != null) {
            return mapper.toJobPositionTrackDtos(trackRepository.findByJobPositionId(jobPositionId));
        }
        return mapper.toJobPositionTrackDtos(trackRepository.findAll());
    }

    // --- WRITE APIs ---

    @Override
    @Transactional
    public DomainDto createDomain(DomainDto dto) {
        String normalizedCode = normalizeCode(dto.getCode());
        if (domainRepository.existsByCode(normalizedCode)) {
            throw new ConflictException("DOMAIN_CODE_EXISTS");
        }
        Domain d = mapper.toDomain(dto);
        d.setCode(normalizedCode);
        d.setStatus(TaxonomyStatus.ACTIVE);
        return mapper.toDomainDto(domainRepository.save(d));
    }

    @Override
    @Transactional
    public DomainDto updateDomain(Long id, DomainDto dto) {
        Domain d = domainRepository.findById(id).orElseThrow(() -> new NotFoundException("DOMAIN_NOT_FOUND"));
        String normalizedCode = normalizeCode(dto.getCode());
        if (!d.getCode().equals(normalizedCode) && domainRepository.existsByCode(normalizedCode)) {
            throw new ConflictException("DOMAIN_CODE_EXISTS");
        }
        d.setCode(normalizedCode);
        d.setName(dto.getName().trim());
        d.setDescription(dto.getDescription() != null ? dto.getDescription().trim() : null);
        if (dto.getStatus() != null) {
            d.setStatus(dto.getStatus());
        }
        return mapper.toDomainDto(domainRepository.save(d));
    }

    @Override
    @Transactional
    public void deactivateDomain(Long id) {
        Domain d = domainRepository.findById(id).orElseThrow(() -> new NotFoundException("DOMAIN_NOT_FOUND"));
        d.setStatus(TaxonomyStatus.INACTIVE);
        domainRepository.save(d);
    }

    @Override
    @Transactional
    public void reactivateDomain(Long id) {
        Domain d = domainRepository.findById(id).orElseThrow(() -> new NotFoundException("DOMAIN_NOT_FOUND"));
        d.setStatus(TaxonomyStatus.ACTIVE);
        domainRepository.save(d);
    }

    @Override
    @Transactional
    public void hardDeleteDomain(Long id) {
        if (!domainRepository.existsById(id)) {
            throw new NotFoundException("DOMAIN_NOT_FOUND");
        }
        domainRepository.deleteById(id);
    }

    @Override
    @Transactional
    public JobPositionDto createJobPosition(JobPositionDto dto) {
        String normalizedCode = normalizeCode(dto.getCode());
        if (jobPositionRepository.existsByCode(normalizedCode)) {
            throw new ConflictException("JOB_POSITION_CODE_EXISTS");
        }
        Domain domain = domainRepository.findById(dto.getDomainId())
                .orElseThrow(() -> new NotFoundException("DOMAIN_NOT_FOUND"));
        if (domain.getStatus() != TaxonomyStatus.ACTIVE) {
            throw new BadRequestException("CANNOT_CREATE_UNDER_INACTIVE_DOMAIN");
        }
        JobPosition jp = mapper.toJobPosition(dto);
        jp.setCode(normalizedCode);
        jp.setDomain(domain);
        jp.setStatus(TaxonomyStatus.ACTIVE);
        return mapper.toJobPositionDto(jobPositionRepository.save(jp));
    }

    @Override
    @Transactional
    public JobPositionDto updateJobPosition(Long id, JobPositionDto dto) {
        JobPosition jp = jobPositionRepository.findById(id).orElseThrow(() -> new NotFoundException("JOB_POSITION_NOT_FOUND"));
        String normalizedCode = normalizeCode(dto.getCode());
        if (!jp.getCode().equals(normalizedCode) && jobPositionRepository.existsByCode(normalizedCode)) {
            throw new ConflictException("JOB_POSITION_CODE_EXISTS");
        }
        if (!jp.getDomainId().equals(dto.getDomainId())) {
            Domain domain = domainRepository.findById(dto.getDomainId())
                    .orElseThrow(() -> new NotFoundException("DOMAIN_NOT_FOUND"));
            if (domain.getStatus() != TaxonomyStatus.ACTIVE) {
                throw new BadRequestException("CANNOT_MOVE_TO_INACTIVE_DOMAIN");
            }
            jp.setDomain(domain);
        }
        jp.setCode(normalizedCode);
        jp.setName(dto.getName().trim());
        jp.setDescription(dto.getDescription() != null ? dto.getDescription().trim() : null);
        if (dto.getStatus() != null) {
            jp.setStatus(dto.getStatus());
        }
        return mapper.toJobPositionDto(jobPositionRepository.save(jp));
    }

    @Override
    @Transactional
    public void deactivateJobPosition(Long id) {
        JobPosition jp = jobPositionRepository.findById(id).orElseThrow(() -> new NotFoundException("JOB_POSITION_NOT_FOUND"));
        jp.setStatus(TaxonomyStatus.INACTIVE);
        jobPositionRepository.save(jp);
    }

    @Override
    @Transactional
    public void reactivateJobPosition(Long id) {
        JobPosition jp = jobPositionRepository.findById(id).orElseThrow(() -> new NotFoundException("JOB_POSITION_NOT_FOUND"));
        jp.setStatus(TaxonomyStatus.ACTIVE);
        jobPositionRepository.save(jp);
    }

    @Override
    @Transactional
    public void hardDeleteJobPosition(Long id) {
        if (!jobPositionRepository.existsById(id)) {
            throw new NotFoundException("JOB_POSITION_NOT_FOUND");
        }
        jobPositionRepository.deleteById(id);
    }

    @Override
    @Transactional
    public JobPositionTrackDto createTrack(JobPositionTrackDto dto) {
        String normalizedCode = normalizeCode(dto.getCode());
        if (trackRepository.existsByCode(normalizedCode)) {
            throw new ConflictException("TRACK_CODE_EXISTS");
        }
        JobPosition jp = jobPositionRepository.findById(dto.getJobPositionId())
                .orElseThrow(() -> new NotFoundException("JOB_POSITION_NOT_FOUND"));
        if (jp.getStatus() != TaxonomyStatus.ACTIVE) {
            throw new BadRequestException("CANNOT_CREATE_UNDER_INACTIVE_JOB_POSITION");
        }
        Domain domain = domainRepository.findById(jp.getDomainId())
                .orElseThrow(() -> new NotFoundException("DOMAIN_NOT_FOUND"));
        if (domain.getStatus() != TaxonomyStatus.ACTIVE) {
            throw new BadRequestException("CANNOT_CREATE_UNDER_INACTIVE_DOMAIN");
        }
        JobPositionTrack track = mapper.toJobPositionTrack(dto);
        track.setCode(normalizedCode);
        track.setJobPosition(jp);
        track.setStatus(TaxonomyStatus.ACTIVE);
        return mapper.toJobPositionTrackDto(trackRepository.save(track));
    }

    @Override
    @Transactional
    public JobPositionTrackDto updateTrack(Long id, JobPositionTrackDto dto) {
        JobPositionTrack track = trackRepository.findById(id).orElseThrow(() -> new NotFoundException("TRACK_NOT_FOUND"));
        String normalizedCode = normalizeCode(dto.getCode());
        if (!track.getCode().equals(normalizedCode) && trackRepository.existsByCode(normalizedCode)) {
            throw new ConflictException("TRACK_CODE_EXISTS");
        }
        if (!track.getJobPositionId().equals(dto.getJobPositionId())) {
            JobPosition jp = jobPositionRepository.findById(dto.getJobPositionId())
                    .orElseThrow(() -> new NotFoundException("JOB_POSITION_NOT_FOUND"));
            if (jp.getStatus() != TaxonomyStatus.ACTIVE) {
                throw new BadRequestException("CANNOT_MOVE_TO_INACTIVE_JOB_POSITION");
            }
            Domain domain = domainRepository.findById(jp.getDomainId())
                    .orElseThrow(() -> new NotFoundException("DOMAIN_NOT_FOUND"));
            if (domain.getStatus() != TaxonomyStatus.ACTIVE) {
                throw new BadRequestException("CANNOT_MOVE_TO_INACTIVE_DOMAIN");
            }
            track.setJobPosition(jp);
        }
        track.setCode(normalizedCode);
        track.setName(dto.getName().trim());
        track.setDescription(dto.getDescription() != null ? dto.getDescription().trim() : null);
        track.setTargetLevel(dto.getTargetLevel());
        if (dto.getStatus() != null) {
            track.setStatus(dto.getStatus());
        }
        return mapper.toJobPositionTrackDto(trackRepository.save(track));
    }

    @Override
    @Transactional
    public void deactivateTrack(Long id) {
        JobPositionTrack track = trackRepository.findById(id).orElseThrow(() -> new NotFoundException("TRACK_NOT_FOUND"));
        track.setStatus(TaxonomyStatus.INACTIVE);
        trackRepository.save(track);
    }

    @Override
    @Transactional
    public void reactivateTrack(Long id) {
        JobPositionTrack track = trackRepository.findById(id).orElseThrow(() -> new NotFoundException("TRACK_NOT_FOUND"));
        track.setStatus(TaxonomyStatus.ACTIVE);
        trackRepository.save(track);
    }

    @Override
    @Transactional
    public void hardDeleteTrack(Long id) {
        if (!trackRepository.existsById(id)) {
            throw new NotFoundException("TRACK_NOT_FOUND");
        }
        // First delete track-skills
        trackSkillRepository.deleteByTrackId(id);
        trackRepository.deleteById(id);
    }

    @Override
    @Transactional
    public List<JobPositionTrackSkillDto> updateTrackSkills(Long trackId, List<JobPositionTrackSkillDto> skillDtos) {
        JobPositionTrack track = trackRepository.findById(trackId)
                .orElseThrow(() -> new NotFoundException("TRACK_NOT_FOUND"));
        
        if (track.getStatus() != TaxonomyStatus.ACTIVE) {
            throw new BadRequestException("TRACK_NOT_ACTIVE");
        }
        // Guard parent chain
        JobPosition jp = jobPositionRepository.findById(track.getJobPositionId())
                .orElseThrow(() -> new NotFoundException("JOB_POSITION_NOT_FOUND"));
        if (jp.getStatus() != TaxonomyStatus.ACTIVE) {
            throw new BadRequestException("JOB_POSITION_NOT_ACTIVE");
        }
        domainRepository.findById(jp.getDomainId()).ifPresent(d -> {
            if (d.getStatus() != TaxonomyStatus.ACTIVE) {
                throw new BadRequestException("DOMAIN_NOT_ACTIVE");
            }
        });

        List<JobPositionTrackSkill> newEntities = new ArrayList<>();
        java.util.Set<Long> seenSkillIds = new java.util.HashSet<>();
        
        for (int i = 0; i < skillDtos.size(); i++) {
            JobPositionTrackSkillDto dto = skillDtos.get(i);
            
            if (seenSkillIds.contains(dto.getSkillId())) {
                throw new BadRequestException("DUPLICATE_SKILL_ID_IN_REQUEST: " + dto.getSkillId());
            }
            seenSkillIds.add(dto.getSkillId());
            
            Skill skill = skillRepository.findById(dto.getSkillId())
                    .orElseThrow(() -> new BadRequestException("SKILL_NOT_FOUND: " + dto.getSkillId()));
            
            if (skill.getStatus() != com.exe.skillverse_backend.shared.enums.SkillStatus.ACTIVE) {
                throw new BadRequestException("SKILL_NOT_ACTIVE: " + skill.getId());
            }
                    
            JobPositionTrackSkill ts = new JobPositionTrackSkill();
            ts.setTrack(track);
            ts.setSkill(skill);
            ts.setRequirementType(dto.getRequirementType() != null ? dto.getRequirementType() : com.exe.skillverse_backend.career_taxonomy_service.enums.RequirementType.REQUIRED);
            ts.setImportanceLevel(dto.getImportanceLevel() != null ? dto.getImportanceLevel() : com.exe.skillverse_backend.career_taxonomy_service.enums.ImportanceLevel.MEDIUM);
            ts.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : i);
            
            newEntities.add(ts);
        }
        
        // Overwrite strategy for MVP: delete all existing and re-insert after validation is successful
        // Flush immediately so the delete reaches the database before we insert the replacement set.
        trackSkillRepository.deleteByTrackId(trackId);
        trackSkillRepository.flush();
        
        List<JobPositionTrackSkill> saved = trackSkillRepository.saveAll(newEntities);
        return mapper.toTrackSkillDtos(saved);
    }

    // --------------- private helpers ---------------

    /**
     * Normalize a taxonomy code to UPPER_SNAKE_CASE.
     * Rules: trim → uppercase → replace spaces/hyphens/dots with '_' → collapse repeated '_'.
     * Examples: "backend dev" → "BACKEND_DEV", "SE" → "SE", "backend" → "BACKEND"
     */
    private static String normalizeCode(String raw) {
        if (raw == null) return null;
        return raw.trim()
                  .toUpperCase(java.util.Locale.ROOT)
                  .replaceAll("[\\s\\-\\.]+", "_")
                  .replaceAll("_+", "_")
                  .replaceAll("^_|_$", "");
    }
}
