package com.exe.skillverse_backend.ai_service.service.impl;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lightweight TF-IDF vectorizer for cosine similarity fallback in course pre-selection.
 */
@Component
public class TfIdfVectorizer {

    public Map<Long, Double> computeCosineScores(
            List<String> queryTerms,
            Set<Long> candidateIds,
            Map<Long, Map<String, Long>> courseTermFreqs,
            Map<String, Long> documentFreqs,
            int totalDocuments) {

        if (queryTerms == null || queryTerms.isEmpty() || candidateIds == null || candidateIds.isEmpty()) {
            return Map.of();
        }

        Map<String, Long> queryTf = new HashMap<>();
        for (String term : queryTerms) {
            if (term != null && !term.isBlank()) {
                queryTf.merge(term, 1L, Long::sum);
            }
        }

        if (queryTf.isEmpty()) {
            return Map.of();
        }

        Map<String, Double> queryWeights = new HashMap<>();
        double queryNormSq = 0.0;

        for (Map.Entry<String, Long> entry : queryTf.entrySet()) {
            String term = entry.getKey();
            double idf = computeIdf(term, documentFreqs, totalDocuments);
            if (idf <= 0) {
                continue;
            }
            double weight = entry.getValue() * idf;
            queryWeights.put(term, weight);
            queryNormSq += weight * weight;
        }

        if (queryWeights.isEmpty() || queryNormSq <= 0) {
            return Map.of();
        }

        double queryNorm = Math.sqrt(queryNormSq);
        Map<Long, Double> scores = new LinkedHashMap<>();

        for (Long courseId : candidateIds) {
            Map<String, Long> tfMap = courseTermFreqs.get(courseId);
            if (tfMap == null || tfMap.isEmpty()) {
                continue;
            }

            double dot = 0.0;
            double docNormSq = 0.0;
            for (Map.Entry<String, Double> queryWeight : queryWeights.entrySet()) {
                String term = queryWeight.getKey();
                long tf = tfMap.getOrDefault(term, 0L);
                if (tf <= 0) {
                    continue;
                }

                double idf = computeIdf(term, documentFreqs, totalDocuments);
                if (idf <= 0) {
                    continue;
                }

                double docWeight = tf * idf;
                dot += queryWeight.getValue() * docWeight;
                docNormSq += docWeight * docWeight;
            }

            if (dot <= 0 || docNormSq <= 0) {
                continue;
            }

            double cosine = dot / (queryNorm * Math.sqrt(docNormSq));
            if (cosine > 0) {
                scores.put(courseId, cosine);
            }
        }

        return scores;
    }

    private double computeIdf(String term, Map<String, Long> documentFreqs, int totalDocuments) {
        if (term == null || term.isBlank() || totalDocuments <= 0) {
            return 0.0;
        }
        long df = documentFreqs.getOrDefault(term, 0L);
        return Math.log((totalDocuments + 1.0) / (df + 1.0)) + 1.0;
    }
}
