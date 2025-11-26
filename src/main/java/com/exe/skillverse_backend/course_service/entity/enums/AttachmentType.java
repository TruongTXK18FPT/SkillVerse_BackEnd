package com.exe.skillverse_backend.course_service.entity.enums;

/**
 * Types of attachments that can be added to lessons
 * Used for Reading lessons to provide supplementary materials
 */
public enum AttachmentType {
    PDF, // Uploaded PDF file
    DOCX, // Uploaded Word document
    PPTX, // Uploaded PowerPoint
    XLSX, // Uploaded Excel
    EXTERNAL_LINK, // Generic external link
    GOOGLE_DRIVE, // Google Drive link
    GITHUB, // GitHub repository
    YOUTUBE, // YouTube video (supplementary)
    WEBSITE // External website
}
