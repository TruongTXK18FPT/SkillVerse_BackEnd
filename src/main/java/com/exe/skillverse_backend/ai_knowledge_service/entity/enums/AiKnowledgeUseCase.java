package com.exe.skillverse_backend.ai_knowledge_service.entity.enums;

/**
 * Use case for AI knowledge documents.
 * Determines how the document is used in the AI system.
 */
public enum AiKnowledgeUseCase {
    CHATBOT_GLOBAL,
    ROADMAP_SKILL,

    /**
     * Legacy AI Knowledge document scopes — retained for backward compatibility only.
     *
     * New mentor grading-doc uploads are no longer supported (upload endpoint removed 2026-04-24).
     * These values are kept so that admin list/detail/reindex/archive and RAG cleanup operations
     * can still handle any existing rows in ai_knowledge_documents safely.
     *
     * Runtime AI grading no longer reads these documents. It now uses course/module reading
     * content via domain=course_content RAG queries and a direct DB reading-lesson fallback.
     * See AssignmentAiGradingServiceImpl.fetchCourseContentContext().
     */
    GRADING_ASSIGNMENT,
    GRADING_MODULE,
    GRADING_COURSE
}
