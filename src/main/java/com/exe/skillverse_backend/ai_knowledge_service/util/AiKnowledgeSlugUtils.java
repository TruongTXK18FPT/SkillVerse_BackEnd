package com.exe.skillverse_backend.ai_knowledge_service.util;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Utility for converting skill names to roadmap-friendly slugs.
 * 
 * Canonical skill names use UPPER_SNAKE_CASE (e.g., JAVA_BACKEND).
 * Roadmap domain slugs use lowercase-hyphen (e.g., java-backend).
 * 
 * This utility does NOT use SkillNameUtils directly because that utility
 * is designed for canonical skill name normalization, not slug generation.
 */
public final class AiKnowledgeSlugUtils {

    private AiKnowledgeSlugUtils() {
        // Utility class
    }

    // Special character mappings for programming languages
    private static final Map<String, String> SPECIAL_CHAR_MAP = new HashMap<>();
    static {
        SPECIAL_CHAR_MAP.put("c#", "csharp");
        SPECIAL_CHAR_MAP.put("c++", "cpp");
        SPECIAL_CHAR_MAP.put("c/", "c");
        SPECIAL_CHAR_MAP.put(".net", "dotnet");
        SPECIAL_CHAR_MAP.put("node.js", "nodejs");
        SPECIAL_CHAR_MAP.put("nodejs", "nodejs");
        SPECIAL_CHAR_MAP.put("f#", "fsharp");
        SPECIAL_CHAR_MAP.put("r#", "rsharp");
    }

    /**
     * Convert a canonical skill name to a roadmap-friendly slug.
     * 
     * Handles:
     * - Special characters: C# → csharp, C++ → cpp, Node.js → nodejs, .NET → dotnet
     * - Vietnamese diacritics: Lập trình → lap-trinh
     * - Mixed case and separators: Java Backend → java-backend
     * 
     * Examples:
     * - "JAVA" -> "java"
     * - "JAVA_BACKEND" -> "java-backend"
     * - "DATA_SCIENCE" -> "data-science"
     * - "java backend" -> "java-backend"
     * - "Java-Backend" -> "java-backend"
     * - "C#" -> "csharp"
     * - "C++" -> "cpp"
     * - "Node.js" -> "nodejs"
     * - ".NET" -> "dotnet"
     * - "Lập trình" -> "lap-trinh"
     * 
     * @param skillName the skill name (canonical or user input)
     * @return lowercase-hyphen slug, or null if input is null/blank
     */
    public static String toRoadmapSkillSlug(String skillName) {
        if (skillName == null || skillName.isBlank()) {
            return null;
        }

        String normalized = skillName.trim().toLowerCase();

        // Apply special character mappings (case-insensitive)
        for (Map.Entry<String, String> entry : SPECIAL_CHAR_MAP.entrySet()) {
            normalized = normalized.replaceAll("(?i)" + Pattern.quote(entry.getKey()), entry.getValue());
        }

        // Remove diacritics (Vietnamese accents, etc.)
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFD)
                .replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");

        // Replace remaining non-alphanumeric sequences with hyphens
        normalized = normalized
                .replaceAll("[^a-z0-9]+", "-")  // Replace non-alphanumeric with hyphens
                .replaceAll("-+", "-")           // Collapse multiple hyphens
                .replaceAll("^-|-$", "");        // Trim leading/trailing hyphens

        return normalized.isEmpty() ? null : normalized;
    }

    /**
     * Build the RAG domain key for roadmap skill documents.
     * 
     * @param skillSlug the lowercase-hyphen skill slug
     * @return domain key like "roadmap_skill_java-backend"
     */
    public static String toRoadmapDomain(String skillSlug) {
        if (skillSlug == null || skillSlug.isBlank()) {
            return null;
        }
        return "roadmap_skill_" + skillSlug;
    }
}
