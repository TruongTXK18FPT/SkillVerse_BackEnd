package com.exe.skillverse_backend.roadmap_package_service.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for sequentially enriching a single roadmap node based on an expert template blueprint
 * and a student's diagnostic assessment profile using Mistral AI.
 */
public interface RoadmapNodeAiEnrichmentService {

    class EnrichedLesson {
        private String title;
        private String description;
        private String learningObjective;
        private Integer estimatedMinutes;

        public EnrichedLesson() {}

        public EnrichedLesson(String title, String description, String learningObjective, Integer estimatedMinutes) {
            this.title = title;
            this.description = description;
            this.learningObjective = learningObjective;
            this.estimatedMinutes = estimatedMinutes;
        }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getLearningObjective() { return learningObjective; }
        public void setLearningObjective(String learningObjective) { this.learningObjective = learningObjective; }
        public Integer getEstimatedMinutes() { return estimatedMinutes; }
        public void setEstimatedMinutes(Integer estimatedMinutes) { this.estimatedMinutes = estimatedMinutes; }
    }

    class EnrichedNode {
        private String description;
        private List<String> learningObjectives;
        private List<String> practicalExercises;
        private List<String> successCriteria;
        private String expectedOutput;
        private String rubric;
        private List<EnrichedLesson> lessons = new ArrayList<>();

        public EnrichedNode() {}

        public EnrichedNode(String description, List<String> learningObjectives, 
                            List<String> practicalExercises, List<String> successCriteria, 
                            String expectedOutput, String rubric) {
            this.description = description;
            this.learningObjectives = learningObjectives;
            this.practicalExercises = practicalExercises;
            this.successCriteria = successCriteria;
            this.expectedOutput = expectedOutput;
            this.rubric = rubric;
            this.lessons = new ArrayList<>();
        }

        public EnrichedNode(String description, List<String> learningObjectives, 
                            List<String> practicalExercises, List<String> successCriteria, 
                            String expectedOutput, String rubric, List<EnrichedLesson> lessons) {
            this.description = description;
            this.learningObjectives = learningObjectives;
            this.practicalExercises = practicalExercises;
            this.successCriteria = successCriteria;
            this.expectedOutput = expectedOutput;
            this.rubric = rubric;
            this.lessons = lessons != null ? lessons : new ArrayList<>();
        }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public List<String> getLearningObjectives() { return learningObjectives; }
        public void setLearningObjectives(List<String> learningObjectives) { this.learningObjectives = learningObjectives; }

        public List<String> getPracticalExercises() { return practicalExercises; }
        public void setPracticalExercises(List<String> practicalExercises) { this.practicalExercises = practicalExercises; }

        public List<String> getSuccessCriteria() { return successCriteria; }
        public void setSuccessCriteria(List<String> successCriteria) { this.successCriteria = successCriteria; }

        public String getExpectedOutput() { return expectedOutput; }
        public void setExpectedOutput(String expectedOutput) { this.expectedOutput = expectedOutput; }

        public String getRubric() { return rubric; }
        public void setRubric(String rubric) { this.rubric = rubric; }

        public List<EnrichedLesson> getLessons() { return lessons; }
        public void setLessons(List<EnrichedLesson> lessons) { this.lessons = lessons != null ? lessons : new ArrayList<>(); }
    }

    /**
     * Call Mistral AI to enrich a single template node based on student diagnostic results.
     */
    EnrichedNode enrichNode(
            String nodeTitle,
            String nodeDescription,
            String baselineExpectedOutput,
            String baselineRubric,
            String skillName,
            String studentLevel,
            String studentGoal,
            boolean isGap,
            boolean isStrength
    );

    default EnrichedNode enrichNode(
            String nodeTitle,
            String nodeDescription,
            String baselineExpectedOutput,
            String baselineRubric,
            String skillName,
            String studentLevel,
            String studentGoal,
            boolean isGap,
            boolean isStrength,
            String pinnedDocumentIdsJson
    ) {
        return enrichNode(nodeTitle, nodeDescription, baselineExpectedOutput, baselineRubric, skillName, studentLevel, studentGoal, isGap, isStrength);
    }

    default EnrichedNode enrichNode(
            String nodeTitle,
            String nodeDescription,
            String baselineExpectedOutput,
            String baselineRubric,
            String skillName,
            String studentLevel,
            String studentGoal,
            boolean isGap,
            boolean isStrength,
            String pinnedDocumentIdsJson,
            String lessonsJson
    ) {
        return enrichNode(nodeTitle, nodeDescription, baselineExpectedOutput, baselineRubric, skillName, studentLevel, studentGoal, isGap, isStrength, pinnedDocumentIdsJson);
    }
}
