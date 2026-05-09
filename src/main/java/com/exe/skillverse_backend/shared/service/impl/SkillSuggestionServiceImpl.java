package com.exe.skillverse_backend.shared.service.impl;

import com.exe.skillverse_backend.shared.dto.PageResponse;
import com.exe.skillverse_backend.shared.dto.SkillDto;
import com.exe.skillverse_backend.shared.dto.SkillSuggestionDto;
import com.exe.skillverse_backend.shared.entity.Skill;
import com.exe.skillverse_backend.shared.entity.SkillSuggestion;
import com.exe.skillverse_backend.shared.enums.SkillStatus;
import com.exe.skillverse_backend.shared.enums.SkillSuggestionStatus;
import com.exe.skillverse_backend.shared.exception.BadRequestException;
import com.exe.skillverse_backend.shared.exception.ConflictException;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import com.exe.skillverse_backend.shared.mapper.SkillSuggestionMapper;
import com.exe.skillverse_backend.shared.repository.SkillRepository;
import com.exe.skillverse_backend.shared.repository.SkillSuggestionRepository;
import com.exe.skillverse_backend.shared.service.SkillService;
import com.exe.skillverse_backend.shared.service.SkillSuggestionService;
import com.exe.skillverse_backend.shared.util.SkillNameUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SkillSuggestionServiceImpl implements SkillSuggestionService {

    private final SkillSuggestionRepository skillSuggestionRepository;
    private final SkillRepository skillRepository;
    private final SkillSuggestionMapper skillSuggestionMapper;
    private final SkillService skillService;

    @Override
    @Transactional
    public SkillSuggestionDto createSuggestion(SkillSuggestionDto dto, Long userId) {
        if (dto.getSuggestedName() == null || dto.getSuggestedName().trim().isEmpty()) {
            throw new BadRequestException("SUGGESTED_NAME_REQUIRED");
        }

        String canonicalKey = SkillNameUtils.normalize(dto.getSuggestedName());
        
        // 1. Check if the skill already exists in main registry
        Optional<Skill> existingSkill = skillRepository.findByCanonicalKey(canonicalKey);
        if (existingSkill.isPresent()) {
            throw new ConflictException("SKILL_ALREADY_EXISTS"); // Suggestion not needed
        }

        // 2. Check if a pending suggestion already exists
        Optional<SkillSuggestion> existingSuggestion = skillSuggestionRepository
                .findBySuggestedCanonicalKeyAndStatus(canonicalKey, SkillSuggestionStatus.PENDING);
        if (existingSuggestion.isPresent()) {
            throw new ConflictException("SUGGESTION_ALREADY_PENDING");
        }

        SkillSuggestion suggestion = new SkillSuggestion();
        suggestion.setSuggestedName(dto.getSuggestedName());
        suggestion.setSuggestedCanonicalKey(canonicalKey);
        suggestion.setDescription(dto.getDescription());
        suggestion.setSourceUserId(userId);
        suggestion.setStatus(SkillSuggestionStatus.PENDING);

        SkillSuggestion saved = skillSuggestionRepository.save(suggestion);
        log.info("Created skill suggestion: id={}, canonicalKey={}", saved.getId(), canonicalKey);
        
        return skillSuggestionMapper.toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<SkillSuggestionDto> listPending(Pageable pageable) {
        Page<SkillSuggestion> page = skillSuggestionRepository.findByStatus(SkillSuggestionStatus.PENDING, pageable);
        return PageResponse.<SkillSuggestionDto>builder()
                .items(skillSuggestionMapper.toDtos(page.getContent()))
                .page(page.getNumber())
                .size(page.getSize())
                .total(page.getTotalElements())
                .build();
    }

    @Override
    @Transactional
    public SkillSuggestionDto approve(Long id, Long adminId) {
        SkillSuggestion suggestion = getOrThrow(id);
        
        if (suggestion.getStatus() != SkillSuggestionStatus.PENDING) {
            throw new BadRequestException("SUGGESTION_NOT_PENDING");
        }

        // Check if skill already exists now (maybe created by another admin in the meantime)
        Optional<Skill> existingSkill = skillRepository.findByCanonicalKey(suggestion.getSuggestedCanonicalKey());
        
        if (existingSkill.isPresent()) {
            // Auto merge if exists
            return performMerge(suggestion, existingSkill.get(), adminId, "Auto-merged during approval because skill exists.");
        }

        // Create new skill with admin audit info
        SkillDto newSkillDto = new SkillDto();
        newSkillDto.setName(suggestion.getSuggestedName());
        newSkillDto.setDescription(suggestion.getDescription());
        // Parent would be set by admin later if needed; MVP just creates a root skill
        
        SkillDto createdSkill = skillService.createApproved(newSkillDto, adminId);
        
        suggestion.setStatus(SkillSuggestionStatus.APPROVED_CREATED);
        suggestion.setMatchedSkillId(createdSkill.getId());
        suggestion.setReviewedBy(adminId);
        suggestion.setReviewedAt(LocalDateTime.now());
        
        SkillSuggestion saved = skillSuggestionRepository.save(suggestion);
        log.info("Approved skill suggestion: id={}, created skillId={}", saved.getId(), createdSkill.getId());
        
        return skillSuggestionMapper.toDto(saved);
    }

    @Override
    @Transactional
    public SkillSuggestionDto merge(Long id, Long matchedSkillId, Long adminId) {
        SkillSuggestion suggestion = getOrThrow(id);
        
        if (suggestion.getStatus() != SkillSuggestionStatus.PENDING) {
            throw new BadRequestException("SUGGESTION_NOT_PENDING");
        }

        Skill targetSkill = skillRepository.findById(matchedSkillId)
                .orElseThrow(() -> new NotFoundException("TARGET_SKILL_NOT_FOUND"));
                
        if (targetSkill.getStatus() != SkillStatus.ACTIVE) {
            throw new BadRequestException("TARGET_SKILL_NOT_ACTIVE");
        }

        return performMerge(suggestion, targetSkill, adminId, "Manually merged by admin.");
    }

    @Override
    @Transactional
    public SkillSuggestionDto reject(Long id, String reviewNote, Long adminId) {
        SkillSuggestion suggestion = getOrThrow(id);
        
        if (suggestion.getStatus() != SkillSuggestionStatus.PENDING) {
            throw new BadRequestException("SUGGESTION_NOT_PENDING");
        }

        suggestion.setStatus(SkillSuggestionStatus.REJECTED);
        suggestion.setReviewNote(reviewNote);
        suggestion.setReviewedBy(adminId);
        suggestion.setReviewedAt(LocalDateTime.now());
        
        SkillSuggestion saved = skillSuggestionRepository.save(suggestion);
        log.info("Rejected skill suggestion: id={}", saved.getId());
        
        return skillSuggestionMapper.toDto(saved);
    }
    
    private SkillSuggestionDto performMerge(SkillSuggestion suggestion, Skill targetSkill, Long adminId, String note) {
        suggestion.setStatus(SkillSuggestionStatus.MERGED_TO_EXISTING);
        suggestion.setMatchedSkillId(targetSkill.getId());
        suggestion.setReviewNote(note);
        suggestion.setReviewedBy(adminId);
        suggestion.setReviewedAt(LocalDateTime.now());
        
        SkillSuggestion saved = skillSuggestionRepository.save(suggestion);
        log.info("Merged skill suggestion: id={}, target skillId={}", saved.getId(), targetSkill.getId());
        
        return skillSuggestionMapper.toDto(saved);
    }

    private SkillSuggestion getOrThrow(Long id) {
        return skillSuggestionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("SUGGESTION_NOT_FOUND"));
    }
}
