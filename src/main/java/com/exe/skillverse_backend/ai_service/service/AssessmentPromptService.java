package com.exe.skillverse_backend.ai_service.service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service interface for generating specialized assessment prompts
 * based on domain, industry, and job role.
 * Uses expert knowledge from TaxonomyService to create professional test questions.
 */
public interface AssessmentPromptService {

    /**
     * Get the assessment test generation prompt for a specific domain/industry/role.
     */
    String getTestGenerationPrompt(String domain, String industry, String role, UserAssessmentInfo userInfo);

    /**
     * Get the test evaluation prompt.
     */
    String getEvaluationPrompt(String domain, String industry, String role, TestSubmissionInfo submissionInfo);

    /**
     * Detect domain from user input.
     */
    String detectDomain(String target, String industry, String role);

    /**
     * Detect role from user input.
     */
    String detectRole(String target, String industry, String role);

    /**
     * Get allowed skills for a domain/role combination.
     */
    Set<String> getAllowedSkills(String domain, String role);

    /**
     * User's assessment form information (simplified for AI test generation).
     */
    class UserAssessmentInfo {
        private String domain;
        private String goal;
        private String level;
        private List<String> skills;
        private List<String> focusAreas;
        private String language;
        private String duration;
        private Integer questionCount;

        public UserAssessmentInfo(String domain, String goal, String level, List<String> skills,
                List<String> focusAreas, String language, String duration, Integer questionCount) {
            this.domain = domain;
            this.goal = goal;
            this.level = level;
            this.skills = skills;
            this.focusAreas = focusAreas;
            this.language = language;
            this.duration = duration;
            this.questionCount = questionCount;
        }

        public String domain() { return domain; }
        public String goal() { return goal; }
        public String level() { return level; }
        public List<String> skills() { return skills; }
        public List<String> focusAreas() { return focusAreas; }
        public String language() { return language; }
        public String duration() { return duration; }
        public Integer questionCount() { return questionCount; }
    }

    /**
     * Test submission information for evaluation.
     */
    class TestSubmissionInfo {
        private String testTitle;
        private String targetField;
        private String domain;
        private String industry;
        private String role;
        private List<QuestionInfo> questions;
        private Map<Long, Object> userAnswers;

        public TestSubmissionInfo(String testTitle, String targetField, String domain, String industry,
                String role, List<QuestionInfo> questions, Map<Long, Object> userAnswers) {
            this.testTitle = testTitle;
            this.targetField = targetField;
            this.domain = domain;
            this.industry = industry;
            this.role = role;
            this.questions = questions;
            this.userAnswers = userAnswers;
        }

        public String testTitle() { return testTitle; }
        public String targetField() { return targetField; }
        public String domain() { return domain; }
        public String industry() { return industry; }
        public String role() { return role; }
        public List<QuestionInfo> questions() { return questions; }
        public Map<Long, Object> userAnswers() { return userAnswers; }
    }

    /**
     * Question information.
     */
    class QuestionInfo {
        private Long questionId;
        private String question;
        private List<String> options;
        private String correctAnswer;
        private String explanation;
        private String difficulty;
        private String skillArea;

        public QuestionInfo(Long questionId, String question, List<String> options, String correctAnswer,
                String explanation, String difficulty, String skillArea) {
            this.questionId = questionId;
            this.question = question;
            this.options = options;
            this.correctAnswer = correctAnswer;
            this.explanation = explanation;
            this.difficulty = difficulty;
            this.skillArea = skillArea;
        }

        public Long questionId() { return questionId; }
        public String question() { return question; }
        public List<String> options() { return options; }
        public String correctAnswer() { return correctAnswer; }
        public String explanation() { return explanation; }
        public String difficulty() { return difficulty; }
        public String skillArea() { return skillArea; }
    }
}
