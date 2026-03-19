package com.exe.skillverse_backend.course_service.dto.curriculumdto;

import com.exe.skillverse_backend.course_service.dto.assignmentdto.AssignmentCriteriaDTO;
import com.exe.skillverse_backend.course_service.dto.quizdto.QuizQuestionCreateDTO;
import com.exe.skillverse_backend.course_service.entity.enums.LessonType;
import com.exe.skillverse_backend.course_service.entity.enums.QuizGradingMethod;
import com.exe.skillverse_backend.course_service.entity.enums.SubmissionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CurriculumItemUpsertDTO {
    private Long id;
    private String clientId;
    private CurriculumItemType type;
    private String title;
    private String description;
    private Integer orderIndex;

    // Lesson fields
    private LessonType lessonType;
    private Integer durationSec;
    private String contentText;
    private String resourceUrl;
    private String videoUrl;

    // Quiz fields
    private Integer passScore;
    private Integer maxAttempts;
    private Integer timeLimitMinutes;
    private Integer roundingIncrement;
    private QuizGradingMethod gradingMethod;
    private Boolean isAssessment;
    private Integer cooldownHours;
    private List<QuizQuestionCreateDTO> questions;

    // Assignment fields
    private SubmissionType submissionType;
    private BigDecimal maxScore;
    private BigDecimal passingScore;
    private Instant dueAt;
    private List<AssignmentCriteriaDTO> criteria;
}
