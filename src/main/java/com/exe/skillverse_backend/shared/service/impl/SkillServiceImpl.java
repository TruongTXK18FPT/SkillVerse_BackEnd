package com.exe.skillverse_backend.shared.service.impl;

import com.exe.skillverse_backend.shared.dto.SkillDto;
import com.exe.skillverse_backend.shared.dto.PageResponse;
import com.exe.skillverse_backend.shared.entity.Skill;
import com.exe.skillverse_backend.shared.exception.BadRequestException;
import com.exe.skillverse_backend.shared.exception.ConflictException;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import com.exe.skillverse_backend.shared.mapper.SkillMapper;
import com.exe.skillverse_backend.shared.repository.SkillRepository;
import com.exe.skillverse_backend.shared.service.SkillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SkillServiceImpl implements SkillService {

    private final SkillRepository skillRepository;
    private final SkillMapper skillMapper;
    private final Clock clock;

    @Override
    @Transactional
    public SkillDto create(SkillDto dto) {
        validateName(dto.getName());
        // Enforce unique theo name+category (nếu muốn)
        skillRepository.findByNameIgnoreCaseAndCategoryIgnoreCase(
                dto.getName(), safe(dto.getCategory())
        ).ifPresent(s -> { throw new ConflictException("SKILL_ALREADY_EXISTS"); });

        Skill e = skillMapper.toEntity(dto);
        e.setCreatedAt(LocalDateTime.now(clock));
        e.setUpdatedAt(LocalDateTime.now(clock));

        // set parent nếu có
        if (dto.getParentSkillId() != null) {
            Skill parent = getOrThrow(dto.getParentSkillId());
            e.setParentSkillId(parent.getId());
            e.setParentSkill(parent);
        }

        Skill saved = skillRepository.save(e);
        log.info("Created skill: id={}, name={}", saved.getId(), saved.getName());
        return skillMapper.toDto(saved);
    }

    @Override
    @Transactional
    public SkillDto update(Long id, SkillDto dto) {
        Skill e = getOrThrow(id);

        // nếu đổi name/category thì kiểm tra trùng
        if (hasChangedNameOrCategory(e, dto)) {
            skillRepository.findByNameIgnoreCaseAndCategoryIgnoreCase(
                    dto.getName(), safe(dto.getCategory())
            ).ifPresent(existing -> {
                if (!existing.getId().equals(id)) {
                    throw new ConflictException("SKILL_ALREADY_EXISTS");
                }
            });
        }

        // re-parent nếu có thay đổi
        if (dto.getParentSkillId() != null && !dto.getParentSkillId().equals(e.getParentSkillId())) {
            Skill newParent = getOrThrow(dto.getParentSkillId());
            // chống tự làm con của chính mình hoặc tạo cycle
            ensureNoCycle(id, newParent.getId());
            e.setParentSkillId(newParent.getId());
            e.setParentSkill(newParent);
        } else if (dto.getParentSkillId() == null) {
            e.setParentSkillId(null);
            e.setParentSkill(null);
        }

        // cập nhật metadata
        e.setName(dto.getName());
        e.setCategory(safe(dto.getCategory()));
        e.setDescription(dto.getDescription());
        e.setUpdatedAt(LocalDateTime.now(clock));

        log.info("Updated skill: id={}, name={}", id, e.getName());
        return skillMapper.toDto(e);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Skill e = getOrThrow(id);
        long childCount = skillRepository.countByParentSkillId(id);
        if (childCount > 0) {
            throw new ConflictException("SKILL_HAS_CHILDREN"); // hoặc policy: chuyển orphan
        }
        skillRepository.delete(e);
        log.info("Deleted skill id={}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public SkillDto get(Long id) {
        return skillMapper.toDto(getOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<SkillDto> search(String q, Pageable p) {
        Page<Skill> page = (q == null || q.isBlank())
                ? Page.empty(p)
                : skillRepository.search(q.trim(), p);
        return toPage(page);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<SkillDto> listByCategory(String category, Pageable p) {
        Page<Skill> page = skillRepository.findByCategoryIgnoreCase(safe(category), p);
        return toPage(page);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<SkillDto> listRoots(Pageable p) {
        Page<Skill> page = skillRepository.findByParentSkillIdIsNull(p);
        return toPage(page);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SkillDto> listChildren(Long parentId) {
        Skill parent = getOrThrow(parentId);
        List<Skill> children = skillRepository.findByParentSkillIdOrderByNameAsc(parent.getId());
        return skillMapper.toDtos(children);
    }

    @Override
    @Transactional
    public SkillDto reparent(Long id, Long newParentId) {
        Skill e = getOrThrow(id);
        if (newParentId == null) {
            e.setParentSkillId(null);
            e.setParentSkill(null);
        } else {
            Skill newParent = getOrThrow(newParentId);
            ensureNoCycle(id, newParent.getId());
            e.setParentSkillId(newParent.getId());
            e.setParentSkill(newParent);
        }
        e.setUpdatedAt(LocalDateTime.now(clock));
        log.info("Reparented skill id={} to parentId={}", id, newParentId);
        return skillMapper.toDto(e);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> pathToRoot(Long id) {
        Skill cur = getOrThrow(id);
        List<Long> path = new java.util.ArrayList<>();
        while (cur != null) {
            path.add(cur.getId());
            Long pid = cur.getParentSkillId();
            if (pid == null) break;
            cur = skillRepository.findById(pid).orElse(null);
        }
        return path; // id -> ... -> root
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<SkillDto> suggestByPrefix(String prefix, Pageable p) {
        if (prefix == null || prefix.isBlank()) return PageResponse.<SkillDto>builder()
                .items(java.util.Collections.emptyList()).page(p.getPageNumber()).size(p.getPageSize()).total(0).build();
        Page<Skill> page = skillRepository.findByNameStartingWithIgnoreCase(prefix.trim(), p);
        return toPage(page);
    }

    // ===== helpers =====
    private Skill getOrThrow(Long id) {
        return skillRepository.findById(id).orElseThrow(() -> new NotFoundException("SKILL_NOT_FOUND"));
    }

    private boolean hasChangedNameOrCategory(Skill e, SkillDto dto) {
        return !java.util.Objects.equals(normalize(e.getName()), normalize(dto.getName()))
            || !java.util.Objects.equals(normalize(e.getCategory()), normalize(dto.getCategory()));
    }

    private void ensureNoCycle(Long nodeId, Long newParentId) {
        if (nodeId.equals(newParentId)) throw new BadRequestException("CANNOT_SET_SELF_AS_PARENT");
        // duyệt lên root để kiểm tra
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
    
    private String normalize(String s) { 
        return s == null ? null : s.trim().toLowerCase(java.util.Locale.ROOT); 
    }

    private PageResponse<SkillDto> toPage(Page<Skill> page) {
        return PageResponse.<SkillDto>builder()
                .items(page.map(skillMapper::toDto).getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .total(page.getTotalElements())
                .build();
    }
}