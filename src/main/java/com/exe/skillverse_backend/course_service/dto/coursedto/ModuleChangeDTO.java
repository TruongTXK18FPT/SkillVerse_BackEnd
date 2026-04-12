package com.exe.skillverse_backend.course_service.dto.coursedto;

import java.util.List;
import lombok.Builder;

/**
 * Represents a single module change in a revision diff.
 * A module can be ADDED, REMOVED, MODIFIED (field changes), or
 * CONTENT_ONLY (same module fields but items inside changed).
 */
@Builder
public record ModuleChangeDTO(
    ChangeKind changeKind,
    Long id,
    Long revisionId,
    Long liveId,
    String title,
    String description,
    Integer orderIndex,
    List<ItemChange> itemChanges,
    ItemChange.FieldChangeList fieldChanges
) {

  public enum ChangeKind {
    ADDED,
    REMOVED,
    MODIFIED,
    CONTENT_ONLY
  }
}
