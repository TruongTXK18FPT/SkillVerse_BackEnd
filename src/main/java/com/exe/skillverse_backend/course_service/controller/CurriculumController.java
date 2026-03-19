package com.exe.skillverse_backend.course_service.controller;

import com.exe.skillverse_backend.course_service.dto.curriculumdto.CurriculumUpsertRequestDTO;
import com.exe.skillverse_backend.course_service.dto.curriculumdto.CurriculumUpsertResponseDTO;
import com.exe.skillverse_backend.course_service.service.CurriculumService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Validated
@Tag(name = "Curriculum", description = "APIs for bulk upsert curriculum")
public class CurriculumController {

    private final CurriculumService curriculumService;

    @PutMapping("/courses/{courseId}/curriculum")
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    @Operation(summary = "Bulk upsert curriculum for a course")
    public ResponseEntity<CurriculumUpsertResponseDTO> upsertCurriculum(
            @PathVariable @NotNull Long courseId,
            @RequestParam @NotNull Long actorId,
            @Valid @RequestBody CurriculumUpsertRequestDTO request) {
        CurriculumUpsertResponseDTO response = curriculumService.upsertCurriculum(courseId, request, actorId);
        return ResponseEntity.ok(response);
    }
}
