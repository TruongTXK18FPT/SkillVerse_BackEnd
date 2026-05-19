package com.exe.skillverse_backend.roadmap_package_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.exe.skillverse_backend.career_taxonomy_service.entity.JobPositionTrackSkill;
import com.exe.skillverse_backend.career_taxonomy_service.enums.RequirementType;
import com.exe.skillverse_backend.career_taxonomy_service.repository.JobPositionTrackSkillRepository;
import com.exe.skillverse_backend.roadmap_package_service.dto.request.RoadmapTemplateAutoGroupRequest;
import com.exe.skillverse_backend.roadmap_package_service.dto.request.RoadmapTemplateSkillBlockRequest;
import com.exe.skillverse_backend.roadmap_package_service.dto.response.RoadmapTemplateNodeGroupResponse;
import com.exe.skillverse_backend.shared.entity.Skill;
import com.exe.skillverse_backend.shared.repository.SkillRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoadmapSkillGroupingServiceTest {

    @Mock private JobPositionTrackSkillRepository trackSkillRepository;
    @Mock private SkillRepository skillRepository;

    @InjectMocks
    private RoadmapSkillGroupingService service;

    @Test
    void autoGroupCombinesRelatedSkillsIntoModuleNodes() {
        when(trackSkillRepository.findByTrackIdOrderBySortOrderAsc(30L)).thenReturn(List.of(
                trackSkill(1L, RequirementType.REQUIRED, 10, 1),
                trackSkill(2L, RequirementType.REQUIRED, 8, 2),
                trackSkill(3L, RequirementType.IMPORTANT, 5, 3),
                trackSkill(4L, RequirementType.REQUIRED, 7, 4)
        ));
        when(skillRepository.findAllById(any())).thenReturn(List.of(
                skill(1L, "Java", "java"),
                skill(2L, "OOP", "oop"),
                skill(3L, "Maven/Gradle", "maven-gradle"),
                skill(4L, "REST API", "rest-api")
        ));

        RoadmapTemplateAutoGroupRequest request = new RoadmapTemplateAutoGroupRequest();
        request.setJobPositionTrackId(30L);
        request.setTotalNodeCount(2);
        request.setSkillBlocks(List.of(block(1L, "Java"), block(2L, "OOP"), block(3L, "Maven/Gradle"), block(4L, "REST API")));

        List<RoadmapTemplateNodeGroupResponse> groups = service.autoGroup(request);

        assertThat(groups).hasSize(2);
        assertThat(groups)
                .anySatisfy(group -> {
                    assertThat(group.getTitle()).contains("Java");
                    assertThat(group.getSkills()).extracting(RoadmapTemplateNodeGroupResponse.SkillItem::getSkillId)
                            .contains(1L, 2L);
                });
        assertThat(groups).allSatisfy(group -> assertThat(group.getSkills()).isNotEmpty());
    }

    private JobPositionTrackSkill trackSkill(Long skillId, RequirementType requirementType, int weight, int sortOrder) {
        return JobPositionTrackSkill.builder()
                .trackId(30L)
                .skillId(skillId)
                .requirementType(requirementType)
                .weight(weight)
                .sortOrder(sortOrder)
                .build();
    }

    private Skill skill(Long id, String name, String canonicalKey) {
        return Skill.builder().id(id).name(name).canonicalKey(canonicalKey).build();
    }

    private RoadmapTemplateSkillBlockRequest block(Long skillId, String name) {
        RoadmapTemplateSkillBlockRequest block = new RoadmapTemplateSkillBlockRequest();
        block.setSkillId(skillId);
        block.setSkillNameSnapshot(name);
        block.setWeightPercent(10D);
        return block;
    }
}
