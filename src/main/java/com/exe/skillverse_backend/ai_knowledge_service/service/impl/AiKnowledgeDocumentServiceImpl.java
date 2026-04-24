package com.exe.skillverse_backend.ai_knowledge_service.service.impl;

import com.exe.skillverse_backend.ai_knowledge_service.dto.request.AdminChatbotKnowledgeUploadRequest;
import com.exe.skillverse_backend.ai_knowledge_service.dto.request.AdminRoadmapKnowledgeUploadRequest;
import com.exe.skillverse_backend.ai_knowledge_service.dto.request.MentorRoadmapKnowledgeSubmissionRequest;
import com.exe.skillverse_backend.ai_knowledge_service.dto.request.ReviewAiKnowledgeRequest;
import com.exe.skillverse_backend.ai_knowledge_service.dto.response.AiKnowledgeDocumentDetailResponse;
import com.exe.skillverse_backend.ai_knowledge_service.dto.response.AiKnowledgeDocumentListItemResponse;
import com.exe.skillverse_backend.ai_knowledge_service.entity.AiKnowledgeDocument;
import com.exe.skillverse_backend.ai_knowledge_service.entity.enums.AiKnowledgeApprovalStatus;
import com.exe.skillverse_backend.ai_knowledge_service.entity.enums.AiKnowledgeIngestionStatus;
import com.exe.skillverse_backend.ai_knowledge_service.entity.enums.AiKnowledgeUseCase;
import com.exe.skillverse_backend.ai_knowledge_service.event.AiKnowledgeRagSyncEvent;
import com.exe.skillverse_backend.ai_knowledge_service.mapper.AiKnowledgeDocumentMapper;
import com.exe.skillverse_backend.ai_knowledge_service.repository.AiKnowledgeDocumentRepository;
import com.exe.skillverse_backend.ai_knowledge_service.service.AiKnowledgeAuthorizationService;
import com.exe.skillverse_backend.ai_knowledge_service.service.AiKnowledgeDocumentService;
import com.exe.skillverse_backend.ai_knowledge_service.service.AiKnowledgeFolderBuilder;
import com.exe.skillverse_backend.ai_knowledge_service.service.AiKnowledgeTextExtractor;
import com.exe.skillverse_backend.ai_knowledge_service.util.AiKnowledgeDocTypeResolver;
import com.exe.skillverse_backend.ai_knowledge_service.util.AiKnowledgeSlugUtils;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.shared.dto.MediaDTO;
import com.exe.skillverse_backend.shared.entity.Media;
import com.exe.skillverse_backend.shared.exception.ApiException;
import com.exe.skillverse_backend.shared.exception.ErrorCode;
import com.exe.skillverse_backend.shared.repository.MediaRepository;
import com.exe.skillverse_backend.shared.service.CloudinaryService;
import com.exe.skillverse_backend.shared.service.MediaService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class AiKnowledgeDocumentServiceImpl implements AiKnowledgeDocumentService {

    private static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024L;
    private static final Set<String> SUPPORTED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/plain",
            "text/markdown"
    );
    private static final Set<String> SUPPORTED_INDUSTRIES = Set.of(
            "IT",
            "Business",
            "Finance",
            "Marketing",
            "Design",
            "Education",
            "Healthcare",
            "Logistics",
            "Legal",
            "Public Administration",
            "Agriculture",
            "Service Hospitality",
            "Arts Entertainment"
    );
    private static final Set<String> SUPPORTED_LEVELS = Set.of(
            "beginner",
            "intermediate",
            "advanced"
    );

    private final AiKnowledgeDocumentRepository documentRepository;
    private final AiKnowledgeAuthorizationService authorizationService;
    private final AiKnowledgeFolderBuilder folderBuilder;
    private final AiKnowledgeTextExtractor textExtractor;
    private final AiKnowledgeDocumentMapper documentMapper;
    private final MediaService mediaService;
    private final CloudinaryService cloudinaryService;
    private final MediaRepository mediaRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public AiKnowledgeDocumentDetailResponse uploadAdminChatbotDocument(User admin, AdminChatbotKnowledgeUploadRequest request) {
        AiKnowledgeDocument document = createDocument(
                admin,
                request.getFile(),
                request.getTitle(),
                request.getDescription(),
                request.getIndustry(),
                request.getLevel(),
                AiKnowledgeUseCase.CHATBOT_GLOBAL,
                AiKnowledgeApprovalStatus.APPROVED,
                null,
                null,
                null,
                null,
                null,
                null,
                true
        );
        return documentMapper.toDetail(document);
    }

    @Override
    @Transactional
    public AiKnowledgeDocumentDetailResponse uploadAdminRoadmapDocument(User admin, AdminRoadmapKnowledgeUploadRequest request) {
        String skillSlug = requireSkillSlug(request.getSkillName());
        AiKnowledgeDocument document = createDocument(
                admin,
                request.getFile(),
                request.getTitle(),
                request.getDescription(),
                request.getIndustry(),
                request.getLevel(),
                AiKnowledgeUseCase.ROADMAP_SKILL,
                AiKnowledgeApprovalStatus.APPROVED,
                null,
                request.getSkillName(),
                skillSlug,
                null,
                null,
                null,
                true
        );
        return documentMapper.toDetail(document);
    }

    @Override
    @Transactional
    public AiKnowledgeDocumentDetailResponse submitMentorRoadmapDocument(User mentor, MentorRoadmapKnowledgeSubmissionRequest request) {
        authorizationService.assertMentorVerifiedSkill(mentor, request.getSkillName());
        String skillSlug = requireSkillSlug(request.getSkillName());
        AiKnowledgeDocument document = createDocument(
                mentor,
                request.getFile(),
                request.getTitle(),
                request.getDescription(),
                request.getIndustry(),
                request.getLevel(),
                AiKnowledgeUseCase.ROADMAP_SKILL,
                AiKnowledgeApprovalStatus.PENDING,
                mentor.getId(),
                request.getSkillName(),
                skillSlug,
                null,
                null,
                null,
                false
        );
        return documentMapper.toDetail(document);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AiKnowledgeDocumentListItemResponse> listAdminDocuments(
            AiKnowledgeUseCase useCase,
            AiKnowledgeApprovalStatus approvalStatus,
            AiKnowledgeIngestionStatus ingestionStatus,
            String skillSlug,
            Long courseId,
            Long moduleId,
            Long assignmentId,
            Pageable pageable) {
        return documentRepository.findAllActiveWithFilters(
                useCase,
                approvalStatus,
                ingestionStatus,
                skillSlug,
                courseId,
                moduleId,
                assignmentId,
                pageable
        ).map(documentMapper::toListItem);
    }

    @Override
    @Transactional(readOnly = true)
    public AiKnowledgeDocumentDetailResponse getAdminDocumentDetail(Long id) {
        return documentMapper.toDetail(getActiveDocument(id));
    }

    @Override
    @Transactional(readOnly = true)
    public DownloadedAiKnowledgeDocument downloadAdminDocument(Long id) {
        return buildDownload(getActiveDocument(id));
    }

    @Override
    @Transactional
    public AiKnowledgeDocumentDetailResponse reviewDocument(Long id, User admin, ReviewAiKnowledgeRequest request) {
        AiKnowledgeDocument document = getActiveDocument(id);

        if (document.getApprovalStatus() != AiKnowledgeApprovalStatus.PENDING) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Only pending documents can be reviewed");
        }
        if (document.getMentorId() == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Only mentor submissions can be reviewed");
        }

        document.setReviewNote(normalize(request.getReviewNote()));

        if (Boolean.TRUE.equals(request.getApproved())) {
            moveDocumentToApprovedFolder(document);
            document.setApprovalStatus(AiKnowledgeApprovalStatus.APPROVED);
            document.setApprovedByUserId(admin.getId());
            document.setApprovedAt(LocalDateTime.now());
            documentRepository.save(document);
            publishRagSync(document.getId(), AiKnowledgeRagSyncEvent.Action.INGEST);
            return documentMapper.toDetail(document);
        }

        document.setApprovalStatus(AiKnowledgeApprovalStatus.REJECTED);
        if (document.getIngestionStatus() != AiKnowledgeIngestionStatus.FAILED) {
            document.setIngestionStatus(AiKnowledgeIngestionStatus.NOT_INGESTED);
        }
        documentRepository.save(document);
        return documentMapper.toDetail(document);
    }

    @Override
    @Transactional
    public AiKnowledgeDocumentDetailResponse reindexDocument(Long id) {
        AiKnowledgeDocument document = getActiveDocument(id);
        if (document.getApprovalStatus() != AiKnowledgeApprovalStatus.APPROVED) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Only approved documents can be reindexed");
        }
        publishRagSync(document.getId(), AiKnowledgeRagSyncEvent.Action.REINDEX);
        return documentMapper.toDetail(document);
    }

    @Override
    @Transactional
    public void archiveDocument(Long id) {
        AiKnowledgeDocument document = getActiveDocument(id);
        if (document.getIngestionStatus() == AiKnowledgeIngestionStatus.INDEXED) {
            publishRagSync(document.getId(), AiKnowledgeRagSyncEvent.Action.DELETE_FROM_RAG);
        }
        document.setArchivedAt(LocalDateTime.now());
        documentRepository.save(document);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AiKnowledgeDocumentListItemResponse> listMentorDocuments(User mentor, Pageable pageable) {
        return documentRepository.findByMentorIdAndArchivedAtIsNullOrderByCreatedAtDesc(mentor.getId(), pageable)
                .map(documentMapper::toListItem);
    }

    @Override
    @Transactional(readOnly = true)
    public AiKnowledgeDocumentDetailResponse getMentorDocumentDetail(User mentor, Long id) {
        AiKnowledgeDocument document = getActiveDocument(id);
        authorizationService.assertMentorCanAccessDocument(mentor, document);
        return documentMapper.toDetail(document);
    }

    @Override
    @Transactional(readOnly = true)
    public DownloadedAiKnowledgeDocument downloadMentorDocument(User mentor, Long id) {
        AiKnowledgeDocument document = getActiveDocument(id);
        authorizationService.assertMentorCanAccessDocument(mentor, document);
        return buildDownload(document);
    }

    @Override
    @Transactional
    public void deleteMentorPendingSubmission(User mentor, Long id) {
        AiKnowledgeDocument document = getActiveDocument(id);
        authorizationService.assertMentorCanAccessDocument(mentor, document);
        if (document.getApprovalStatus() != AiKnowledgeApprovalStatus.PENDING) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Only pending submissions can be deleted by mentor");
        }
        document.setArchivedAt(LocalDateTime.now());
        documentRepository.save(document);
    }

    private AiKnowledgeDocument createDocument(
            User actor,
            MultipartFile file,
            String title,
            String description,
            String industry,
            String level,
            AiKnowledgeUseCase useCase,
            AiKnowledgeApprovalStatus approvalStatus,
            Long mentorId,
            String skillName,
            String skillSlug,
            Long courseId,
            Long moduleId,
            Long assignmentId,
            boolean adminDirectUpload) {
        String resolvedMimeType = validateKnowledgeFile(file);
        MultipartFile normalizedFile = prepareFile(file, resolvedMimeType);

        String folder = folderBuilder.buildFolder(
                useCase,
                mentorId,
                skillSlug,
                courseId,
                moduleId,
                assignmentId,
                !adminDirectUpload
        );

        Media media = uploadMedia(normalizedFile, actor.getId(), folder);
        ExtractionResult extractionResult = extractText(media, normalizedFile.getContentType());
        String normalizedIndustry = normalizeKnowledgeIndustry(industry);
        String normalizedLevel = normalizeKnowledgeLevel(level);

        AiKnowledgeDocument document = AiKnowledgeDocument.builder()
                .mediaId(media.getId())
                .title(normalizeRequired(title, "Title is required"))
                .description(normalize(description))
                .useCase(useCase)
                .approvalStatus(approvalStatus)
                .ingestionStatus(extractionResult.ingestionStatus())
                .uploadedByUserId(actor.getId())
                .mentorId(mentorId)
                .skillName(normalize(skillName))
                .skillSlug(normalize(skillSlug))
                .industry(normalizedIndustry)
                .level(normalizedLevel)
                .courseId(courseId)
                .moduleId(moduleId)
                .assignmentId(assignmentId)
                .docType(AiKnowledgeDocTypeResolver.resolve(useCase, null))
                .mimeType(normalizedFile.getContentType())
                .fileSizeBytes(normalizedFile.getSize())
                .originalFileName(requireOriginalFileName(normalizedFile))
                .storageFolder(folder)
                .storageUrl(media.getUrl())
                .extractedText(extractionResult.extractedText())
                .extractError(extractionResult.extractError())
                .approvedByUserId(adminDirectUpload ? actor.getId() : null)
                .approvedAt(adminDirectUpload ? LocalDateTime.now() : null)
                .build();

        document = documentRepository.save(document);

        if (adminDirectUpload) {
            publishRagSync(document.getId(), AiKnowledgeRagSyncEvent.Action.INGEST);
        }

        return document;
    }

    private String validateKnowledgeFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "File is required");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "File size must be less than or equal to 10MB");
        }
        String resolvedMimeType = resolveMimeTypeByContent(file);
        if (resolvedMimeType == null || !SUPPORTED_CONTENT_TYPES.contains(resolvedMimeType)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Only PDF, DOCX, TXT, and MD files are supported");
        }
        return resolvedMimeType;
    }

    private void moveDocumentToApprovedFolder(AiKnowledgeDocument document) {
        if (document.getMentorId() == null) {
            return;
        }

        Media media = mediaRepository.findById(document.getMediaId())
                .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR, "Media not found for AI knowledge document"));

        String approvedFolder = folderBuilder.buildFolder(
                document.getUseCase(),
                document.getMentorId(),
                document.getSkillSlug(),
                document.getCourseId(),
                document.getModuleId(),
                document.getAssignmentId(),
                false
        );

        if (approvedFolder.equals(document.getStorageFolder()) || media.getCloudinaryPublicId() == null) {
            document.setStorageFolder(approvedFolder);
            return;
        }

        String targetPublicId = buildTargetPublicId(
                media.getCloudinaryPublicId(),
                document.getStorageFolder(),
                approvedFolder
        );

        try {
            Map<String, Object> renameResult = cloudinaryService.renameFile(media.getCloudinaryPublicId(), targetPublicId, "raw");
            String nextPublicId = (String) renameResult.get("public_id");
            String nextUrl = (String) renameResult.get("secure_url");

            if (nextPublicId != null && !nextPublicId.isBlank()) {
                media.setCloudinaryPublicId(nextPublicId);
            }
            if (nextUrl != null && !nextUrl.isBlank()) {
                media.setUrl(nextUrl);
                document.setStorageUrl(nextUrl);
            }

            mediaRepository.save(media);
            document.setStorageFolder(approvedFolder);
        } catch (IOException e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Failed to move approved document to target folder");
        }
    }

    private String buildTargetPublicId(String currentPublicId, String currentFolder, String targetFolder) {
        int lastSlash = currentPublicId.lastIndexOf('/');
        if (lastSlash < 0) {
            return targetFolder + "/" + currentPublicId;
        }

        String fileName = currentPublicId.substring(lastSlash + 1);
        String currentPath = currentPublicId.substring(0, lastSlash);

        if (currentFolder != null && !currentFolder.isBlank() && currentPath.endsWith(currentFolder)) {
            String basePrefix = currentPath.substring(0, currentPath.length() - currentFolder.length());
            return basePrefix + targetFolder + "/" + fileName;
        }

        int firstSlash = currentPublicId.indexOf('/');
        if (firstSlash > 0) {
            String basePrefix = currentPublicId.substring(0, firstSlash);
            return basePrefix + "/" + targetFolder + "/" + fileName;
        }

        return targetFolder + "/" + fileName;
    }

    private Media uploadMedia(MultipartFile file, Long actorId, String folder) {
        try {
            MediaDTO mediaDTO = mediaService.uploadDocument(file, actorId, folder);
            return mediaRepository.findById(mediaDTO.getId())
                    .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR, "Uploaded media not found"));
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.BAD_REQUEST, e.getMessage());
        } catch (IOException e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Failed to upload knowledge document");
        }
    }

    private DownloadedAiKnowledgeDocument buildDownload(AiKnowledgeDocument document) {
        Media media = mediaRepository.findById(document.getMediaId())
                .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR, "Media not found for AI knowledge document"));

        if (media.getCloudinaryPublicId() == null || media.getCloudinaryPublicId().isBlank()) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Stored file is missing Cloudinary public ID");
        }

        String resourceType = media.getCloudinaryResourceType() != null && !media.getCloudinaryResourceType().isBlank()
                ? media.getCloudinaryResourceType()
                : "raw";

        try {
            byte[] bytes = cloudinaryService.fetchFile(media.getCloudinaryPublicId(), resourceType);
            String contentType = normalize(document.getMimeType());
            return new DownloadedAiKnowledgeDocument(
                    document.getOriginalFileName(),
                    contentType != null ? contentType : "application/octet-stream",
                    bytes
            );
        } catch (IOException e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Failed to download knowledge document");
        }
    }

    private ExtractionResult extractText(Media media, String mimeType) {
        try {
            String extractedText = normalize(textExtractor.extractText(media, mimeType));
            if (extractedText == null) {
                return new ExtractionResult(null, "Extracted text is empty", AiKnowledgeIngestionStatus.FAILED);
            }
            return new ExtractionResult(extractedText, null, AiKnowledgeIngestionStatus.NOT_INGESTED);
        } catch (Exception e) {
            return new ExtractionResult(null, e.getMessage(), AiKnowledgeIngestionStatus.FAILED);
        }
    }

    private AiKnowledgeDocument getActiveDocument(Long id) {
        return documentRepository.findByIdAndArchivedAtIsNull(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "AI knowledge document not found: " + id));
    }


    private String requireSkillSlug(String skillName) {
        String skillSlug = AiKnowledgeSlugUtils.toRoadmapSkillSlug(skillName);
        if (skillSlug == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Valid skill name is required");
        }
        return skillSlug;
    }

    private String requireOriginalFileName(MultipartFile file) {
        String originalFileName = normalize(file.getOriginalFilename());
        if (originalFileName == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Original file name is required");
        }
        return originalFileName;
    }

    private String resolveMimeTypeByContent(MultipartFile file) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Failed to read knowledge document");
        }

        if (isPdf(bytes)) {
            return "application/pdf";
        }
        if (isDocx(bytes)) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        return resolvePlainTextMimeType(file, bytes);
    }

    private boolean isPdf(byte[] bytes) {
        return bytes.length >= 5
                && bytes[0] == '%'
                && bytes[1] == 'P'
                && bytes[2] == 'D'
                && bytes[3] == 'F'
                && bytes[4] == '-';
    }

    private boolean isDocx(byte[] bytes) {
        if (bytes.length < 4 || bytes[0] != 'P' || bytes[1] != 'K') {
            return false;
        }

        boolean hasContentTypes = false;
        boolean hasWordDocument = false;

        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                String entryName = entry.getName();
                if ("[Content_Types].xml".equals(entryName)) {
                    hasContentTypes = true;
                }
                if ("word/document.xml".equals(entryName)) {
                    hasWordDocument = true;
                }
                if (hasContentTypes && hasWordDocument) {
                    return true;
                }
            }
        } catch (IOException e) {
            return false;
        }

        return false;
    }

    private String resolvePlainTextMimeType(MultipartFile file, byte[] bytes) {
        if (!isLikelyText(bytes)) {
            return null;
        }

        String originalFileName = requireOriginalFileName(file).toLowerCase();
        if (originalFileName.endsWith(".md")) {
            return "text/markdown";
        }
        if (originalFileName.endsWith(".txt")) {
            return "text/plain";
        }

        String contentType = file.getContentType();
        if (contentType == null) {
            return null;
        }

        String lower = contentType.toLowerCase();
        if (lower.startsWith("text/markdown") || lower.startsWith("text/x-markdown")) {
            return "text/markdown";
        }
        if (lower.startsWith("text/plain")) {
            return "text/plain";
        }
        return null;
    }

    private boolean isLikelyText(byte[] bytes) {
        if (bytes.length == 0) {
            return false;
        }

        int sampleLength = Math.min(bytes.length, 8192);
        byte[] sample = Arrays.copyOf(bytes, sampleLength);

        for (byte value : sample) {
            if (value == 0) {
                return false;
            }
        }

        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            decoder.decode(java.nio.ByteBuffer.wrap(stripUtf8Bom(sample)));
        } catch (CharacterCodingException e) {
            return false;
        }

        int suspiciousControlChars = 0;
        for (byte value : sample) {
            int unsigned = value & 0xFF;
            if (unsigned < 0x09 || (unsigned > 0x0D && unsigned < 0x20)) {
                suspiciousControlChars++;
            }
        }

        return suspiciousControlChars <= Math.max(2, sampleLength / 100);
    }

    private byte[] stripUtf8Bom(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF) {
            return Arrays.copyOfRange(bytes, 3, bytes.length);
        }
        return bytes;
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, message);
        }
        return normalized;
    }

    private String normalizeKnowledgeIndustry(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        if (!SUPPORTED_INDUSTRIES.contains(normalized)) {
            throw new ApiException(
                    ErrorCode.BAD_REQUEST,
                    "Invalid industry. Allowed values: " + SUPPORTED_INDUSTRIES
            );
        }
        return normalized;
    }

    private String normalizeKnowledgeLevel(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        if (!SUPPORTED_LEVELS.contains(normalized)) {
            throw new ApiException(
                    ErrorCode.BAD_REQUEST,
                    "Invalid level. Allowed values: " + SUPPORTED_LEVELS
            );
        }
        return normalized;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private MultipartFile prepareFile(MultipartFile file, String resolvedMimeType) {
        if (resolvedMimeType.equals(file.getContentType())) {
            return file;
        }
        try {
            return new InMemoryMultipartFile(
                    file.getName(),
                    requireOriginalFileName(file),
                    resolvedMimeType,
                    file.getBytes()
            );
        } catch (IOException e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Failed to prepare knowledge document upload");
        }
    }

    private void publishRagSync(Long documentId, AiKnowledgeRagSyncEvent.Action action) {
        eventPublisher.publishEvent(new AiKnowledgeRagSyncEvent(documentId, action));
    }

    private record ExtractionResult(String extractedText, String extractError, AiKnowledgeIngestionStatus ingestionStatus) {
    }

    private static final class InMemoryMultipartFile implements MultipartFile {
        private final String name;
        private final String originalFilename;
        private final String contentType;
        private final byte[] bytes;

        private InMemoryMultipartFile(String name, String originalFilename, String contentType, byte[] bytes) {
            this.name = name;
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.bytes = bytes;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return bytes.length == 0;
        }

        @Override
        public long getSize() {
            return bytes.length;
        }

        @Override
        public byte[] getBytes() {
            return bytes;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException {
            java.nio.file.Files.write(dest.toPath(), bytes);
        }
    }
}
