package com.exe.skillverse_backend.course_service.dto.moduledto;

import java.time.Instant;
import java.util.List;

import com.exe.skillverse_backend.course_service.dto.lessondto.LessonBriefDTO;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModuleDetailDTO {
  private Long id;
  private String title;
  private String description;
  private Integer orderIndex;
  private Instant createdAt;
  private Instant updatedAt;
  private List<LessonBriefDTO> lessons;
}


