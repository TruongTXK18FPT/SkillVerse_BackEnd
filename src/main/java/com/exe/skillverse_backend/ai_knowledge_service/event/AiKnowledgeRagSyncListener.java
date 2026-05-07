package com.exe.skillverse_backend.ai_knowledge_service.event;

import com.exe.skillverse_backend.ai_knowledge_service.entity.AiKnowledgeDocument;
import com.exe.skillverse_backend.ai_knowledge_service.repository.AiKnowledgeDocumentRepository;
import com.exe.skillverse_backend.ai_knowledge_service.service.AiKnowledgeIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiKnowledgeRagSyncListener {
    private final AiKnowledgeDocumentRepository documentRepository;
    private final AiKnowledgeIngestionService ingestionService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(AiKnowledgeRagSyncEvent event) {
        AiKnowledgeDocument document = documentRepository.findById(event.getDocumentId()).orElse(null);
        if (document == null) {
            log.warn("AI knowledge document not found for RAG sync: {}", event.getDocumentId());
            return;
        }
        try {
            switch (event.getAction()) {
                case INGEST -> ingestionService.ingest(document);
                case REINDEX -> ingestionService.reindex(document);
                case DELETE_FROM_RAG -> ingestionService.deleteFromRag(document);
            }
        } catch (Exception e) {
            log.error("AI knowledge RAG sync failed for document {} with action {}: {}",
                    event.getDocumentId(), event.getAction(), e.getMessage(), e);
        }
    }
}
