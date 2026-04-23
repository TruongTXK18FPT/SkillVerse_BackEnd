package com.exe.skillverse_backend.ai_knowledge_service.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class AiKnowledgeRagSyncEvent {
    private final Long documentId;
    private final Action action;

    public enum Action {
        INGEST,
        REINDEX,
        DELETE_FROM_RAG
    }
}
