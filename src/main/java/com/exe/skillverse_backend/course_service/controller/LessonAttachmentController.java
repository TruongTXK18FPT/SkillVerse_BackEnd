package com.exe.skillverse_backend.course_service.controller;

import com.exe.skillverse_backend.course_service.dto.attachmentdto.AddAttachmentRequest;
import com.exe.skillverse_backend.course_service.dto.attachmentdto.LessonAttachmentDTO;
import com.exe.skillverse_backend.course_service.service.LessonAttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for managing lesson attachments (PDFs, links, etc.)
 * Allows mentors to add supplementary materials to Reading lessons
 * (Coursera-like)
 */
@RestController
@RequestMapping("/api/lessons/{lessonId}/attachments")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Lesson Attachments", description = "Manage PDF and resource attachments for lessons")
public class LessonAttachmentController {

    private final LessonAttachmentService attachmentService;

    /**
     * Add attachment to lesson (uploaded file or external link)
     * POST /api/lessons/{lessonId}/attachments
     */
    @PostMapping
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    @Operation(summary = "Add attachment to lesson", description = "Add PDF, document, or external link to a Reading lesson")
    public ResponseEntity<LessonAttachmentDTO> addAttachment(
            @Parameter(description = "Lesson ID") @PathVariable @NotNull Long lessonId,
            @Parameter(description = "Attachment data") @Valid @RequestBody AddAttachmentRequest request,
            @Parameter(description = "Actor user ID") @RequestParam @NotNull Long actorId) {

        log.info("[API] POST /api/lessons/{}/attachments - actorId={}", lessonId, actorId);

        LessonAttachmentDTO attachment = attachmentService.addAttachment(lessonId, request, actorId);

        log.info("[API] Attachment added successfully: attachmentId={}", attachment.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(attachment);
    }

    /**
     * List all attachments for a lesson
     * GET /api/lessons/{lessonId}/attachments
     */
    @GetMapping
    @Operation(summary = "List all attachments for a lesson", description = "Get all PDFs, documents, and links attached to a lesson")
    public ResponseEntity<List<LessonAttachmentDTO>> listAttachments(
            @Parameter(description = "Lesson ID") @PathVariable @NotNull Long lessonId) {

        log.debug("[API] GET /api/lessons/{}/attachments", lessonId);

        List<LessonAttachmentDTO> attachments = attachmentService.listAttachments(lessonId);

        log.debug("[API] Returning {} attachments", attachments.size());
        return ResponseEntity.ok(attachments);
    }

    /**
     * Delete an attachment
     * DELETE /api/lessons/{lessonId}/attachments/{attachmentId}
     */
    @DeleteMapping("/{attachmentId}")
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    @Operation(summary = "Delete attachment", description = "Remove an attachment from a lesson")
    public ResponseEntity<Void> deleteAttachment(
            @Parameter(description = "Lesson ID") @PathVariable @NotNull Long lessonId,
            @Parameter(description = "Attachment ID") @PathVariable @NotNull Long attachmentId,
            @Parameter(description = "Actor user ID") @RequestParam @NotNull Long actorId) {

        log.info("[API] DELETE /api/lessons/{}/attachments/{} - actorId={}",
                lessonId, attachmentId, actorId);

        attachmentService.deleteAttachment(attachmentId, actorId);

        log.info("[API] Attachment deleted successfully: attachmentId={}", attachmentId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Reorder attachments
     * PUT /api/lessons/{lessonId}/attachments/reorder
     */
    @PutMapping("/reorder")
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    @Operation(summary = "Reorder attachments", description = "Change the display order of attachments")
    public ResponseEntity<Void> reorderAttachments(
            @Parameter(description = "Lesson ID") @PathVariable @NotNull Long lessonId,
            @Parameter(description = "Ordered list of attachment IDs") @RequestBody List<Long> attachmentIds,
            @Parameter(description = "Actor user ID") @RequestParam @NotNull Long actorId) {

        log.info("[API] PUT /api/lessons/{}/attachments/reorder - actorId={}, newOrder={}",
                lessonId, attachmentIds, actorId);

        attachmentService.reorderAttachments(lessonId, attachmentIds, actorId);

        log.info("[API] Attachments reordered successfully");
        return ResponseEntity.ok().build();
    }
}
