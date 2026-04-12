package com.exe.skillverse_backend.course_service.dto.lessondto;

import com.exe.skillverse_backend.course_service.dto.attachmentdto.LessonAttachmentDTO;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LessonDetailDTO {
    private Long id;
    private String title;
    private String type;
    private Integer orderIndex;
    private Integer durationSec;
    private String contentText;
    private String resourceUrl;
    private String videoUrl;
    private Long videoMediaId;
    private List<LessonAttachmentDTO> attachments;
}


