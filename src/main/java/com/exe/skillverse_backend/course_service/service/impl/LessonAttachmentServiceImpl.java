package com.exe.skillverse_backend.course_service.service.impl;

import com.exe.skillverse_backend.course_service.dto.attachmentdto.AddAttachmentRequest;
import com.exe.skillverse_backend.course_service.dto.attachmentdto.LessonAttachmentDTO;
import com.exe.skillverse_backend.course_service.entity.Lesson;
import com.exe.skillverse_backend.course_service.entity.LessonAttachment;
import com.exe.skillverse_backend.course_service.repository.LessonAttachmentRepository;
import com.exe.skillverse_backend.course_service.repository.LessonRepository;
import com.exe.skillverse_backend.course_service.service.LessonAttachmentService;
import com.exe.skillverse_backend.shared.entity.Media;
import com.exe.skillverse_backend.shared.exception.AccessDeniedException;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import com.exe.skillverse_backend.shared.repository.MediaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of LessonAttachmentService
 * Handles adding PDFs, documents, and external links to Reading lessons
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LessonAttachmentServiceImpl implements LessonAttachmentService {

    private final LessonAttachmentRepository attachmentRepository;
    private final LessonRepository lessonRepository;
    private final MediaRepository mediaRepository;

    @Override
    @Transactional
    public LessonAttachmentDTO addAttachment(Long lessonId, AddAttachmentRequest request, Long actorId) {
        log.info("[ATTACHMENT_ADD] Starting: lessonId={}, title='{}', type={}, actorId={}",
                lessonId, request.getTitle(), request.getType(), actorId);

        // Fetch lesson
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> {
                    log.error("[ATTACHMENT_ADD] Lesson not found: lessonId={}", lessonId);
                    return new NotFoundException("Lesson not found with id: " + lessonId);
                });

        log.debug("[ATTACHMENT_ADD] Lesson found: id={}, title='{}', type={}",
                lesson.getId(), lesson.getTitle(), lesson.getType());

        // Verify access (actor is course author or admin)
        verifyAccess(lesson, actorId);

        // Build attachment entity
        LessonAttachment attachment = LessonAttachment.builder()
                .lesson(lesson)
                .title(request.getTitle())
                .description(request.getDescription())
                .type(request.getType())
                .orderIndex(request.getOrderIndex())
                .build();

        // Handle uploaded file
        if (request.getMediaId() != null) {
            log.debug("[ATTACHMENT_ADD] Processing uploaded file: mediaId={}", request.getMediaId());
            Media media = mediaRepository.findById(request.getMediaId())
                    .orElseThrow(() -> {
                        log.error("[ATTACHMENT_ADD] Media not found: mediaId={}", request.getMediaId());
                        return new NotFoundException("Media not found with id: " + request.getMediaId());
                    });

            attachment.setMedia(media);
            attachment.setFileSize(media.getFileSize());
            log.debug("[ATTACHMENT_ADD] File attached: url={}, size={}bytes",
                    media.getUrl(), media.getFileSize());
        }

        // Handle external link
        if (request.getExternalUrl() != null && !request.getExternalUrl().isEmpty()) {
            log.debug("[ATTACHMENT_ADD] Processing external link: url={}", request.getExternalUrl());
            attachment.setExternalUrl(request.getExternalUrl());
        }

        // Validate: must have either media or external URL
        if (attachment.getMedia() == null &&
                (attachment.getExternalUrl() == null || attachment.getExternalUrl().isEmpty())) {
            log.error("[ATTACHMENT_ADD] Invalid request: no media or external URL provided");
            throw new IllegalArgumentException("Must provide either mediaId or externalUrl");
        }

        // Save attachment
        attachment = attachmentRepository.save(attachment);
        log.info("[ATTACHMENT_ADD] Attachment saved successfully: id={}, lessonId={}, title='{}'",
                attachment.getId(), lessonId, attachment.getTitle());

        return toDTO(attachment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LessonAttachmentDTO> listAttachments(Long lessonId) {
        log.debug("[ATTACHMENT_LIST] Fetching attachments for lessonId={}", lessonId);

        List<LessonAttachment> attachments = attachmentRepository
                .findByLessonIdOrderByOrderIndexAsc(lessonId);

        log.info("[ATTACHMENT_LIST] Found {} attachments for lessonId={}",
                attachments.size(), lessonId);

        return attachments.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteAttachment(Long attachmentId, Long actorId) {
        log.info("[ATTACHMENT_DELETE] Starting: attachmentId={}, actorId={}", attachmentId, actorId);

        LessonAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> {
                    log.error("[ATTACHMENT_DELETE] Attachment not found: attachmentId={}", attachmentId);
                    return new NotFoundException("Attachment not found with id: " + attachmentId);
                });

        log.debug("[ATTACHMENT_DELETE] Attachment found: id={}, title='{}', lessonId={}",
                attachment.getId(), attachment.getTitle(), attachment.getLesson().getId());

        // Verify access
        verifyAccess(attachment.getLesson(), actorId);

        attachmentRepository.delete(attachment);
        log.info("[ATTACHMENT_DELETE] Attachment deleted successfully: attachmentId={}", attachmentId);
    }

    @Override
    @Transactional
    public void reorderAttachments(Long lessonId, List<Long> attachmentIds, Long actorId) {
        log.info("[ATTACHMENT_REORDER] Starting: lessonId={}, newOrder={}, actorId={}",
                lessonId, attachmentIds, actorId);

        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new NotFoundException("Lesson not found with id: " + lessonId));

        verifyAccess(lesson, actorId);

        // Update order index for each attachment
        for (int i = 0; i < attachmentIds.size(); i++) {
            Long attachmentId = attachmentIds.get(i);
            LessonAttachment attachment = attachmentRepository.findById(attachmentId)
                    .orElseThrow(() -> new NotFoundException("Attachment not found with id: " + attachmentId));

            attachment.setOrderIndex(i);
            attachmentRepository.save(attachment);
            log.debug("[ATTACHMENT_REORDER] Updated: attachmentId={}, newIndex={}", attachmentId, i);
        }

        log.info("[ATTACHMENT_REORDER] Reorder completed successfully for lessonId={}", lessonId);
    }

    /**
     * Verify that actor has access to modify lesson attachments
     * Actor must be course author or admin
     */
    private void verifyAccess(Lesson lesson, Long actorId) {
        Long courseAuthorId = lesson.getModule().getCourse().getAuthor().getId();

        if (!courseAuthorId.equals(actorId)) {
            log.warn("[ATTACHMENT_ACCESS_DENIED] Actor {} attempted to modify lesson {} (author: {})",
                    actorId, lesson.getId(), courseAuthorId);
            throw new AccessDeniedException(
                    "Only course author can modify lesson attachments");
        }

        log.debug("[ATTACHMENT_ACCESS_OK] Access granted for actorId={} on lessonId={}",
                actorId, lesson.getId());
    }

    /**
     * Convert entity to DTO
     */
    private LessonAttachmentDTO toDTO(LessonAttachment attachment) {
        return LessonAttachmentDTO.builder()
                .id(attachment.getId())
                .title(attachment.getTitle())
                .description(attachment.getDescription())
                .downloadUrl(attachment.getDownloadUrl())
                .type(attachment.getType())
                .fileSize(attachment.getFileSize())
                .fileSizeFormatted(attachment.getFormattedFileSize())
                .orderIndex(attachment.getOrderIndex())
                .createdAt(attachment.getCreatedAt() != null ? attachment.getCreatedAt().toString() : null)
                .build();
    }
}
