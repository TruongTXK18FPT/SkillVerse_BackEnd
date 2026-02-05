package com.exe.skillverse_backend.course_service.dto.curriculumdto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CurriculumUpsertResponseDTO {
    private List<ModuleUpsertDTO> modules;
}
