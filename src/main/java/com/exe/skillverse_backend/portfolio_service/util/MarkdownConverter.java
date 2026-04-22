package com.exe.skillverse_backend.portfolio_service.util;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Utility class for converting Markdown to HTML.
 * Used when mapping portfolio data to CV to ensure proper rendering in templates.
 */
@Component
public class MarkdownConverter {

    private final Parser parser;
    private final HtmlRenderer renderer;

    public MarkdownConverter() {
        List<Extension> extensions = List.of(TablesExtension.create());
        this.parser = Parser.builder()
                .extensions(extensions)
                .build();
        this.renderer = HtmlRenderer.builder()
                .extensions(extensions)
                .build();
    }

    /**
     * Converts Markdown text to HTML.
     * If input is null or empty, returns empty string.
     * 
     * @param markdown The markdown text to convert
     * @return HTML formatted string
     */
    public String toHtml(String markdown) {
        if (markdown == null || markdown.trim().isEmpty()) {
            return "";
        }

        try {
            Node document = parser.parse(markdown);
            String html = renderer.render(document);
            
            // Remove wrapping <p> tags if the content is a single paragraph
            // This helps with inline rendering in CV templates
            html = html.trim();
            if (html.startsWith("<p>") && html.endsWith("</p>") && html.indexOf("<p>", 1) == -1) {
                html = html.substring(3, html.length() - 4);
            }
            
            return html;
        } catch (Exception e) {
            // If conversion fails, return the original text with basic HTML escaping
            return escapeHtml(markdown);
        }
    }

    /**
     * Converts Markdown to HTML with paragraphs preserved.
     * Use this for multi-line content that should maintain paragraph structure.
     */
    public String toHtmlWithParagraphs(String markdown) {
        if (markdown == null || markdown.trim().isEmpty()) {
            return "";
        }

        try {
            Node document = parser.parse(markdown);
            return renderer.render(document).trim();
        } catch (Exception e) {
            return escapeHtml(markdown);
        }
    }

    /**
     * Basic HTML escaping for fallback when markdown parsing fails.
     */
    private String escapeHtml(String text) {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;");
    }
}
