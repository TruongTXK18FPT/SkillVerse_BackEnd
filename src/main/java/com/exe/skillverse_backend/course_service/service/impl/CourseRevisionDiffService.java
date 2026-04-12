package com.exe.skillverse_backend.course_service.service.impl;

import com.exe.skillverse_backend.course_service.dto.coursedto.CourseRevisionDiffDTO;
import com.exe.skillverse_backend.course_service.dto.coursedto.ItemChange;
import com.exe.skillverse_backend.course_service.dto.coursedto.ItemChange.ChangeType;
import com.exe.skillverse_backend.course_service.dto.coursedto.ItemChange.FieldChange;
import com.exe.skillverse_backend.course_service.dto.coursedto.ItemChange.FieldChangeList;
import com.exe.skillverse_backend.course_service.dto.coursedto.ItemChange.ItemKind;
import com.exe.skillverse_backend.course_service.dto.coursedto.ModuleChangeDTO;
import com.exe.skillverse_backend.course_service.dto.coursedto.ModuleChangeDTO.ChangeKind;
import com.exe.skillverse_backend.course_service.dto.coursedto.CourseRevisionDiffDTO.DiffSummary;
import com.exe.skillverse_backend.course_service.entity.Assignment;
import com.exe.skillverse_backend.course_service.entity.CourseRevision;
import com.exe.skillverse_backend.course_service.entity.Lesson;
import com.exe.skillverse_backend.course_service.entity.Module;
import com.exe.skillverse_backend.course_service.entity.Quiz;
import com.exe.skillverse_backend.course_service.repository.CourseRevisionRepository;
import com.exe.skillverse_backend.course_service.repository.ModuleRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CourseRevisionDiffService {

  private final CourseRevisionRepository courseRevisionRepository;
  private final ModuleRepository moduleRepository;

  // ─── Public API ───────────────────────────────────────────────────────────

  public CourseRevisionDiffDTO computeDiff(Long revisionId) {
    CourseRevision revision = courseRevisionRepository.findById(revisionId)
        .orElseThrow(() -> new IllegalArgumentException("Revision not found: " + revisionId));

    Long courseId = revision.getCourse().getId();
    JsonNode snapshot = revision.getContentSnapshotJson();
    List<SnapshotModule> snapshotModules = parseSnapshot(snapshot);
    List<Module> liveModules = moduleRepository.findByCourseIdOrderByOrderIndexAsc(courseId);
    Map<Long, Module> liveById = liveModules.stream()
        .collect(Collectors.toMap(Module::getId, Function.identity()));

    List<ModuleChangeDTO> moduleChanges = computeModuleChanges(snapshotModules, liveById, revisionId);
    DiffSummary summary = buildSummary(moduleChanges);

    return CourseRevisionDiffDTO.builder()
        .revisionId(revisionId)
        .courseId(courseId)
        .revisionTitle(revision.getTitle())
        .liveTitle(revision.getCourse().getTitle())
        .revisionNumber(revision.getRevisionNumber())
        .moduleChanges(moduleChanges)
        .summary(summary)
        .build();
  }

  // ─── Snapshot Parsing ─────────────────────────────────────────────────────

  private List<SnapshotModule> parseSnapshot(JsonNode snapshot) {
    List<SnapshotModule> modules = new ArrayList<>();
    if (snapshot == null || !snapshot.has("modules")) {
      return modules;
    }
    JsonNode modulesNode = snapshot.get("modules");
    if (!modulesNode.isArray()) {
      return modules;
    }
    for (JsonNode moduleNode : modulesNode) {
      SnapshotModule sm = parseSnapshotModule(moduleNode);
      if (sm != null) {
        modules.add(sm);
      }
    }
    return modules;
  }

  private SnapshotModule parseSnapshotModule(JsonNode moduleNode) {
    Long moduleId = parsePositiveId(moduleNode.path("id"));
    String title = textOr(moduleNode.path("title"), null);
    String description = textOr(moduleNode.path("description"), null);
    Integer orderIndex = intOr(moduleNode.path("orderIndex"), null);

    List<SnapshotItem> items = new ArrayList<>();
    JsonNode itemsNode = moduleNode.path("lessons");
    if (itemsNode.isArray()) {
      for (JsonNode itemNode : itemsNode) {
        SnapshotItem item = parseSnapshotItem(itemNode, moduleId);
        if (item != null) {
          items.add(item);
        }
      }
    }
    return new SnapshotModule(moduleId, title, description, orderIndex, items);
  }

  private SnapshotItem parseSnapshotItem(JsonNode itemNode, Long moduleId) {
    Long id = parsePositiveId(itemNode.path("id"));
    String type = textOr(itemNode.path("type"), "lesson");
    String itemTitle = textOr(itemNode.path("title"), null);
    Integer orderIndex = intOr(itemNode.path("orderIndex"), 0);
    String normalizedType = type.trim().toLowerCase();

    if ("quiz".equals(normalizedType)) {
      return new SnapshotQuiz(
          id,
          moduleId,
          itemTitle,
          orderIndex,
          textOr(itemNode.path("quizDescription"), null),
          intOr(itemNode.path("passScore"), null),
          intOr(itemNode.path("quizMaxAttempts"), null),
          intOr(itemNode.path("quizTimeLimitMinutes"), null),
          textOr(itemNode.path("gradingMethod"), null)
      );
    }

    if ("assignment".equals(normalizedType)) {
      return new SnapshotAssignment(
          id,
          moduleId,
          itemTitle,
          orderIndex,
          textOr(itemNode.path("assignmentDescription"), null),
          textOr(itemNode.path("assignmentSubmissionType"), null),
          textOr(itemNode.path("assignmentMaxScore"), null),
          textOr(itemNode.path("assignmentPassingScore"), null),
          boolOr(itemNode.path("isRequired"), null)
      );
    }

    String lessonType = "video".equals(normalizedType) ? "VIDEO" : "READING";
    Integer durationMin = intOr(itemNode.path("durationMin"), null);
    return new SnapshotLesson(
        id,
        moduleId,
        itemTitle,
        orderIndex,
        lessonType,
        textOr(itemNode.path("contentText"), null),
        durationMin != null ? durationMin * 60 : null,
        textOr(itemNode.path("youtubeUrl"), textOr(itemNode.path("videoUrl"), null)),
        textOr(itemNode.path("resourceUrl"), null)
    );
  }

  // ─── Diff Computation ─────────────────────────────────────────────────────

  private List<ModuleChangeDTO> computeModuleChanges(
      List<SnapshotModule> snapshotModules,
      Map<Long, Module> liveById,
      Long revisionId
  ) {
    List<ModuleChangeDTO> changes = new ArrayList<>();

    // Build lookup maps for snapshot modules
    Map<Long, SnapshotModule> snapshotById = new HashMap<>();
    for (SnapshotModule sm : snapshotModules) {
      if (sm.id() != null) {
        snapshotById.put(sm.id(), sm);
      }
    }

    // Process snapshot modules — match by ID first, then by title
    List<SnapshotModule> unmatchedSnapshots = new ArrayList<>();
    for (SnapshotModule sm : snapshotModules) {
      Module live = sm.id() != null ? liveById.get(sm.id()) : null;
      if (live == null && sm.title() != null) {
        live = findLiveModuleByTitle(sm.title(), liveById.values());
      }

      if (live == null) {
        // ADDED in revision
        List<ItemChange> itemChanges = computeItemChanges(sm.items(), null, sm.id(), revisionId);
        changes.add(ModuleChangeDTO.builder()
            .changeKind(ChangeKind.ADDED)
            .id(null)
            .revisionId(revisionId)
            .liveId(null)
            .title(sm.title())
            .description(sm.description())
            .orderIndex(sm.orderIndex())
            .itemChanges(itemChanges)
            .fieldChanges(null)
            .build());
      } else {
        // Module exists in both — detect field-level changes
        FieldChangeList moduleFieldChanges = computeModuleFieldChanges(sm, live);
        List<ItemChange> itemChanges = computeItemChanges(sm.items(), live, live.getId(), revisionId);

        if (moduleFieldChanges != null || !itemChanges.isEmpty()) {
          ChangeKind kind = moduleFieldChanges != null ? ChangeKind.MODIFIED : ChangeKind.CONTENT_ONLY;
          changes.add(ModuleChangeDTO.builder()
              .changeKind(kind)
              .id(live.getId())
              .revisionId(revisionId)
              .liveId(live.getId())
              .title(sm.title())
              .description(sm.description())
              .orderIndex(sm.orderIndex())
              .itemChanges(itemChanges)
              .fieldChanges(moduleFieldChanges)
              .build());
        }
        liveById.remove(live.getId());
      }
    }

    // Remaining live modules → REMOVED
    for (Module live : liveById.values()) {
      changes.add(ModuleChangeDTO.builder()
          .changeKind(ChangeKind.REMOVED)
          .id(live.getId())
          .revisionId(revisionId)
          .liveId(live.getId())
          .title(live.getTitle())
          .description(live.getDescription())
          .orderIndex(live.getOrderIndex())
          .itemChanges(List.of())
          .fieldChanges(null)
          .build());
    }

    // Sort by orderIndex for consistent display
    changes.sort(Comparator
        .comparing(ModuleChangeDTO::orderIndex, Comparator.nullsLast(Integer::compareTo))
        .thenComparing(ModuleChangeDTO::id, Comparator.nullsLast(Long::compareTo)));

    return changes;
  }

  private List<ItemChange> computeItemChanges(
      List<SnapshotItem> snapshotItems,
      Module live,
      Long moduleId,
      Long revisionId
  ) {
    if (snapshotItems == null || snapshotItems.isEmpty()) {
      return List.of();
    }

    List<ItemChange> changes = new ArrayList<>();

    // Build live lookup maps by kind
    List<Lesson> liveLessons = toList(live != null ? live.getLessons() : null);
    List<Quiz> liveQuizzes = toList(live != null ? live.getQuizzes() : null);
    List<Assignment> liveAssignments = toList(live != null ? live.getAssignments() : null);

    Map<Long, Lesson> liveLessonsById = liveLessons.stream()
        .collect(Collectors.toMap(Lesson::getId, Function.identity()));
    Map<Long, Quiz> liveQuizzesById = liveQuizzes.stream()
        .collect(Collectors.toMap(Quiz::getId, Function.identity()));
    Map<Long, Assignment> liveAssignmentsById = liveAssignments.stream()
        .collect(Collectors.toMap(Assignment::getId, Function.identity()));

    for (SnapshotItem si : snapshotItems) {
      Object liveItem = matchLiveItem(si, liveLessonsById, liveQuizzesById, liveAssignmentsById);
      if (liveItem == null && si.title() != null) {
        liveItem = matchLiveItemByTitle(si, liveLessons, liveQuizzes, liveAssignments);
      }

      if (liveItem == null) {
        // ADDED in revision
        changes.add(ItemChange.builder()
            .changeType(ChangeType.ADDED)
            .kind(toItemKind(si))
            .id(si.id())
            .moduleId(moduleId)
            .title(si.title())
            .fieldChanges(null)
            .build());
      } else {
        // Check for MODIFIED
        FieldChangeList fc = computeItemFieldChanges(si, liveItem, toItemKind(si));
        if (fc != null) {
          changes.add(ItemChange.builder()
              .changeType(ChangeType.MODIFIED)
              .kind(toItemKind(si))
              .id(getLiveItemId(liveItem))
              .moduleId(moduleId)
              .title(si.title())
              .fieldChanges(fc)
              .build());
        }
        removeFromLiveMaps(liveItem, liveLessonsById, liveQuizzesById, liveAssignmentsById);
      }
    }

    // Remaining live items → REMOVED
    for (Lesson l : liveLessonsById.values()) {
      changes.add(ItemChange.builder()
          .changeType(ChangeType.REMOVED)
          .kind(ItemKind.LESSON)
          .id(l.getId())
          .moduleId(moduleId)
          .title(l.getTitle())
          .fieldChanges(null)
          .build());
    }
    for (Quiz q : liveQuizzesById.values()) {
      changes.add(ItemChange.builder()
          .changeType(ChangeType.REMOVED)
          .kind(ItemKind.QUIZ)
          .id(q.getId())
          .moduleId(moduleId)
          .title(q.getTitle())
          .fieldChanges(null)
          .build());
    }
    for (Assignment a : liveAssignmentsById.values()) {
      changes.add(ItemChange.builder()
          .changeType(ChangeType.REMOVED)
          .kind(ItemKind.ASSIGNMENT)
          .id(a.getId())
          .moduleId(moduleId)
          .title(a.getTitle())
          .fieldChanges(null)
          .build());
    }

    return changes;
  }

  // ─── Field-level Change Detection ─────────────────────────────────────

  private FieldChangeList computeModuleFieldChanges(SnapshotModule snap, Module live) {
    List<FieldChange> changes = new ArrayList<>();

    String snapTitle = snap.title() != null ? snap.title().trim() : null;
    String liveTitle = live.getTitle() != null ? live.getTitle().trim() : null;
    if (!Objects.equals(snapTitle, liveTitle)) {
      changes.add(new FieldChange("title", liveTitle, snapTitle));
    }

    String snapDesc = snap.description() != null ? snap.description().trim() : null;
    String liveDesc = live.getDescription() != null ? live.getDescription().trim() : null;
    if (!Objects.equals(snapDesc, liveDesc)) {
      changes.add(new FieldChange("description", liveDesc, snapDesc));
    }

    if (!Objects.equals(snap.orderIndex(), live.getOrderIndex())) {
      changes.add(new FieldChange("orderIndex",
          String.valueOf(live.getOrderIndex()), String.valueOf(snap.orderIndex())));
    }

    return changes.isEmpty() ? null : toFieldChangeList(changes);
  }

  private FieldChangeList computeItemFieldChanges(SnapshotItem si, Object liveItem, ItemKind kind) {
    List<FieldChange> changes = new ArrayList<>();

    if (si instanceof SnapshotLesson sl) {
      Lesson l = (Lesson) liveItem;
      addIfChanged(changes, "title", l.getTitle(), sl.title());
      addIfChanged(changes, "contentText", l.getContentText(), sl.contentText());
      addIfChanged(changes, "durationSec", l.getDurationSec(), sl.durationSec());
      addIfChanged(changes, "videoUrl", l.getVideoUrl(), sl.videoUrl());
      addIfChanged(changes, "resourceUrl", l.getResourceUrl(), sl.resourceUrl());
      addIfChanged(changes, "lessonType",
          l.getType() != null ? l.getType().name() : null, sl.lessonType());
    } else if (si instanceof SnapshotQuiz sq) {
      Quiz q = (Quiz) liveItem;
      addIfChanged(changes, "title", q.getTitle(), sq.title());
      addIfChanged(changes, "description", q.getDescription(), sq.description());
      addIfChanged(changes, "passScore", q.getPassScore(), sq.passScore());
      addIfChanged(changes, "maxAttempts", q.getMaxAttempts(), sq.maxAttempts());
      addIfChanged(changes, "timeLimitMinutes", q.getTimeLimitMinutes(), sq.timeLimitMinutes());
      addIfChanged(changes, "gradingMethod",
          q.getGradingMethod() != null ? q.getGradingMethod().name() : null, sq.gradingMethod());
    } else if (si instanceof SnapshotAssignment sa) {
      Assignment a = (Assignment) liveItem;
      addIfChanged(changes, "title", a.getTitle(), sa.title());
      addIfChanged(changes, "description", a.getDescription(), sa.description());
      addIfChanged(changes, "submissionType",
          a.getSubmissionType() != null ? a.getSubmissionType().name() : null, sa.submissionType());
      addIfChanged(changes, "maxScore",
          a.getMaxScore() != null ? a.getMaxScore().toPlainString() : null, sa.maxScore());
      addIfChanged(changes, "passingScore",
          a.getPassingScore() != null ? a.getPassingScore().toPlainString() : null, sa.passingScore());
      addIfChanged(changes, "isRequired", a.getIsRequired(), sa.isRequired());
    }

    return changes.isEmpty() ? null : toFieldChangeList(changes);
  }

  private <T> void addIfChanged(List<FieldChange> changes, String field, T live, T snap) {
    String liveStr = live != null ? String.valueOf(live) : null;
    String snapStr = snap != null ? String.valueOf(snap) : null;
    if (!Objects.equals(liveStr, snapStr)) {
      changes.add(new FieldChange(field, liveStr, snapStr));
    }
  }

  // ─── Summary ────────────────────────────────────────────────────────────

  private DiffSummary buildSummary(List<ModuleChangeDTO> moduleChanges) {
    int modulesAdded = 0, modulesRemoved = 0, modulesModified = 0, modulesContentOnly = 0;
    int lessonsAdded = 0, lessonsModified = 0, lessonsRemoved = 0;
    int quizzesAdded = 0, quizzesModified = 0, quizzesRemoved = 0;
    int assignmentsAdded = 0, assignmentsModified = 0, assignmentsRemoved = 0;

    for (ModuleChangeDTO mc : moduleChanges) {
      switch (mc.changeKind()) {
        case ADDED -> modulesAdded++;
        case REMOVED -> modulesRemoved++;
        case MODIFIED -> modulesModified++;
        case CONTENT_ONLY -> modulesContentOnly++;
      }

      if (mc.itemChanges() != null) {
        for (ItemChange ic : mc.itemChanges()) {
          switch (ic.changeType()) {
            case ADDED -> {
              if (ic.kind() == ItemKind.LESSON) lessonsAdded++;
              else if (ic.kind() == ItemKind.QUIZ) quizzesAdded++;
              else assignmentsAdded++;
            }
            case MODIFIED -> {
              if (ic.kind() == ItemKind.LESSON) lessonsModified++;
              else if (ic.kind() == ItemKind.QUIZ) quizzesModified++;
              else assignmentsModified++;
            }
            case REMOVED -> {
              if (ic.kind() == ItemKind.LESSON) lessonsRemoved++;
              else if (ic.kind() == ItemKind.QUIZ) quizzesRemoved++;
              else assignmentsRemoved++;
            }
          }
        }
      }
    }

    return DiffSummary.builder()
        .modulesAdded(modulesAdded)
        .modulesRemoved(modulesRemoved)
        .modulesModified(modulesModified)
        .modulesContentOnly(modulesContentOnly)
        .lessonsAdded(lessonsAdded)
        .lessonsModified(lessonsModified)
        .lessonsRemoved(lessonsRemoved)
        .quizzesAdded(quizzesAdded)
        .quizzesModified(quizzesModified)
        .quizzesRemoved(quizzesRemoved)
        .assignmentsAdded(assignmentsAdded)
        .assignmentsModified(assignmentsModified)
        .assignmentsRemoved(assignmentsRemoved)
        .build();
  }

  // ─── Helpers ─────────────────────────────────────────────────────────────

  private Long parsePositiveId(JsonNode node) {
    if (node == null || node.isNull() || !node.isNumber()) {
      return null;
    }
    long val = node.asLong();
    return val > 0 ? val : null;
  }

  private String textOr(JsonNode node, String fallback) {
    if (node == null || node.isNull() || !node.isTextual()) {
      return fallback;
    }
    String val = node.asText().trim();
    return val.isEmpty() ? fallback : val;
  }

  private Integer intOr(JsonNode node, Integer fallback) {
    if (node == null || node.isNull() || !node.isNumber()) {
      return fallback;
    }
    return node.asInt();
  }

  private Boolean boolOr(JsonNode node, Boolean fallback) {
    if (node == null || node.isNull()) {
      return fallback;
    }
    if (node.isBoolean()) {
      return node.asBoolean();
    }
    if (node.isTextual()) {
      String val = node.asText().trim().toLowerCase();
      if ("true".equals(val) || "1".equals(val)) return true;
      if ("false".equals(val) || "0".equals(val)) return false;
    }
    return fallback;
  }

  private <T> List<T> toList(java.util.Collection<T> col) {
    return col != null ? new ArrayList<>(col) : List.of();
  }

  private Module findLiveModuleByTitle(String title, java.util.Collection<Module> liveModules) {
    if (title == null) return null;
    String normalized = title.trim().toLowerCase();
    for (Module m : liveModules) {
      if (m.getTitle() != null && m.getTitle().trim().toLowerCase().equals(normalized)) {
        return m;
      }
    }
    return null;
  }

  private ItemKind toItemKind(SnapshotItem si) {
    if (si instanceof SnapshotQuiz) return ItemKind.QUIZ;
    if (si instanceof SnapshotAssignment) return ItemKind.ASSIGNMENT;
    return ItemKind.LESSON;
  }

  private Object matchLiveItem(SnapshotItem si,
      Map<Long, Lesson> lessons, Map<Long, Quiz> quizzes, Map<Long, Assignment> assignments) {
    if (si.id() == null) return null;
    if (si instanceof SnapshotQuiz && quizzes.containsKey(si.id())) return quizzes.get(si.id());
    if (si instanceof SnapshotAssignment && assignments.containsKey(si.id())) return assignments.get(si.id());
    if (si instanceof SnapshotLesson && lessons.containsKey(si.id())) return lessons.get(si.id());
    return null;
  }

  private Object matchLiveItemByTitle(SnapshotItem si,
      List<Lesson> lessons, List<Quiz> quizzes, List<Assignment> assignments) {
    if (si.title() == null) return null;
    String normalized = si.title().trim().toLowerCase();
    if (si instanceof SnapshotQuiz) {
      return quizzes.stream()
          .filter(q -> q.getTitle() != null && q.getTitle().trim().toLowerCase().equals(normalized))
          .findFirst().orElse(null);
    }
    if (si instanceof SnapshotAssignment) {
      return assignments.stream()
          .filter(a -> a.getTitle() != null && a.getTitle().trim().toLowerCase().equals(normalized))
          .findFirst().orElse(null);
    }
    return lessons.stream()
        .filter(l -> l.getTitle() != null && l.getTitle().trim().toLowerCase().equals(normalized))
        .findFirst().orElse(null);
  }

  private Long getLiveItemId(Object liveItem) {
    if (liveItem instanceof Lesson l) return l.getId();
    if (liveItem instanceof Quiz q) return q.getId();
    if (liveItem instanceof Assignment a) return a.getId();
    return null;
  }

  private void removeFromLiveMaps(Object item,
      Map<Long, Lesson> lessons, Map<Long, Quiz> quizzes, Map<Long, Assignment> assignments) {
    if (item instanceof Lesson l) lessons.remove(l.getId());
    else if (item instanceof Quiz q) quizzes.remove(q.getId());
    else if (item instanceof Assignment a) assignments.remove(a.getId());
  }

  private FieldChangeList toFieldChangeList(List<FieldChange> changes) {
    FieldChange title = null, description = null, orderIndex = null;
    FieldChange contentText = null, durationSec = null, videoUrl = null, resourceUrl = null;
    FieldChange passScore = null, maxAttempts = null, timeLimitMinutes = null;
    FieldChange gradingMethod = null;
    FieldChange submissionType = null, maxScore = null, passingScore = null, isRequired = null;
    FieldChange lessonType = null;

    for (FieldChange fc : changes) {
      switch (fc.fieldName()) {
        case "title" -> title = fc;
        case "description" -> description = fc;
        case "orderIndex" -> orderIndex = fc;
        case "contentText" -> contentText = fc;
        case "durationSec" -> durationSec = fc;
        case "videoUrl" -> videoUrl = fc;
        case "resourceUrl" -> resourceUrl = fc;
        case "passScore" -> passScore = fc;
        case "maxAttempts" -> maxAttempts = fc;
        case "timeLimitMinutes" -> timeLimitMinutes = fc;
        case "gradingMethod" -> gradingMethod = fc;
        case "submissionType" -> submissionType = fc;
        case "maxScore" -> maxScore = fc;
        case "passingScore" -> passingScore = fc;
        case "isRequired" -> isRequired = fc;
        case "lessonType" -> lessonType = fc;
      }
    }

    return new FieldChangeList(
        title, description, orderIndex, contentText, durationSec, videoUrl, resourceUrl,
        passScore, maxAttempts, timeLimitMinutes, gradingMethod,
        submissionType, maxScore, passingScore, isRequired, lessonType
    );
  }

  // ─── Snapshot Model Records ─────────────────────────────────────────────

  private record SnapshotModule(
      Long id, String title, String description, Integer orderIndex, List<SnapshotItem> items
  ) {}

  private interface SnapshotItem {
    Long id();
    Long moduleId();
    String title();
    Integer orderIndex();
  }

  private record SnapshotLesson(
      Long id, Long moduleId, String title, Integer orderIndex,
      String lessonType, String contentText, Integer durationSec,
      String videoUrl, String resourceUrl
  ) implements SnapshotItem {}

  private record SnapshotQuiz(
      Long id, Long moduleId, String title, Integer orderIndex,
      String description, Integer passScore, Integer maxAttempts,
      Integer timeLimitMinutes, String gradingMethod
  ) implements SnapshotItem {}

  private record SnapshotAssignment(
      Long id, Long moduleId, String title, Integer orderIndex,
      String description, String submissionType,
      String maxScore, String passingScore, Boolean isRequired
  ) implements SnapshotItem {}
}
