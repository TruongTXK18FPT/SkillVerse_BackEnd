package com.exe.skillverse_backend.course_service.dto.coursedto;

import lombok.Builder;

/**
 * Represents a single content item change within a module in a revision diff.
 * An item can be ADDED (exists only in revision), REMOVED (exists only in live),
 * or MODIFIED (exists in both with field differences).
 */
@Builder
public record ItemChange(
    ChangeType changeType,
    ItemKind kind,
    Long id,
    Long moduleId,
    String title,
    FieldChangeList fieldChanges
) {

  public enum ChangeType {
    ADDED, REMOVED, MODIFIED
  }

  public enum ItemKind {
    LESSON, QUIZ, ASSIGNMENT
  }

  /**
   * Immutable list of field-level changes for MODIFIED items.
   */
  @Builder
  public record FieldChangeList(
      FieldChange title,
      FieldChange description,
      FieldChange orderIndex,
      FieldChange contentText,
      FieldChange durationSec,
      FieldChange videoUrl,
      FieldChange resourceUrl,
      FieldChange passScore,
      FieldChange maxAttempts,
      FieldChange timeLimitMinutes,
      FieldChange gradingMethod,
      FieldChange submissionType,
      FieldChange maxScore,
      FieldChange passingScore,
      FieldChange isRequired,
      FieldChange lessonType
  ) {
  }

  /**
   * A single field change, from a previous value to a new value.
   */
  public record FieldChange(
      String fieldName,
      String previousValue,
      String newValue
  ) {
  }
}
