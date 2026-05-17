package com.exe.skillverse_backend.question_bank_service.service;

import com.exe.skillverse_backend.ai_service.entity.ExpertPromptConfig;
import com.exe.skillverse_backend.ai_service.repository.ExpertPromptConfigRepository;
import com.exe.skillverse_backend.question_bank_service.repository.QuestionBankRepository;
import com.exe.skillverse_backend.question_bank_service.service.impl.SkillResolveServiceImpl;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SkillResolveServiceImplTest {

    @Mock
    private ExpertPromptConfigRepository expertPromptConfigRepository;

    @Mock
    private QuestionBankRepository questionBankRepository;

    @Mock
    private QuestionBankService questionBankService;

    private SkillResolveServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SkillResolveServiceImpl(
                expertPromptConfigRepository,
                questionBankRepository,
                questionBankService
        );
        when(expertPromptConfigRepository.findByIsActiveTrueOrderByDomainAscIndustryAscJobRoleAsc())
                .thenReturn(List.of(
                        config("Information Technology", "Software Development", "Backend Developer",
                                "backend, api, server, database"),
                        config("Information Technology", "Software Development", "Frontend Developer",
                                "frontend, react, vue, angular"),
                        config("Information Technology", "Software Development", "UI/UX Designer",
                                "ui, ux, design, figma"),
                        config("Business", "Marketing", "SEO Specialist",
                                "seo, onpage, offpage, keyword")
                ));
        when(questionBankRepository.findByDomainAndIsActiveTrue(any()))
                .thenReturn(List.of());
    }

    @Test
    @DisplayName("resolveSkill should match React to Frontend Developer without AI")
    void resolveSkill_ShouldMatchReactToFrontendDeveloper() {
        var response = service.resolveSkill("reakt js");

        assertEquals("Frontend Developer", response.getJobRole());
        assertEquals("Information Technology", response.getDomain());
        assertTrue(response.getConfidence() >= 60);
    }

    @Test
    @DisplayName("resolveSkill should map Spring Boot Java to Backend Developer")
    void resolveSkill_ShouldMatchSpringBootJavaToBackendDeveloper() {
        var response = service.resolveSkill("java sprng boot");

        assertEquals("Backend Developer", response.getJobRole());
        assertEquals("Software Development", response.getIndustry());
        assertTrue(response.getConfidence() >= 60);
    }

    @Test
    @DisplayName("resolveSkill should normalize skill name before question bank lookup")
    void resolveSkill_ShouldNormalizeSkillNameBeforeLookup() {
        service.resolveSkill("Java Spring Boot");

        org.mockito.Mockito.verify(questionBankRepository).findByDomainAndIsActiveTrue(
                eq("Information Technology")
        );
    }

    private ExpertPromptConfig config(String domain, String industry, String jobRole, String keywords) {
        return ExpertPromptConfig.builder()
                .domain(domain)
                .industry(industry)
                .jobRole(jobRole)
                .keywords(keywords)
                .systemPrompt("prompt")
                .isActive(true)
                .build();
    }
}
