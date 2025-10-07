package com.exe.skillverse_backend.course_service.dto.moduledto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModuleSummaryDTO {
  private Long id;
  private String title;
  private String description;
  private Integer orderIndex;
}


