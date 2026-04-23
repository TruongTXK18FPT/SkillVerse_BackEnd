package com.exe.skillverse_backend.ai_knowledge_service.entity.enums;

/**
 * Approval status for AI knowledge documents.
 * Admin uploads are auto-approved; mentor submissions require admin review.
 */
public enum AiKnowledgeApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED
}
