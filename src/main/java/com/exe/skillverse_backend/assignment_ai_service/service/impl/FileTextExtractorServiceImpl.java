package com.exe.skillverse_backend.assignment_ai_service.service.impl;

import com.exe.skillverse_backend.assignment_ai_service.service.FileTextExtractorService;
import com.exe.skillverse_backend.shared.entity.Media;
import com.exe.skillverse_backend.shared.service.MediaService;
import java.io.InputStream;
import java.net.URL;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileTextExtractorServiceImpl implements FileTextExtractorService {

    // NOTE: 20MB here = AI service memory/process limit.
    // Actual upload limit is enforced earlier by MediaServiceImpl (10MB Cloudinary Free Tier).
    // Files larger than 10MB are rejected before reaching this service.
    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024; // 20MB (AI service limit)
    private static final int MAX_CHARS = 50_000;

    private final MediaService mediaService;

    @Override
    public String extractText(Media media, String contentType) {
        if (media.getFileSize() != null && media.getFileSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                String.format("File size (%.1fMB) exceeds the maximum allowed size of %dMB",
                            media.getFileSize() / 1024.0 / 1024.0, MAX_FILE_SIZE / 1024 / 1024));
        }

        String url = media.getUrl();
        String text;

        if (contentType != null && contentType.contains("pdf")) {
            text = extractFromPdf(url);
        } else if (contentType != null
                && (contentType.contains("wordprocessingml")
                    || contentType.contains("msword"))) {
            text = extractFromDocx(url);
        } else {
            throw new IllegalArgumentException(
                "Unsupported content type for AI grading: " + contentType
                + ". Only PDF and DOCX are supported.");
        }

        if (text.length() > MAX_CHARS) {
            text = text.substring(0, MAX_CHARS);
            log.warn("Extracted text truncated to {} chars for submission {}",
                MAX_CHARS, media.getId());
        }

        return text;
    }

    private String extractFromPdf(String url) {
        try (InputStream is = new URL(url).openStream();
             PDDocument document = PDDocument.load(is)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        } catch (java.io.IOException e) {
            log.error("Failed to extract text from PDF: {}", url, e);
            throw new RuntimeException("Failed to read PDF content", e);
        }
    }

    private String extractFromDocx(String url) {
        try (InputStream is = new URL(url).openStream();
             XWPFDocument document = new XWPFDocument(is)) {
            XWPFWordExtractor extractor = new XWPFWordExtractor(document);
            return extractor.getText();
        } catch (java.io.IOException e) {
            log.error("Failed to extract text from DOCX: {}", url, e);
            throw new RuntimeException("Failed to read DOCX content", e);
        }
    }
}
