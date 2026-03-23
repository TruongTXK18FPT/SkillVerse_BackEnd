package com.exe.skillverse_backend.course_service.policy;

import com.exe.skillverse_backend.course_service.entity.CourseRevision;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CourseRevisionCompatibilityCheckerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final CourseRevisionCompatibilityChecker checker = new CourseRevisionCompatibilityChecker();

    @Test
    void isNonBreaking_returnsTrueWhenMarkedCompatibleAndOnlyAddsContent() throws Exception {
        CourseRevision source = CourseRevision.builder()
                .contentSnapshotJson(OBJECT_MAPPER.readTree("""
                        {
                          "modules": [
                            { "id": 1, "lessons": [ { "id": 10 } ], "quizzes": [], "assignments": [] }
                          ],
                          "completionRule": { "requiredLessons": 1 }
                        }
                        """))
                .build();
        CourseRevision target = CourseRevision.builder()
                .contentSnapshotJson(OBJECT_MAPPER.readTree("""
                        {
                          "compatibility": { "autoCompatibleOnly": true },
                          "modules": [
                            { "id": 1, "lessons": [ { "id": 10 }, { "id": 11 } ], "quizzes": [], "assignments": [] }
                          ],
                          "completionRule": { "requiredLessons": 1 }
                        }
                        """))
                .build();

        assertTrue(checker.isNonBreaking(source, target));
    }

    @Test
    void isNonBreaking_returnsFalseWhenExistingLearningItemsAreRemoved() throws Exception {
        CourseRevision source = CourseRevision.builder()
                .contentSnapshotJson(OBJECT_MAPPER.readTree("""
                        {
                          "modules": [
                            { "id": 1, "lessons": [ { "id": 10 }, { "id": 11 } ], "quizzes": [], "assignments": [] }
                          ]
                        }
                        """))
                .build();
        CourseRevision target = CourseRevision.builder()
                .contentSnapshotJson(OBJECT_MAPPER.readTree("""
                        {
                          "compatibility": { "autoCompatibleOnly": true },
                          "modules": [
                            { "id": 1, "lessons": [ { "id": 10 } ], "quizzes": [], "assignments": [] }
                          ]
                        }
                        """))
                .build();

        assertFalse(checker.isNonBreaking(source, target));
    }

    @Test
    void isNonBreaking_returnsFalseWhenCompletionRuleChanges() throws Exception {
        CourseRevision source = CourseRevision.builder()
                .contentSnapshotJson(OBJECT_MAPPER.readTree("""
                        {
                          "modules": [
                            { "id": 1, "lessons": [ { "id": 10 } ], "quizzes": [], "assignments": [] }
                          ],
                          "completionRule": { "requiredLessons": 1 }
                        }
                        """))
                .build();
        CourseRevision target = CourseRevision.builder()
                .contentSnapshotJson(OBJECT_MAPPER.readTree("""
                        {
                          "compatibility": { "autoCompatibleOnly": true },
                          "modules": [
                            { "id": 1, "lessons": [ { "id": 10 } ], "quizzes": [], "assignments": [] }
                          ],
                          "completionRule": { "requiredLessons": 2 }
                        }
                        """))
                .build();

        assertFalse(checker.isNonBreaking(source, target));
    }

    @Test
    void isNonBreaking_returnsFalseWhenRevisionNotMarkedCompatible() throws Exception {
        CourseRevision source = CourseRevision.builder()
                .contentSnapshotJson(OBJECT_MAPPER.readTree("{\"modules\":[]}"))
                .build();
        CourseRevision target = CourseRevision.builder()
                .contentSnapshotJson(OBJECT_MAPPER.readTree("{\"modules\":[]}"))
                .build();

        assertFalse(checker.isNonBreaking(source, target));
    }

    @Test
    void isNonBreaking_returnsFalseWhenSnapshotMissing() throws Exception {
        CourseRevision source = CourseRevision.builder()
                .contentSnapshotJson(OBJECT_MAPPER.readTree("{\"modules\":[]}"))
                .build();
        CourseRevision target = CourseRevision.builder()
                .contentSnapshotJson(null)
                .build();

        assertFalse(checker.isNonBreaking(source, target));
    }

    @Test
    void isNonBreaking_returnsFalseWhenSourceSnapshotIsLegacyEmptyObject() throws Exception {
        CourseRevision source = CourseRevision.builder()
                .contentSnapshotJson(OBJECT_MAPPER.readTree("{}"))
                .build();
        CourseRevision target = CourseRevision.builder()
                .contentSnapshotJson(OBJECT_MAPPER.readTree("""
                        {
                          "compatibility": { "autoCompatibleOnly": true },
                          "completionRule": { "requiredLessons": 1 },
                          "modules": []
                        }
                        """))
                .build();

        assertFalse(checker.isNonBreaking(source, target));
    }

    @Test
    void isNonBreaking_returnsFalseWhenTargetIntroducesNewCompletionRule() throws Exception {
        CourseRevision source = CourseRevision.builder()
                .contentSnapshotJson(OBJECT_MAPPER.readTree("""
                        {
                          "modules": [
                            { "id": 1, "lessons": [ { "id": 10 } ], "quizzes": [], "assignments": [] }
                          ]
                        }
                        """))
                .build();
        CourseRevision target = CourseRevision.builder()
                .contentSnapshotJson(OBJECT_MAPPER.readTree("""
                        {
                          "compatibility": { "autoCompatibleOnly": true },
                          "modules": [
                            { "id": 1, "lessons": [ { "id": 10 } ], "quizzes": [], "assignments": [] }
                          ],
                          "completionRule": { "requiredLessons": 1 }
                        }
                        """))
                .build();

        assertFalse(checker.isNonBreaking(source, target));
    }

    @Test
    void isNonBreaking_returnsFalseWhenExistingQuizPassScoreChanges() throws Exception {
        CourseRevision source = CourseRevision.builder()
                .contentSnapshotJson(OBJECT_MAPPER.readTree("""
                        {
                          "modules": [
                            {
                              "id": 1,
                              "lessons": [],
                              "quizzes": [ { "id": 21, "passScore": 70, "title": "Quiz v1" } ],
                              "assignments": []
                            }
                          ]
                        }
                        """))
                .build();
        CourseRevision target = CourseRevision.builder()
                .contentSnapshotJson(OBJECT_MAPPER.readTree("""
                        {
                          "compatibility": { "autoCompatibleOnly": true },
                          "modules": [
                            {
                              "id": 1,
                              "lessons": [],
                              "quizzes": [ { "id": 21, "passScore": 80, "title": "Quiz v2" } ],
                              "assignments": []
                            }
                          ]
                        }
                        """))
                .build();

        assertFalse(checker.isNonBreaking(source, target));
    }

    @Test
    void isNonBreaking_returnsFalseWhenAssignmentPassingScoreChanges() throws Exception {
        CourseRevision source = CourseRevision.builder()
                .contentSnapshotJson(OBJECT_MAPPER.readTree("""
                        {
                          "modules": [
                            {
                              "id": 1,
                              "lessons": [],
                              "quizzes": [],
                              "assignments": [ { "id": 31, "passingScore": 60, "isRequired": true } ]
                            }
                          ]
                        }
                        """))
                .build();
        CourseRevision target = CourseRevision.builder()
                .contentSnapshotJson(OBJECT_MAPPER.readTree("""
                        {
                          "compatibility": { "autoCompatibleOnly": true },
                          "modules": [
                            {
                              "id": 1,
                              "lessons": [],
                              "quizzes": [],
                              "assignments": [ { "id": 31, "passingScore": 75, "isRequired": true } ]
                            }
                          ]
                        }
                        """))
                .build();

        assertFalse(checker.isNonBreaking(source, target));
    }

    @Test
    void isNonBreaking_returnsFalseWhenAssignmentRequiredFlagChanges() throws Exception {
        CourseRevision source = CourseRevision.builder()
                .contentSnapshotJson(OBJECT_MAPPER.readTree("""
                        {
                          "modules": [
                            {
                              "id": 1,
                              "lessons": [],
                              "quizzes": [],
                              "assignments": [ { "id": 31, "passingScore": 60, "isRequired": false } ]
                            }
                          ]
                        }
                        """))
                .build();
        CourseRevision target = CourseRevision.builder()
                .contentSnapshotJson(OBJECT_MAPPER.readTree("""
                        {
                          "compatibility": { "autoCompatibleOnly": true },
                          "modules": [
                            {
                              "id": 1,
                              "lessons": [],
                              "quizzes": [],
                              "assignments": [ { "id": 31, "passingScore": 60, "isRequired": true } ]
                            }
                          ]
                        }
                        """))
                .build();

        assertFalse(checker.isNonBreaking(source, target));
    }

    @Test
    void isNonBreaking_returnsFalseWhenExistingLessonTypeChanges() throws Exception {
        CourseRevision source = CourseRevision.builder()
                .contentSnapshotJson(OBJECT_MAPPER.readTree("""
                        {
                          "modules": [
                            { "id": 1, "lessons": [ { "id": 10, "type": "READING" } ], "quizzes": [], "assignments": [] }
                          ]
                        }
                        """))
                .build();
        CourseRevision target = CourseRevision.builder()
                .contentSnapshotJson(OBJECT_MAPPER.readTree("""
                        {
                          "compatibility": { "autoCompatibleOnly": true },
                          "modules": [
                            { "id": 1, "lessons": [ { "id": 10, "type": "VIDEO" } ], "quizzes": [], "assignments": [] }
                          ]
                        }
                        """))
                .build();

        assertFalse(checker.isNonBreaking(source, target));
    }

    @Test
    void isNonBreaking_returnsTrueWhenOnlyMetadataChangesOnExistingItems() throws Exception {
        CourseRevision source = CourseRevision.builder()
                .contentSnapshotJson(OBJECT_MAPPER.readTree("""
                        {
                          "modules": [
                            {
                              "id": 1,
                              "lessons": [ { "id": 10, "type": "READING", "title": "Old lesson title" } ],
                              "quizzes": [ { "id": 21, "passScore": 70, "title": "Old quiz title" } ],
                              "assignments": [ { "id": 31, "passingScore": 60, "isRequired": true, "title": "Old assignment title" } ]
                            }
                          ],
                          "completionRule": { "requiredLessons": 1 }
                        }
                        """))
                .build();
        CourseRevision target = CourseRevision.builder()
                .contentSnapshotJson(OBJECT_MAPPER.readTree("""
                        {
                          "compatibility": { "autoCompatibleOnly": true },
                          "modules": [
                            {
                              "id": 1,
                              "lessons": [ { "id": 10, "type": "READING", "title": "New lesson title" } ],
                              "quizzes": [ { "id": 21, "passScore": 70, "title": "New quiz title" } ],
                              "assignments": [ { "id": 31, "passingScore": 60, "isRequired": true, "title": "New assignment title" } ]
                            }
                          ],
                          "completionRule": { "requiredLessons": 1 }
                        }
                        """))
                .build();

        assertTrue(checker.isNonBreaking(source, target));
    }

    @Test
    void evaluateCompatibility_returnsItemRuleReasonWhenQuizPassScoreChanges() throws Exception {
        CourseRevision source = CourseRevision.builder()
                .contentSnapshotJson(OBJECT_MAPPER.readTree("""
                        {
                          "modules": [
                            { "id": 1, "quizzes": [ { "id": 88, "passScore": 70 } ] }
                          ]
                        }
                        """))
                .build();
        CourseRevision target = CourseRevision.builder()
                .contentSnapshotJson(OBJECT_MAPPER.readTree("""
                        {
                          "compatibility": { "autoCompatibleOnly": true },
                          "modules": [
                            { "id": 1, "quizzes": [ { "id": 88, "passScore": 80 } ] }
                          ]
                        }
                        """))
                .build();

        CourseRevisionCompatibilityChecker.CompatibilityResult result = checker.evaluateCompatibility(source, target);

        assertFalse(result.isNonBreaking());
        assertEquals("ITEM_RULE_CHANGED", result.getReasonCode());
        assertEquals("quiz:88/passScore", result.getReasonDetail());
    }

    @Test
    void evaluateCompatibility_detectsBreakingQuizRuleInsideModuleLessonsArray() throws Exception {
        CourseRevision source = CourseRevision.builder()
                .contentSnapshotJson(OBJECT_MAPPER.readTree("""
                        {
                          "modules": [
                            {
                              "id": 1,
                              "lessons": [
                                { "id": 88, "type": "quiz", "passScore": 70 },
                                { "id": 44, "type": "assignment", "passingScore": 60, "isRequired": true }
                              ]
                            }
                          ]
                        }
                        """))
                .build();
        CourseRevision target = CourseRevision.builder()
                .contentSnapshotJson(OBJECT_MAPPER.readTree("""
                        {
                          "compatibility": { "autoCompatibleOnly": true },
                          "modules": [
                            {
                              "id": 1,
                              "lessons": [
                                { "id": 88, "type": "quiz", "passScore": 80 },
                                { "id": 44, "type": "assignment", "passingScore": 60, "isRequired": true }
                              ]
                            }
                          ]
                        }
                        """))
                .build();

        CourseRevisionCompatibilityChecker.CompatibilityResult result = checker.evaluateCompatibility(source, target);

        assertFalse(result.isNonBreaking());
        assertEquals("ITEM_RULE_CHANGED", result.getReasonCode());
        assertEquals("quiz:88/passScore", result.getReasonDetail());
    }

    @Test
    void evaluateCompatibility_returnsCourseRuleReasonWhenCompletionRuleIntroduced() throws Exception {
        CourseRevision source = CourseRevision.builder()
                .contentSnapshotJson(OBJECT_MAPPER.readTree("{\"modules\":[]}"))
                .build();
        CourseRevision target = CourseRevision.builder()
                .contentSnapshotJson(OBJECT_MAPPER.readTree("""
                        {
                          "compatibility": { "autoCompatibleOnly": true },
                          "modules": [],
                          "completionRule": { "requiredLessons": 1 }
                        }
                        """))
                .build();

        CourseRevisionCompatibilityChecker.CompatibilityResult result = checker.evaluateCompatibility(source, target);

        assertFalse(result.isNonBreaking());
        assertEquals("COURSE_RULE_CHANGED", result.getReasonCode());
        assertEquals("/completionRule", result.getReasonDetail());
    }

    @Test
    void evaluateCompatibility_returnsItemRemovedWhenExistingItemIdChanges() throws Exception {
        CourseRevision source = CourseRevision.builder()
                .contentSnapshotJson(OBJECT_MAPPER.readTree("""
                        {
                          "modules": [
                            { "id": 1, "lessons": [ { "id": 10, "type": "READING" } ] }
                          ]
                        }
                        """))
                .build();
        CourseRevision target = CourseRevision.builder()
                .contentSnapshotJson(OBJECT_MAPPER.readTree("""
                        {
                          "compatibility": { "autoCompatibleOnly": true },
                          "modules": [
                            { "id": 1, "lessons": [ { "id": 11, "type": "READING" } ] }
                          ]
                        }
                        """))
                .build();

        CourseRevisionCompatibilityChecker.CompatibilityResult result = checker.evaluateCompatibility(source, target);

        assertFalse(result.isNonBreaking());
        assertEquals("ITEM_REMOVED", result.getReasonCode());
        assertEquals("lesson:10", result.getReasonDetail());
    }

    @Test
    void evaluateCompatibility_returnsItemRemovedWhenExistingItemTypeChanges() throws Exception {
        CourseRevision source = CourseRevision.builder()
                .contentSnapshotJson(OBJECT_MAPPER.readTree("""
                        {
                          "modules": [
                            { "id": 1, "lessons": [ { "id": 10, "type": "READING" } ], "quizzes": [] }
                          ]
                        }
                        """))
                .build();
        CourseRevision target = CourseRevision.builder()
                .contentSnapshotJson(OBJECT_MAPPER.readTree("""
                        {
                          "compatibility": { "autoCompatibleOnly": true },
                          "modules": [
                            { "id": 1, "lessons": [], "quizzes": [ { "id": 10, "passScore": 70 } ] }
                          ]
                        }
                        """))
                .build();

        CourseRevisionCompatibilityChecker.CompatibilityResult result = checker.evaluateCompatibility(source, target);

        assertFalse(result.isNonBreaking());
        assertEquals("ITEM_REMOVED", result.getReasonCode());
        assertEquals("lesson:10", result.getReasonDetail());
    }

    @Test
    void evaluateCompatibility_treatsMissingRuleFieldAsInheritedNotBreaking() throws Exception {
        CourseRevision source = CourseRevision.builder()
                .contentSnapshotJson(OBJECT_MAPPER.readTree("""
                        {
                          "modules": [
                            { "id": 1, "quizzes": [ { "id": 88, "passScore": 70 } ] }
                          ]
                        }
                        """))
                .build();
        CourseRevision target = CourseRevision.builder()
                .contentSnapshotJson(OBJECT_MAPPER.readTree("""
                        {
                          "compatibility": { "autoCompatibleOnly": true },
                          "modules": [
                            { "id": 1, "quizzes": [ { "id": 88 } ] }
                          ]
                        }
                        """))
                .build();

        CourseRevisionCompatibilityChecker.CompatibilityResult result = checker.evaluateCompatibility(source, target);

        assertTrue(result.isNonBreaking());
        assertEquals("NON_BREAKING", result.getReasonCode());
    }

    @Test
    void evaluateCompatibility_treatsExplicitNullRuleAsBreakingChange() throws Exception {
        CourseRevision source = CourseRevision.builder()
                .contentSnapshotJson(OBJECT_MAPPER.readTree("""
                        {
                          "modules": [
                            { "id": 1, "quizzes": [ { "id": 88, "passScore": 70 } ] }
                          ]
                        }
                        """))
                .build();
        CourseRevision target = CourseRevision.builder()
                .contentSnapshotJson(OBJECT_MAPPER.readTree("""
                        {
                          "compatibility": { "autoCompatibleOnly": true },
                          "modules": [
                            { "id": 1, "quizzes": [ { "id": 88, "passScore": null } ] }
                          ]
                        }
                        """))
                .build();

        CourseRevisionCompatibilityChecker.CompatibilityResult result = checker.evaluateCompatibility(source, target);

        assertFalse(result.isNonBreaking());
        assertEquals("ITEM_RULE_CHANGED", result.getReasonCode());
        assertEquals("quiz:88/passScore", result.getReasonDetail());
    }
}
