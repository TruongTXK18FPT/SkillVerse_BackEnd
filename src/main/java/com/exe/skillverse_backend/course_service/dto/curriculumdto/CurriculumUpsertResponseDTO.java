package com.exe.skillverse_backend.course_service.dto.curriculumdto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CurriculumUpsertResponseDTO {
    private List<ModuleUpsertDTO> modules;
}
