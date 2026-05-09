package com.exe.skillverse_backend.shared.repository;

import com.exe.skillverse_backend.shared.entity.SkillSuggestion;
import com.exe.skillverse_backend.shared.enums.SkillSuggestionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SkillSuggestionRepository extends JpaRepository<SkillSuggestion, Long> {

    Optional<SkillSuggestion> findBySuggestedCanonicalKeyAndStatus(String canonicalKey, SkillSuggestionStatus status);

    Page<SkillSuggestion> findByStatus(SkillSuggestionStatus status, Pageable pageable);

    List<SkillSuggestion> findBySourceUserId(Long userId);
}
