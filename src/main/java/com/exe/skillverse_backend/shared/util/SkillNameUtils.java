package com.exe.skillverse_backend.shared.util;

import java.util.Locale;

/**
 * Canonical skill-name normalization shared across all services.
 *
 * Rule: strip any non-alphanumeric separator → collapse to single underscores
 *       → trim leading/trailing underscores → UPPER_CASE.
 *
 * Examples:
 *   "BACKEND"   → "BACKEND"
 *   "BACK_END"  → "BACK_END"
 *   "back end"  → "BACK_END"
 *   "back-end"  → "BACK_END"
 *   "Java Core" → "JAVA_CORE"
 *
 * NOTE: "BACKEND" and "BACK_END" normalize to *different* canonical strings
 * because they have different alphanumeric separators — they represent
 * distinct inputs. The DB query in MentorSkillVerificationRequestRepository
 * uses REGEXP_REPLACE on both sides so they still match at query time.
 * This utility ensures a given string is consistently stored in its own
 * canonical form and never varies across re-submissions.
 */
public final class SkillNameUtils {

    private SkillNameUtils() {}

    /**
     * Normalize a skill name to canonical form.
     * Returns {@code null} when input is {@code null}.
     */
    public static String normalize(String raw) {
        if (raw == null) return null;
        return raw.trim()
                .replaceAll("[^a-zA-Z0-9]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "")
                .toUpperCase(Locale.ROOT);
    }

    /**
     * Normalize and throw {@link IllegalArgumentException} when blank.
     * Use this at write paths where a skill name is required.
     */
    public static String normalizeRequired(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Skill name is required.");
        }
        return normalize(raw);
    }
}
