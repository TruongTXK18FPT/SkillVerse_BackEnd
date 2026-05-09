package com.exe.skillverse_backend.shared.service.impl;

import com.exe.skillverse_backend.shared.dto.PageResponse;
import com.exe.skillverse_backend.shared.dto.SkillDto;
import com.exe.skillverse_backend.shared.entity.Skill;
import com.exe.skillverse_backend.shared.enums.SkillStatus;
import com.exe.skillverse_backend.shared.exception.BadRequestException;
import com.exe.skillverse_backend.shared.exception.ConflictException;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import com.exe.skillverse_backend.shared.mapper.SkillMapper;
import com.exe.skillverse_backend.shared.repository.SkillRepository;
import com.exe.skillverse_backend.shared.service.SkillReferenceGuard;
import com.exe.skillverse_backend.shared.service.SkillService;
import com.exe.skillverse_backend.shared.util.SkillNameUtils;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SkillServiceImpl implements SkillService {

    private final SkillRepository skillRepository;
    private final SkillReferenceGuard skillReferenceGuard;
    private final SkillMapper skillMapper;
    private final Clock clock;

    // ===== resolve =====

    @Override
    @Transactional
    public SkillDto resolve(String rawSkillName) {
        validateName(rawSkillName);
        String canonicalKey = SkillNameUtils.normalize(rawSkillName);
        Skill skill = skillRepository.findByCanonicalKey(canonicalKey)
                .orElseThrow(() -> new NotFoundException("SKILL_NOT_FOUND"));
        if (skill.getStatus() != SkillStatus.ACTIVE) {
            throw new NotFoundException("SKILL_NOT_FOUND"); // treat inactive/merged as not found for public API
        }
        return skillMapper.toDto(skill);
    }

    // ===== list active =====

    @Override
    @Transactional(readOnly = true)
    public List<SkillDto> listActive() {
        return skillMapper.toDtos(skillRepository.findByStatus(SkillStatus.ACTIVE));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SkillDto> listAll() {
        return skillMapper.toDtos(skillRepository.findAll());
    }

    // ===== create =====

    @Override
    @Transactional
    public SkillDto create(SkillDto dto) {
        validateName(dto.getName());
        String canonicalKey = guardDuplicateCanonical(dto.getName());
        Skill e = buildSkillEntity(dto, canonicalKey, null);
        Skill saved = skillRepository.save(e);
        log.info("Created skill: id={}, name={}, canonicalKey={}", saved.getId(), saved.getName(), canonicalKey);
        return skillMapper.toDto(saved);
    }

    @Override
    @Transactional
    public SkillDto createApproved(SkillDto dto, Long approvedBy) {
        validateName(dto.getName());
        String canonicalKey = guardDuplicateCanonical(dto.getName());
        Skill e = buildSkillEntity(dto, canonicalKey, approvedBy); // approvedBy/At set inside helper
        Skill saved = skillRepository.save(e);
        log.info("Created approved skill: id={}, name={}, approvedBy={}", saved.getId(), saved.getName(), approvedBy);
        return skillMapper.toDto(saved);
    }

    // ===== update =====

    @Override
    @Transactional
    public SkillDto update(Long id, SkillDto dto) {
        Skill e = getOrThrow(id);

        if (e.getStatus() != SkillStatus.ACTIVE) {
            throw new BadRequestException("CANNOT_UPDATE_NON_ACTIVE_SKILL");
        }

        String canonicalKey = SkillNameUtils.normalize(dto.getName());

        if (!Objects.equals(e.getCanonicalKey(), canonicalKey)) {
            if (skillRepository.existsByCanonicalKey(canonicalKey)) {
                throw new ConflictException("SKILL_ALREADY_EXISTS");
            }
        }

        if (dto.getParentSkillId() != null && !dto.getParentSkillId().equals(e.getParentSkillId())) {
            Skill newParent = getOrThrow(dto.getParentSkillId());
            ensureNoCycle(id, newParent.getId());
            e.setParentSkillId(newParent.getId());
        } else if (dto.getParentSkillId() == null) {
            e.setParentSkillId(null);
        }

        e.setName(dto.getName());
        e.setCanonicalKey(canonicalKey);
        e.setDescription(dto.getDescription());
        e.setUpdatedAt(LocalDateTime.now(clock));

        log.info("Updated skill: id={}, name={}, canonicalKey={}", id, e.getName(), canonicalKey);
        return skillMapper.toDto(e);
    }

    // ===== delete (soft) =====

    @Override
    @Transactional
    public void delete(Long id) {
        Skill e = getOrThrow(id);
        if (skillRepository.countByParentSkillId(id) > 0) {
            throw new ConflictException("SKILL_HAS_CHILDREN");
        }
        if (skillReferenceGuard.isSkillReferenced(id)) {
            throw new ConflictException("SKILL_IS_MAPPED_TO_TRACK");
        }
        e.setStatus(SkillStatus.INACTIVE);
        e.setUpdatedAt(LocalDateTime.now(clock));
        skillRepository.save(e);
        log.info("Soft deleted skill id={}", id);
    }

    @Override
    @Transactional
    public void reactivate(Long id) {
        Skill e = getOrThrow(id);
        e.setStatus(SkillStatus.ACTIVE);
        e.setUpdatedAt(LocalDateTime.now(clock));
        skillRepository.save(e);
        log.info("Reactivated skill id={}", id);
    }

    @Override
    @Transactional
    public void hardDelete(Long id) {
        Skill e = getOrThrow(id);
        if (skillRepository.countByParentSkillId(id) > 0) {
            throw new ConflictException("SKILL_HAS_CHILDREN");
        }
        if (skillReferenceGuard.isSkillReferenced(id)) {
            throw new ConflictException("SKILL_IS_MAPPED_TO_TRACK");
        }
        skillRepository.delete(e);
        log.info("Hard deleted skill id={}", id);
    }

    // ===== get =====

    @Override
    @Transactional(readOnly = true)
    public SkillDto get(Long id) {
        return skillMapper.toDto(getOrThrow(id));
    }

    // ===== public queries — ACTIVE only =====

    @Override
    @Transactional(readOnly = true)
    public PageResponse<SkillDto> search(String q, Pageable p) {
        if (q == null || q.isBlank()) {
            return emptyPage(p);
        }
        Page<Skill> page = skillRepository.searchActive(q.trim(), SkillStatus.ACTIVE, p);
        return toPage(page);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<SkillDto> listRoots(Pageable p) {
        Page<Skill> page = skillRepository.findByParentSkillIdIsNullAndStatus(SkillStatus.ACTIVE, p);
        return toPage(page);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SkillDto> listChildren(Long parentId) {
        Skill parent = getOrThrow(parentId);
        List<Skill> children = skillRepository.findByParentSkillIdAndStatusOrderByNameAsc(parent.getId(), SkillStatus.ACTIVE);
        return skillMapper.toDtos(children);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<SkillDto> suggestByPrefix(String prefix, Pageable p) {
        if (prefix == null || prefix.isBlank()) {
            return emptyPage(p);
        }
        Page<Skill> page = skillRepository.findByNameContainingIgnoreCaseAndStatus(prefix.trim(), SkillStatus.ACTIVE, p);
        return toPage(page);
    }

    // ===== tree helpers =====

    @Override
    @Transactional
    public SkillDto reparent(Long id, Long newParentId) {
        Skill e = getOrThrow(id);
        if (newParentId == null) {
            e.setParentSkillId(null);
        } else {
            Skill newParent = getOrThrow(newParentId);
            ensureNoCycle(id, newParent.getId());
            e.setParentSkillId(newParent.getId());
        }
        e.setUpdatedAt(LocalDateTime.now(clock));
        log.info("Reparented skill id={} to parentId={}", id, newParentId);
        return skillMapper.toDto(e);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> pathToRoot(Long id) {
        Skill cur = getOrThrow(id);
        List<Long> path = new ArrayList<>();
        while (cur != null) {
            path.add(cur.getId());
            Long pid = cur.getParentSkillId();
            if (pid == null) break;
            cur = skillRepository.findById(pid).orElse(null);
        }
        return path;
    }

    // ===== private helpers =====

    /** Builds a new Skill entity from DTO. When approvedBy is non-null, sets approvedBy and approvedAt. */
    private Skill buildSkillEntity(SkillDto dto, String canonicalKey, Long approvedBy) {
        Skill e = skillMapper.toEntity(dto);
        e.setName(dto.getName());
        e.setCanonicalKey(canonicalKey);
        e.setStatus(SkillStatus.ACTIVE);
        e.setCreatedAt(LocalDateTime.now(clock));
        e.setUpdatedAt(LocalDateTime.now(clock));

        if (approvedBy != null) {
            e.setApprovedBy(approvedBy);
            e.setApprovedAt(LocalDateTime.now(clock));
        }

        if (dto.getParentSkillId() != null) {
            Skill parent = getOrThrow(dto.getParentSkillId());
            e.setParentSkillId(parent.getId());
        }
        return e;
    }

    /** Normalizes name and throws ConflictException if canonical key already exists. */
    private String guardDuplicateCanonical(String name) {
        String canonicalKey = SkillNameUtils.normalize(name);
        if (skillRepository.existsByCanonicalKey(canonicalKey)) {
            throw new ConflictException("SKILL_ALREADY_EXISTS");
        }
        return canonicalKey;
    }

    private Skill getOrThrow(Long id) {
        return skillRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("SKILL_NOT_FOUND"));
    }

    private void ensureNoCycle(Long nodeId, Long newParentId) {
        if (nodeId.equals(newParentId)) throw new BadRequestException("CANNOT_SET_SELF_AS_PARENT");
        Long cur = newParentId;
        while (cur != null) {
            if (cur.equals(nodeId)) throw new BadRequestException("CYCLE_DETECTED");
            cur = skillRepository.findById(cur).map(Skill::getParentSkillId).orElse(null);
        }
    }

    private void validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new BadRequestException("SKILL_NAME_REQUIRED");
        }
    }

    private String safe(String s) {
        return s == null ? null : s.trim();
    }

    private PageResponse<SkillDto> toPage(Page<Skill> page) {
        return PageResponse.<SkillDto>builder()
                .items(page.map(skillMapper::toDto).getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .total(page.getTotalElements())
                .build();
    }

    private PageResponse<SkillDto> emptyPage(Pageable p) {
        return PageResponse.<SkillDto>builder()
                .items(Collections.emptyList())
                .page(p.getPageNumber())
                .size(p.getPageSize())
                .total(0)
                .build();
    }
}