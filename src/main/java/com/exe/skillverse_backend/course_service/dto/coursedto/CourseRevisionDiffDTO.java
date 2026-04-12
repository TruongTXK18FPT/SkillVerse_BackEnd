package com.exe.skillverse_backend.course_service.dto.coursedto;

import java.util.List;
import lombok.Builder;

/**
 * Top-level response DTO for the revision diff (what-changed) endpoint.
 * Compares a revision snapshot against the current live course content
 * and returns structured changes organized by module.
 */
@Builder
public record CourseRevisionDiffDTO(
    Long revisionId,
    Long courseId,
    String revisionTitle,
    String liveTitle,
    int revisionNumber,
    List<ModuleChangeDTO> moduleChanges,
    DiffSummary summary
) {

  /**
   * Summary counts of all changes for quick overview.
   */
  @Builder
  public record DiffSummary(
      int modulesAdded,
      int modulesRemoved,
      int modulesModified,
      int modulesContentOnly,
      int lessonsAdded,
      int lessonsModified,
      int lessonsRemoved,
      int quizzesAdded,
      int quizzesModified,
      int quizzesRemoved,
      int assignmentsAdded,
      int assignmentsModified,
      int assignmentsRemoved
  ) {

    public int totalChanges() {
      return modulesAdded + modulesRemoved + modulesModified + modulesContentOnly
          + lessonsAdded + lessonsModified + lessonsRemoved
          + quizzesAdded + quizzesModified + quizzesRemoved
          + assignmentsAdded + assignmentsModified + assignmentsRemoved;
    }

    public boolean hasChanges() {
      return totalChanges() > 0;
    }
  }
}
