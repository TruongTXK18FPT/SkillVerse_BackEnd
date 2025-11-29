package com.exe.skillverse_backend.ai_service.util;

import java.util.Objects;

public final class MarkdownSpeechSanitizer {
    private MarkdownSpeechSanitizer() {}

    /**
     * Sanitize markdown text for speech: remove/neutralize markdown formatting while
     * keeping meaningful content. Only used for TTS pipeline; does not affect rendering.
     */
    public static String sanitizeForSpeech(String markdown) {
        if (markdown == null) return null;
        String text = markdown;

        // Normalize line endings
        text = text.replace("\r\n", "\n").replace("\r", "\n");

        // Remove HTML tags that may be embedded in markdown
        text = text.replaceAll("(?s)<[^>]+>", "");

        // Images: ![alt](url) -> alt
        text = text.replaceAll("!\\[([^\\]]*)]\\([^)]*\\)", "$1");

        // Links: [label](url) -> label
        text = text.replaceAll("\\[([^\\]]+)]\\([^)]*\\)", "$1");

        // Autolinks: <http://...> -> remove to avoid reading raw URLs
        text = text.replaceAll("<https?://[^>]+>", "");

        // Inline code: `code` -> code
        text = text.replaceAll("`([^`]+)`", "$1");

        // Code fences: remove ``` and optional language, keep inner content
        text = text.replace("```", "");

        // Headings: remove leading # markers
        text = text.replaceAll("(?m)^\\s{0,3}#{1,6}\\s+", "");

        // Blockquotes: remove leading '>'
        text = text.replaceAll("(?m)^\\s{0,3}>\\s?", "");

        // Lists: bullets -, *, + and ordered lists 1. -> remove markers
        text = text.replaceAll("(?m)^\\s{0,3}[-*+]\\s+", "");
        text = text.replaceAll("(?m)^\\s{0,3}\\d+\\.\\s+", "");

        // Horizontal rules: lines of --- or *** -> remove line
        text = text.replaceAll("(?m)^\\s{0,3}([-*_]){3,}\\s*$", "");

        // Emphasis: **bold** / *italic* / __bold__ / _italic_ / ~~strike~~ -> keep inner text
        text = text.replaceAll("\\*\\*([^*]+)\\*\\*", "$1");
        text = text.replaceAll("\\*([^*]+)\\*", "$1");
        text = text.replaceAll("__([^_]+)__", "$1");
        text = text.replaceAll("_([^_]+)_", "$1");
        text = text.replaceAll("~~([^~]+)~~", "$1");

        // Collapse multiple spaces and tidy lines
        text = text.replaceAll("[\\t ]+", " ");
        text = text.replaceAll("(?m)^\\s+", "");
        text = text.replaceAll("\\n{3,}", "\n\n");
        text = text.trim();

        // Fallback to original if we stripped too much
        if (text.isEmpty()) return markdown.trim();
        return text;
    }
}

