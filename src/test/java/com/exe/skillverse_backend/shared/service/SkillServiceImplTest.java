package com.exe.skillverse_backend.shared.service;

import com.exe.skillverse_backend.shared.dto.SkillDto;
import com.exe.skillverse_backend.shared.entity.Skill;
import com.exe.skillverse_backend.shared.exception.BadRequestException;
import com.exe.skillverse_backend.shared.exception.ConflictException;
import com.exe.skillverse_backend.shared.mapper.SkillMapper;
import com.exe.skillverse_backend.shared.repository.SkillRepository;
import com.exe.skillverse_backend.shared.service.impl.SkillServiceImpl;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SkillServiceImplTest {

    @Mock
    private SkillRepository skillRepository;

    @Mock
    private SkillMapper skillMapper;

    private SkillServiceImpl service;

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-04-03T10:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        service = new SkillServiceImpl(skillRepository, skillMapper, fixedClock);
        lenient().when(skillRepository.save(any(Skill.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("create should reject duplicate name and category combinations")
    void create_ShouldRejectDuplicateNameAndCategoryCombinations() {
        SkillDto dto = SkillDto.builder().name("Spring Boot").category("Backend").build();
        // Service normalizes to UPPERCASE before checking uniqueness
        when(skillRepository.findByNameIgnoreCaseAndCategoryIgnoreCase("SPRING_BOOT", "Backend"))
                .thenReturn(Optional.of(skill(10L, null, null, null)));

        assertThrows(ConflictException.class, () -> service.create(dto));
        verify(skillMapper, never()).toEntity(dto);
    }

    @Test
    @DisplayName("create should stamp timestamps and resolve the parent skill")
    void create_ShouldStampTimestampsAndResolveParentSkill() {
        SkillDto dto = SkillDto.builder()
                .name("Spring Security")
                .category("Backend")
                .parentSkillId(1L)
                .build();
        Skill parent = skill(1L, "SPRING", "Backend", null);
        Skill entity = skill(null, "SPRING SECURITY", "Backend", null);

        // Service normalizes to UPPERCASE before uniqueness check and persistence
        when(skillRepository.findByNameIgnoreCaseAndCategoryIgnoreCase("SPRING_SECURITY", "Backend"))
                .thenReturn(Optional.empty());
        when(skillRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(skillMapper.toEntity(dto)).thenReturn(entity);
        when(skillMapper.toDto(any(Skill.class))).thenReturn(SkillDto.builder()
                .id(2L)
                .name("SPRING SECURITY")
                .parentSkillId(1L)
                .createdAt(LocalDateTime.now(fixedClock))
                .updatedAt(LocalDateTime.now(fixedClock))
                .build());

        SkillDto response = service.create(dto);

        assertEquals(2L, response.getId());
        assertEquals(1L, response.getParentSkillId());
        assertEquals("SPRING SECURITY", response.getName());
    }

    @Test
    @DisplayName("update should reject cyclic reparenting")
    void update_ShouldRejectCyclicReparenting() {
        Skill current = skill(10L, "Java", "Backend", 1L);
        Skill newParent = skill(20L, "Spring", "Backend", 10L);
        SkillDto dto = SkillDto.builder().name("Java").category("Backend").parentSkillId(20L).build();

        when(skillRepository.findById(10L)).thenReturn(Optional.of(current));
        when(skillRepository.findById(20L)).thenReturn(Optional.of(newParent));

        assertThrows(BadRequestException.class, () -> service.update(10L, dto));
    }

    @Test
    @DisplayName("delete should block skills that still have children")
    void delete_ShouldBlockSkillsThatStillHaveChildren() {
        Skill skill = skill(30L, "Child", "Backend", null);
        when(skillRepository.findById(30L)).thenReturn(Optional.of(skill));
        when(skillRepository.countByParentSkillId(30L)).thenReturn(2L);

        assertThrows(ConflictException.class, () -> service.delete(30L));
        verify(skillRepository, never()).delete(skill);
    }

    @Test
    @DisplayName("pathToRoot should return the node chain from child to root")
    void pathToRoot_ShouldReturnNodeChainFromChildToRoot() {
        Skill child = skill(3L, "Child", "Backend", 2L);
        Skill parent = skill(2L, "Parent", "Backend", 1L);
        Skill root = skill(1L, "Root", "Backend", null);

        when(skillRepository.findById(3L)).thenReturn(Optional.of(child));
        when(skillRepository.findById(2L)).thenReturn(Optional.of(parent));
        when(skillRepository.findById(1L)).thenReturn(Optional.of(root));

        List<Long> path = service.pathToRoot(3L);

        assertEquals(List.of(3L, 2L, 1L), path);
    }

    private Skill skill(Long id, String name, String category, Long parentSkillId) {
        Skill skill = new Skill();
        skill.setId(id);
        skill.setName(name);
        skill.setCategory(category);
        skill.setParentSkillId(parentSkillId);
        return skill;
    }
}
