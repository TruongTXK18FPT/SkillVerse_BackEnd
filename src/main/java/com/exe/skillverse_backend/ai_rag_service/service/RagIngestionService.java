package com.exe.skillverse_backend.ai_rag_service.service;

import com.exe.skillverse_backend.ai_rag_service.config.AiRagProperties;
import com.exe.skillverse_backend.ai_rag_service.dto.DocumentInput;
import com.exe.skillverse_backend.ai_rag_service.provider.EmbeddingProvider;
import com.exe.skillverse_backend.ai_rag_service.repository.RagChunkJdbcRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RagIngestionService {

    private final AiRagProperties properties;
    private final TextChunker textChunker;
    private final EmbeddingProvider embeddingProvider;
    private final RagChunkJdbcRepository repository;

    @Transactional
    public int ingestDocument(DocumentInput document) {
        log.info("Starting ingestion for document {} (type={})", document.getDocId(), document.getDocType());

        // 1. Delete old chunks
        repository.deleteByDocIdPattern(document.getDocId());

        // 2. Chunk text
        List<String> chunks = textChunker.chunkText(document.getContent());
        int originalSize = chunks.size();
        
        if (originalSize > properties.getMaxDocumentChunks()) {
            log.info("Document {} generated {} chunks, truncating to {}", 
                    document.getDocId(), originalSize, properties.getMaxDocumentChunks());
            chunks = chunks.subList(0, properties.getMaxDocumentChunks());
            if (document.getMetadata() != null) {
                document.getMetadata().put("truncated", "true");
                document.getMetadata().put("original_chunk_count", originalSize);
            }
        } else {
            log.info("Document {} generated {} chunks", document.getDocId(), originalSize);
        }

        if (chunks.isEmpty()) {
            log.warn("Document {} has empty extracted text, no chunks generated", document.getDocId());
            return 0;
        }

        // 3. Embed and insert in batches
        int batchSize = properties.getEmbeddingBatchSize();
        int chunksInserted = 0;

        for (int i = 0; i < chunks.size(); i += batchSize) {
            int end = Math.min(i + batchSize, chunks.size());
            List<String> batchChunks = chunks.subList(i, end);
            
            List<List<Double>> embeddings = embeddingProvider.embed(batchChunks);
            
            for (int j = 0; j < batchChunks.size(); j++) {
                String chunkId = document.getDocId() + "_chunk_" + (i + j);
                repository.insertChunk(
                        chunkId,
                        document.getDocType(),
                        document.getTitle(),
                        batchChunks.get(j),
                        embeddings.get(j),
                        document.getMetadata()
                );
                chunksInserted++;
            }
            log.info("Inserted batch {} of {} for document {}", (i / batchSize) + 1, (int) Math.ceil((double) chunks.size() / batchSize), document.getDocId());
        }

        log.info("Finished ingestion for document {}. Inserted {} chunks.", document.getDocId(), chunksInserted);
        return chunksInserted;
    }
    
    @Transactional
    public void deleteDocument(String docId) {
        log.info("Deleting chunks for document {}", docId);
        repository.deleteByDocIdPattern(docId);
    }
}
