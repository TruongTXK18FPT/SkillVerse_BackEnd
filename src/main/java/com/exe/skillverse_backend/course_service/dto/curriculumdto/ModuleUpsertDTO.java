package com.exe.skillverse_backend.course_service.dto.curriculumdto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModuleUpsertDTO {
    private Long id;
    private String clientId;
    private String title;
    private String description;
    private Integer orderIndex;
    private List<CurriculumItemUpsertDTO> items;
}
