package com.exe.skillverse_backend.ai_rag_service.provider;

import java.util.List;

public interface EmbeddingProvider {
    List<List<Double>> embed(List<String> texts);
}
