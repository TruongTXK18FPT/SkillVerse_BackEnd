package com.exe.skillverse_backend.question_bank_service.service.impl;

import com.exe.skillverse_backend.question_bank_service.dto.request.BulkImportRequest;
import com.exe.skillverse_backend.question_bank_service.dto.request.CreateQuestionRequest;
import com.exe.skillverse_backend.question_bank_service.dto.response.ImportResultResponse;
import com.exe.skillverse_backend.question_bank_service.repository.QuestionBankRepository;
import com.exe.skillverse_backend.question_bank_service.service.QuestionImportService;
import com.exe.skillverse_backend.question_bank_service.service.QuestionBankQuestionService;
import com.exe.skillverse_backend.shared.exception.ApiException;
import com.exe.skillverse_backend.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class QuestionImportServiceImpl implements QuestionImportService {

    private final QuestionBankRepository questionBankRepository;
    private final QuestionBankQuestionService questionBankQuestionService;
    private final ObjectMapper objectMapper;

    @Override
    public BulkImportRequest previewImport(MultipartFile file, Long bankId) {
        if (!questionBankRepository.existsById(bankId)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Question bank not found: " + bankId);
        }

        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "File name is required");
        }

        List<BulkImportRequest.QuestionImportItem> items;

        if (filename.toLowerCase().endsWith(".csv")) {
            items = parseCSV(file);
        } else if (filename.toLowerCase().endsWith(".json")) {
            items = parseJSON(file);
        } else {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Unsupported file format. Use CSV or JSON");
        }

        for (int i = 0; i < items.size(); i++) {
            validateItem(items.get(i), i + 1);
        }

        log.info("Preview import for bank {}: {} rows", bankId, items.size());
        return BulkImportRequest.builder()
                .questions(items)
                .build();
    }

    @Override
    public ImportResultResponse confirmImport(Long bankId, List<CreateQuestionRequest> questions, String source) {
        int savedCount = questionBankQuestionService.bulkAddQuestions(
                bankId, questions, source != null ? source : "IMPORT");

        List<Integer> savedIndices = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            savedIndices.add(i);
        }

        return ImportResultResponse.builder()
                .totalRows(questions.size())
                .validRows(questions.size())
                .invalidRows(0)
                .savedCount(savedCount)
                .savedIndices(savedIndices)
                .message("Successfully imported " + savedCount + " questions")
                .build();
    }

    // ==================== CSV Parsing (custom, no dependency) ====================

    private List<BulkImportRequest.QuestionImportItem> parseCSV(MultipartFile file) {
        List<BulkImportRequest.QuestionImportItem> items = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "CSV file is empty");
            }

            List<String> headers = parseCSVLine(headerLine);

            // Map header names to indices (case-insensitive)
            int qIdx = findHeader(headers, "question_text", "questiontext", "question");
            int oaIdx = findHeader(headers, "option_a", "optiona", "option1", "a");
            int obIdx = findHeader(headers, "option_b", "optionb", "option2", "b");
            int ocIdx = findHeader(headers, "option_c", "optionc", "option3", "c");
            int odIdx = findHeader(headers, "option_d", "optiond", "option4", "d");
            int caIdx = findHeader(headers, "correct_answer", "correctanswer", "answer", "ca");
            int expIdx = findHeader(headers, "explanation", "exp");
            int diffIdx = findHeader(headers, "difficulty", "diff");
            int skillIdx = findHeader(headers, "skill_area", "skillarea", "skill");
            int catIdx = findHeader(headers, "category", "cat");

            String line;
            int lineNum = 1;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                if (line.trim().isEmpty()) continue;

                List<String> values = parseCSVLine(line);

                String questionText = getValue(values, qIdx);
                String optionA = normalizeOption(getValue(values, oaIdx), "A");
                String optionB = normalizeOption(getValue(values, obIdx), "B");
                String optionC = normalizeOption(getValue(values, ocIdx), "C");
                String optionD = normalizeOption(getValue(values, odIdx), "D");
                String correctAnswer = normalizeAnswer(getValue(values, caIdx));
                String explanation = getValue(values, expIdx);
                String difficulty = normalizeDifficulty(getValue(values, diffIdx));
                String skillArea = getValue(values, skillIdx);
                String category = normalizeCategory(getValue(values, catIdx));

                List<String> options = Arrays.asList(optionA, optionB, optionC, optionD);

                items.add(BulkImportRequest.QuestionImportItem.builder()
                        .questionText(questionText)
                        .options(options)
                        .correctAnswer(correctAnswer)
                        .explanation(explanation)
                        .difficulty(difficulty)
                        .skillArea(skillArea)
                        .category(category)
                        .lineNumber(lineNum)
                        .build());
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse CSV file: {}", e.getMessage(), e);
            throw new ApiException(ErrorCode.BAD_REQUEST, "Failed to parse CSV file: " + e.getMessage());
        }

        return items;
    }

    private int findHeader(List<String> headers, String... names) {
        for (String name : names) {
            for (int i = 0; i < headers.size(); i++) {
                if (headers.get(i).trim().equalsIgnoreCase(name)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private String getValue(List<String> values, int index) {
        if (index < 0 || index >= values.size()) return null;
        String v = values.get(index);
        return v != null ? v.trim() : null;
    }

    private List<String> parseCSVLine(String line) {
        List<String> result = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        result.add(current.toString());
        return result;
    }

    private String normalizeOption(String value, String prefix) {
        if (value == null || value.isBlank()) return "";
        String trimmed = value.trim();
        if (trimmed.matches("^[A-D]\\..*")) {
            trimmed = trimmed.substring(2).trim();
        }
        return prefix + ". " + trimmed;
    }

    private String normalizeAnswer(String value) {
        if (value == null) return null;
        String v = value.trim().toUpperCase();
        if (v.length() > 1) v = v.substring(0, 1);
        return v.matches("[A-D]") ? v : null;
    }

    private String normalizeDifficulty(String difficulty) {
        if (difficulty == null || difficulty.isBlank()) return "INTERMEDIATE";
        String d = difficulty.trim().toUpperCase();
        return (d.equals("BEGINNER") || d.equals("INTERMEDIATE") || d.equals("ADVANCED") || d.equals("EXPERT"))
                ? d : "INTERMEDIATE";
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) return "KNOWLEDGE";
        String c = category.trim().toUpperCase();
        return (c.equals("KNOWLEDGE") || c.equals("SKILL") || c.equals("SITUATION") || c.equals("ANALYSIS"))
                ? c : "KNOWLEDGE";
    }

    // ==================== JSON Parsing ====================

    private List<BulkImportRequest.QuestionImportItem> parseJSON(MultipartFile file) {
        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            List<?> rawList = objectMapper.readValue(content, List.class);

            List<BulkImportRequest.QuestionImportItem> items = new ArrayList<>();
            int lineNumber = 1;

            for (Object item : rawList) {
                if (!(item instanceof Map)) {
                    throw new ApiException(ErrorCode.BAD_REQUEST, "Invalid JSON format at item " + lineNumber);
                }

                Map<String, Object> map = (Map<String, Object>) item;
                lineNumber++;

                String questionText = (String) map.get("questionText");
                String correctAnswer = normalizeAnswer((String) map.get("correctAnswer"));
                String difficulty = normalizeDifficulty((String) map.get("difficulty"));
                String skillArea = (String) map.get("skillArea");
                String category = normalizeCategory((String) map.get("category"));
                String explanation = (String) map.get("explanation");

                List<String> options = new ArrayList<>();
                Object optionsObj = map.get("options");
                if (optionsObj instanceof List) {
                    List<?> opts = (List<?>) optionsObj;
                    String[] prefixes = {"A. ", "B. ", "C. ", "D. "};
                    for (int i = 0; i < Math.min(opts.size(), 4); i++) {
                        String val = opts.get(i) != null ? opts.get(i).toString().trim() : "";
                        String prefix = prefixes[i];
                        if (!val.startsWith(prefix)) {
                            val = prefix + val;
                        }
                        options.add(val);
                    }
                }

                items.add(BulkImportRequest.QuestionImportItem.builder()
                        .questionText(questionText)
                        .options(options.size() == 4 ? options : null)
                        .correctAnswer(correctAnswer)
                        .explanation(explanation)
                        .difficulty(difficulty)
                        .skillArea(skillArea)
                        .category(category)
                        .lineNumber(lineNumber - 1)
                        .build());
            }

            return items;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse JSON file: {}", e.getMessage(), e);
            throw new ApiException(ErrorCode.BAD_REQUEST, "Failed to parse JSON file: " + e.getMessage());
        }
    }

    // ==================== Validation ====================

    private void validateItem(BulkImportRequest.QuestionImportItem item, int lineNumber) {
        List<String> errors = new ArrayList<>();

        if (item.getQuestionText() == null || item.getQuestionText().isBlank()) {
            errors.add("Question text is required");
        } else if (item.getQuestionText().length() > 2000) {
            errors.add("Question text exceeds 2000 characters");
        }

        if (item.getOptions() == null || item.getOptions().size() < 4) {
            errors.add("Exactly 4 options are required");
        } else {
            for (int i = 0; i < item.getOptions().size(); i++) {
                String opt = item.getOptions().get(i);
                if (opt == null || opt.isBlank()) {
                    errors.add("Option " + (char) ('A' + i) + " is blank");
                } else if (opt.length() > 500) {
                    errors.add("Option " + (char) ('A' + i) + " exceeds 500 characters");
                }
            }
        }

        if (item.getCorrectAnswer() == null || item.getCorrectAnswer().isBlank()) {
            errors.add("Correct answer is required");
        } else if (!item.getCorrectAnswer().matches("^[A-D]$")) {
            errors.add("Correct answer must be A, B, C, or D");
        }

        if (item.getExplanation() != null && item.getExplanation().length() > 1000) {
            errors.add("Explanation exceeds 1000 characters");
        }

        item.setValid(errors.isEmpty());
        item.setErrors(errors);
    }
}
