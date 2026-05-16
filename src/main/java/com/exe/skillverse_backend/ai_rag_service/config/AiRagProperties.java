package com.exe.skillverse_backend.ai_rag_service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "skillverse.ai.rag")
@Data
public class AiRagProperties {
    private boolean javaEnabled = true;
    private int topKDefault = 5;
    private int topKMax = 20;
    private double scoreThreshold = 0.4;
    private int chunkSize = 150;
    private int chunkOverlap = 15;
    private int maxDocumentChunks = 1000;
    private int embeddingBatchSize = 16;
    private int ingestMaxConcurrency = 1;
}
