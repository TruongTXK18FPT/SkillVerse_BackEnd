package com.exe.skillverse_backend.course_service.service.impl;

import com.exe.skillverse_backend.course_service.dto.assignmentdto.AssignmentCriteriaDTO;
import com.exe.skillverse_backend.course_service.dto.curriculumdto.CurriculumItemType;
import com.exe.skillverse_backend.course_service.dto.curriculumdto.CurriculumItemUpsertDTO;
import com.exe.skillverse_backend.course_service.dto.curriculumdto.CurriculumUpsertRequestDTO;
import com.exe.skillverse_backend.course_service.dto.curriculumdto.CurriculumUpsertResponseDTO;
import com.exe.skillverse_backend.course_service.dto.curriculumdto.ModuleUpsertDTO;
import com.exe.skillverse_backend.course_service.dto.quizdto.QuizQuestionCreateDTO;
import com.exe.skillverse_backend.course_service.dto.quizdto.QuizOptionCreateDTO;
import com.exe.skillverse_backend.course_service.entity.Assignment;
import com.exe.skillverse_backend.course_service.entity.AssignmentCriteria;
import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.course_service.entity.Lesson;
import com.exe.skillverse_backend.course_service.entity.Module;
import com.exe.skillverse_backend.course_service.entity.Quiz;
import com.exe.skillverse_backend.course_service.entity.QuizOption;
import com.exe.skillverse_backend.course_service.entity.QuizQuestion;
import com.exe.skillverse_backend.course_service.entity.enums.LessonType;
import com.exe.skillverse_backend.course_service.entity.enums.QuizGradingMethod;
import com.exe.skillverse_backend.course_service.entity.enums.SubmissionType;
import com.exe.skillverse_backend.course_service.repository.*;
import com.exe.skillverse_backend.course_service.service.CurriculumService;
import com.exe.skillverse_backend.shared.exception.AccessDeniedException;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CurriculumServiceImpl implements CurriculumService {

    private final CourseRepository courseRepository;
    private final ModuleRepository moduleRepository;
    private final LessonRepository lessonRepository;
    private final QuizRepository quizRepository;
    private final QuizQuestionRepository questionRepository;
    private final QuizOptionRepository optionRepository;
    private final AssignmentRepository assignmentRepository;
    private final AssignmentCriteriaRepository criteriaRepository;
    private final Clock clock;

    @Override
    @Transactional
    public CurriculumUpsertResponseDTO upsertCurriculum(Long courseId, CurriculumUpsertRequestDTO request, Long actorId) {
        Course course = getCourseOrThrow(courseId);
        ensureAuthorOrAdmin(actorId, course.getAuthor().getId());

        List<ModuleUpsertDTO> moduleDtos = request.getModules() != null ? request.getModules() : List.of();
        List<Module> existingModules = moduleRepository.findByCourseIdOrderByOrderIndexAsc(courseId);
        Map<Long, Module> existingModuleMap = existingModules.stream()
                .collect(Collectors.toMap(Module::getId, m -> m));

        Set<Long> keptModuleIds = new HashSet<>();
        List<ModuleUpsertDTO> responseModules = new ArrayList<>();

        for (int moduleIndex = 0; moduleIndex < moduleDtos.size(); moduleIndex++) {
            ModuleUpsertDTO moduleDto = moduleDtos.get(moduleIndex);
            if (moduleDto.getTitle() == null || moduleDto.getTitle().isBlank()) {
                throw new IllegalArgumentException("Module title is required");
            }

            Module module = null;
            if (moduleDto.getId() != null) {
                module = existingModuleMap.get(moduleDto.getId());
                if (module == null) {
                    throw new NotFoundException("MODULE_NOT_FOUND");
                }
                if (!module.getCourse().getId().equals(courseId)) {
                    throw new IllegalArgumentException("MODULE_NOT_IN_COURSE");
                }
            } else {
                module = new Module();
                module.setCourse(course);
            }

            Integer orderIndex = moduleDto.getOrderIndex() != null ? moduleDto.getOrderIndex() : moduleIndex;
            module.setTitle(moduleDto.getTitle().trim());
            module.setDescription(moduleDto.getDescription() != null ? moduleDto.getDescription().trim() : null);
            module.setOrderIndex(orderIndex);

            Module savedModule = moduleRepository.save(module);
            keptModuleIds.add(savedModule.getId());

            List<CurriculumItemUpsertDTO> itemResponses = upsertModuleItems(
                    savedModule,
                    moduleDto.getItems(),
                    request.isReplaceMissing()
            );

            responseModules.add(new ModuleUpsertDTO(
                    savedModule.getId(),
                    moduleDto.getClientId(),
                    savedModule.getTitle(),
                    savedModule.getDescription(),
                    savedModule.getOrderIndex(),
                    itemResponses
            ));
        }

        if (request.isReplaceMissing()) {
            List<Module> toDelete = existingModules.stream()
                    .filter(m -> !keptModuleIds.contains(m.getId()))
                    .toList();
            if (!toDelete.isEmpty()) {
                moduleRepository.deleteAll(toDelete);
            }
        }

        return new CurriculumUpsertResponseDTO(responseModules);
    }

    private List<CurriculumItemUpsertDTO> upsertModuleItems(
            Module module,
            List<CurriculumItemUpsertDTO> items,
            boolean replaceMissing
    ) {
        List<CurriculumItemUpsertDTO> itemDtos = items != null ? items : List.of();
        Map<Long, Lesson> existingLessons = lessonRepository.findByModuleIdOrderByOrderIndexAsc(module.getId())
                .stream()
                .collect(Collectors.toMap(Lesson::getId, l -> l));
        Map<Long, Quiz> existingQuizzes = quizRepository.findByModuleId(module.getId())
                .stream()
                .collect(Collectors.toMap(Quiz::getId, q -> q));
        Map<Long, Assignment> existingAssignments = assignmentRepository.findByModuleId(module.getId())
                .stream()
                .collect(Collectors.toMap(Assignment::getId, a -> a));

        Set<Long> keptLessonIds = new HashSet<>();
        Set<Long> keptQuizIds = new HashSet<>();
        Set<Long> keptAssignmentIds = new HashSet<>();

        List<CurriculumItemUpsertDTO> responseItems = new ArrayList<>();

        for (int itemIndex = 0; itemIndex < itemDtos.size(); itemIndex++) {
            CurriculumItemUpsertDTO item = itemDtos.get(itemIndex);
            if (item.getType() == null) {
                throw new IllegalArgumentException("Curriculum item type is required");
            }
            Integer orderIndex = item.getOrderIndex() != null ? item.getOrderIndex() : itemIndex;

            if (item.getType() == CurriculumItemType.LESSON) {
                Lesson lesson = upsertLesson(module, item, orderIndex, existingLessons);
                keptLessonIds.add(lesson.getId());
                responseItems.add(buildLessonResponse(item, lesson));
            } else if (item.getType() == CurriculumItemType.QUIZ) {
                Quiz quiz = upsertQuiz(module, item, orderIndex, existingQuizzes);
                keptQuizIds.add(quiz.getId());
                List<QuizQuestionCreateDTO> questionResponses = item.getQuestions() != null
                        ? upsertQuizQuestions(quiz, item.getQuestions(), replaceMissing)
                        : null;
                responseItems.add(buildQuizResponse(item, quiz, questionResponses));
            } else if (item.getType() == CurriculumItemType.ASSIGNMENT) {
                Assignment assignment = upsertAssignment(module, item, orderIndex, existingAssignments);
                keptAssignmentIds.add(assignment.getId());
                List<AssignmentCriteriaDTO> criteriaResponses = item.getCriteria() != null
                        ? upsertAssignmentCriteria(assignment, item.getCriteria(), replaceMissing)
                        : null;
                responseItems.add(buildAssignmentResponse(item, assignment, criteriaResponses));
            } else {
                throw new IllegalArgumentException("Unsupported curriculum item type: " + item.getType());
            }
        }

        if (replaceMissing) {
            List<Lesson> lessonsToDelete = existingLessons.values().stream()
                    .filter(l -> !keptLessonIds.contains(l.getId()))
                    .toList();
            if (!lessonsToDelete.isEmpty()) {
                lessonRepository.deleteAll(lessonsToDelete);
            }

            List<Quiz> quizzesToDelete = existingQuizzes.values().stream()
                    .filter(q -> !keptQuizIds.contains(q.getId()))
                    .toList();
            if (!quizzesToDelete.isEmpty()) {
                quizRepository.deleteAll(quizzesToDelete);
            }

            List<Assignment> assignmentsToDelete = existingAssignments.values().stream()
                    .filter(a -> !keptAssignmentIds.contains(a.getId()))
                    .toList();
            if (!assignmentsToDelete.isEmpty()) {
                assignmentRepository.deleteAll(assignmentsToDelete);
            }
        }

        return responseItems;
    }

    private Lesson upsertLesson(
            Module module,
            CurriculumItemUpsertDTO item,
            Integer orderIndex,
            Map<Long, Lesson> existingLessons
    ) {
        LessonType lessonType = item.getLessonType();
        if (lessonType == null) {
            throw new IllegalArgumentException("lessonType is required for LESSON items");
        }

        Lesson lesson;
        if (item.getId() != null) {
            lesson = existingLessons.get(item.getId());
            if (lesson == null) {
                throw new NotFoundException("LESSON_NOT_FOUND");
            }
        } else {
            lesson = new Lesson();
            lesson.setModule(module);
        }

        lesson.setTitle(item.getTitle() != null ? item.getTitle().trim() : "Untitled lesson");
        lesson.setType(lessonType);
        lesson.setOrderIndex(orderIndex);
        lesson.setDurationSec(item.getDurationSec());

        if (lessonType == LessonType.READING) {
            String content = item.getContentText() != null ? item.getContentText() : item.getDescription();
            lesson.setContentText(content);
            lesson.setResourceUrl(item.getResourceUrl());
            lesson.setVideoUrl(null);
        } else if (lessonType == LessonType.VIDEO) {
            lesson.setVideoUrl(item.getVideoUrl());
            lesson.setContentText(null);
            lesson.setResourceUrl(null);
        } else {
            lesson.setContentText(item.getContentText());
            lesson.setVideoUrl(item.getVideoUrl());
            lesson.setResourceUrl(item.getResourceUrl());
        }

        return lessonRepository.save(lesson);
    }

    private Quiz upsertQuiz(
            Module module,
            CurriculumItemUpsertDTO item,
            Integer orderIndex,
            Map<Long, Quiz> existingQuizzes
    ) {
        Quiz quiz;
        if (item.getId() != null) {
            quiz = existingQuizzes.get(item.getId());
            if (quiz == null) {
                throw new NotFoundException("QUIZ_NOT_FOUND");
            }
        } else {
            quiz = new Quiz();
            quiz.setModule(module);
            quiz.setCreatedAt(now());
        }

        quiz.setTitle(item.getTitle() != null ? item.getTitle().trim() : "Untitled quiz");
        quiz.setDescription(item.getDescription());
        quiz.setPassScore(item.getPassScore());
        quiz.setMaxAttempts(item.getMaxAttempts());
        quiz.setTimeLimitMinutes(item.getTimeLimitMinutes());
        quiz.setRoundingIncrement(item.getRoundingIncrement());
        quiz.setGradingMethod(item.getGradingMethod());
        quiz.setIsAssessment(item.getIsAssessment());
        quiz.setCooldownHours(item.getCooldownHours());
        quiz.setOrderIndex(orderIndex);
        quiz.setUpdatedAt(now());

        applyQuizDefaults(quiz);

        Quiz savedQuiz = quizRepository.save(quiz);
        return savedQuiz;
    }

    private Assignment upsertAssignment(
            Module module,
            CurriculumItemUpsertDTO item,
            Integer orderIndex,
            Map<Long, Assignment> existingAssignments
    ) {
        Assignment assignment;
        if (item.getId() != null) {
            assignment = existingAssignments.get(item.getId());
            if (assignment == null) {
                throw new NotFoundException("ASSIGNMENT_NOT_FOUND");
            }
        } else {
            assignment = new Assignment();
            assignment.setModule(module);
            assignment.setCreatedAt(now());
        }

        assignment.setTitle(item.getTitle() != null ? item.getTitle().trim() : "Untitled assignment");
        assignment.setDescription(item.getDescription());
        assignment.setSubmissionType(item.getSubmissionType() != null ? item.getSubmissionType() : SubmissionType.TEXT);
        assignment.setMaxScore(item.getMaxScore() != null ? item.getMaxScore() : BigDecimal.valueOf(100));
        assignment.setPassingScore(item.getPassingScore());
        assignment.setDueAt(item.getDueAt());
        assignment.setOrderIndex(orderIndex);
        assignment.setUpdatedAt(now());

        Assignment savedAssignment = assignmentRepository.save(assignment);
        return savedAssignment;
    }

    private List<QuizQuestionCreateDTO> upsertQuizQuestions(
            Quiz quiz,
            List<QuizQuestionCreateDTO> questionDtos,
            boolean replaceMissing
    ) {
        List<QuizQuestionCreateDTO> questions = questionDtos != null ? questionDtos : List.of();
        List<QuizQuestion> existingQuestions = questionRepository.findByQuizIdWithOptions(quiz.getId());
        Map<Long, QuizQuestion> existingMap = existingQuestions.stream()
                .filter(q -> q.getId() != null)
                .collect(Collectors.toMap(QuizQuestion::getId, q -> q));

        Set<Long> keepIds = new HashSet<>();
        List<QuestionMapping> mappings = new ArrayList<>();

        for (int index = 0; index < questions.size(); index++) {
            QuizQuestionCreateDTO questionDto = questions.get(index);
            if (questionDto.getQuestionText() == null || questionDto.getQuestionText().isBlank()) {
                continue;
            }

            QuizQuestion question = null;
            if (questionDto.getId() != null) {
                question = existingMap.get(questionDto.getId());
            }
            if (question == null) {
                question = new QuizQuestion();
                question.setQuiz(quiz);
            }

            question.setQuestionText(questionDto.getQuestionText().trim());
            question.setQuestionType(questionDto.getQuestionType());
            question.setScore(questionDto.getScore());
            question.setOrderIndex(questionDto.getOrderIndex() != null ? questionDto.getOrderIndex() : index);

            List<OptionMapping> optionMappings = upsertQuizOptions(question, questionDto.getOptions(), replaceMissing);
            question.setOptions(optionMappings.stream().map(OptionMapping::entity).toList());

            QuizQuestion savedQuestion = questionRepository.save(question);
            if (savedQuestion.getId() != null) {
                keepIds.add(savedQuestion.getId());
            }
            mappings.add(new QuestionMapping(questionDto, savedQuestion, optionMappings));
        }

        if (replaceMissing && !existingQuestions.isEmpty()) {
            List<QuizQuestion> toDelete = existingQuestions.stream()
                    .filter(q -> q.getId() != null && !keepIds.contains(q.getId()))
                    .toList();
            if (!toDelete.isEmpty()) {
                questionRepository.deleteAll(toDelete);
            }
        }

        List<QuizQuestionCreateDTO> response = new ArrayList<>();
        for (QuestionMapping mapping : mappings) {
            QuizQuestionCreateDTO dto = new QuizQuestionCreateDTO();
            dto.setId(mapping.entity().getId());
            dto.setClientId(mapping.source().getClientId());
            dto.setQuestionText(mapping.entity().getQuestionText());
            dto.setQuestionType(mapping.entity().getQuestionType());
            dto.setScore(mapping.entity().getScore());
            dto.setOrderIndex(mapping.entity().getOrderIndex());

            List<QuizOptionCreateDTO> options = new ArrayList<>();
            for (OptionMapping optionMapping : mapping.optionMappings()) {
                QuizOptionCreateDTO optionDto = new QuizOptionCreateDTO();
                optionDto.setId(optionMapping.entity().getId());
                optionDto.setClientId(optionMapping.source().getClientId());
                optionDto.setOptionText(optionMapping.entity().getOptionText());
                optionDto.setCorrect(Boolean.TRUE.equals(optionMapping.entity().getIsCorrect()));
                optionDto.setFeedback(optionMapping.entity().getFeedback());
                optionDto.setOrderIndex(optionMapping.entity().getOrderIndex());
                options.add(optionDto);
            }
            dto.setOptions(options);
            response.add(dto);
        }

        return response;
    }

    private List<OptionMapping> upsertQuizOptions(
            QuizQuestion question,
            List<QuizOptionCreateDTO> optionDtos,
            boolean replaceMissing
    ) {
        List<QuizOptionCreateDTO> options = optionDtos != null ? optionDtos : List.of();
        List<QuizOption> existingOptions = question.getOptions() != null
                ? new ArrayList<>(question.getOptions())
                : new ArrayList<>();
        Map<Long, QuizOption> existingMap = existingOptions.stream()
                .filter(option -> option.getId() != null)
                .collect(Collectors.toMap(QuizOption::getId, option -> option));

        List<OptionMapping> mappings = new ArrayList<>();
        List<QuizOption> mergedOptions = new ArrayList<>();

        for (int index = 0; index < options.size(); index++) {
            QuizOptionCreateDTO optionDto = options.get(index);
            if (optionDto.getOptionText() == null || optionDto.getOptionText().isBlank()) {
                continue;
            }

            QuizOption option = null;
            if (optionDto.getId() != null) {
                option = existingMap.get(optionDto.getId());
            }
            if (option == null) {
                option = new QuizOption();
            }

            option.setQuestion(question);
            option.setOptionText(optionDto.getOptionText().trim());
            option.setIsCorrect(optionDto.isCorrect());
            option.setFeedback(optionDto.getFeedback());
            option.setOrderIndex(optionDto.getOrderIndex() != null ? optionDto.getOrderIndex() : index);
            mergedOptions.add(option);
            mappings.add(new OptionMapping(optionDto, option));
        }

        if (!replaceMissing && !existingOptions.isEmpty()) {
            Set<Long> mergedIds = mergedOptions.stream()
                    .map(QuizOption::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            existingOptions.stream()
                    .filter(option -> option.getId() != null && !mergedIds.contains(option.getId()))
                    .forEach(mergedOptions::add);
        }

        question.setOptions(mergedOptions);
        return mappings;
    }

    private List<AssignmentCriteriaDTO> upsertAssignmentCriteria(
            Assignment assignment,
            List<AssignmentCriteriaDTO> criteriaDtos,
            boolean replaceMissing
    ) {
        List<AssignmentCriteriaDTO> criteria = criteriaDtos != null ? criteriaDtos : List.of();
        List<AssignmentCriteria> existingCriteria = criteriaRepository.findByAssignmentIdOrderByOrderIndexAsc(assignment.getId());
        Map<Long, AssignmentCriteria> existingMap = existingCriteria.stream()
                .filter(c -> c.getId() != null)
                .collect(Collectors.toMap(AssignmentCriteria::getId, c -> c));

        Set<Long> keepIds = new HashSet<>();
        List<AssignmentCriteriaDTO> response = new ArrayList<>();

        for (int index = 0; index < criteria.size(); index++) {
            AssignmentCriteriaDTO dto = criteria.get(index);
            if (dto.getName() == null || dto.getName().isBlank()) {
                continue;
            }

            AssignmentCriteria entity = null;
            if (dto.getId() != null) {
                entity = existingMap.get(dto.getId());
            }
            if (entity == null) {
                entity = new AssignmentCriteria();
                entity.setAssignment(assignment);
            }

            entity.setName(dto.getName().trim());
            entity.setDescription(dto.getDescription());
            entity.setMaxPoints(dto.getMaxPoints() != null ? dto.getMaxPoints() : BigDecimal.valueOf(0));
            entity.setOrderIndex(dto.getOrderIndex() != null ? dto.getOrderIndex() : index);
            entity.setRequired(dto.isRequired());

            AssignmentCriteria saved = criteriaRepository.save(entity);
            if (saved.getId() != null) {
                keepIds.add(saved.getId());
            }

            AssignmentCriteriaDTO responseDto = new AssignmentCriteriaDTO();
            responseDto.setId(saved.getId());
            responseDto.setClientId(dto.getClientId());
            responseDto.setName(saved.getName());
            responseDto.setDescription(saved.getDescription());
            responseDto.setMaxPoints(saved.getMaxPoints());
            responseDto.setOrderIndex(saved.getOrderIndex());
            responseDto.setRequired(saved.isRequired());
            response.add(responseDto);
        }

        if (replaceMissing && !existingCriteria.isEmpty()) {
            List<AssignmentCriteria> toDelete = existingCriteria.stream()
                    .filter(c -> c.getId() != null && !keepIds.contains(c.getId()))
                    .toList();
            if (!toDelete.isEmpty()) {
                criteriaRepository.deleteAll(toDelete);
            }
        }

        return response;
    }

    private CurriculumItemUpsertDTO buildLessonResponse(CurriculumItemUpsertDTO source, Lesson lesson) {
        CurriculumItemUpsertDTO response = new CurriculumItemUpsertDTO();
        response.setId(lesson.getId());
        response.setClientId(source.getClientId());
        response.setType(CurriculumItemType.LESSON);
        response.setTitle(lesson.getTitle());
        response.setOrderIndex(lesson.getOrderIndex());
        response.setLessonType(lesson.getType());
        response.setDurationSec(lesson.getDurationSec());
        response.setContentText(lesson.getContentText());
        response.setResourceUrl(lesson.getResourceUrl());
        response.setVideoUrl(lesson.getVideoUrl());
        return response;
    }

    private CurriculumItemUpsertDTO buildQuizResponse(
            CurriculumItemUpsertDTO source,
            Quiz quiz,
            List<QuizQuestionCreateDTO> questionResponses
    ) {
        CurriculumItemUpsertDTO response = new CurriculumItemUpsertDTO();
        response.setId(quiz.getId());
        response.setClientId(source.getClientId());
        response.setType(CurriculumItemType.QUIZ);
        response.setTitle(quiz.getTitle());
        response.setDescription(quiz.getDescription());
        response.setOrderIndex(quiz.getOrderIndex());
        response.setPassScore(quiz.getPassScore());
        response.setMaxAttempts(quiz.getMaxAttempts());
        response.setTimeLimitMinutes(quiz.getTimeLimitMinutes());
        response.setRoundingIncrement(quiz.getRoundingIncrement());
        response.setGradingMethod(quiz.getGradingMethod());
        response.setIsAssessment(quiz.getIsAssessment());
        response.setCooldownHours(quiz.getCooldownHours());
        response.setQuestions(questionResponses);
        return response;
    }

    private CurriculumItemUpsertDTO buildAssignmentResponse(
            CurriculumItemUpsertDTO source,
            Assignment assignment,
            List<AssignmentCriteriaDTO> criteriaResponses
    ) {
        CurriculumItemUpsertDTO response = new CurriculumItemUpsertDTO();
        response.setId(assignment.getId());
        response.setClientId(source.getClientId());
        response.setType(CurriculumItemType.ASSIGNMENT);
        response.setTitle(assignment.getTitle());
        response.setDescription(assignment.getDescription());
        response.setOrderIndex(assignment.getOrderIndex());
        response.setSubmissionType(assignment.getSubmissionType());
        response.setMaxScore(assignment.getMaxScore());
        response.setPassingScore(assignment.getPassingScore());
        response.setDueAt(assignment.getDueAt());
        response.setCriteria(criteriaResponses);
        return response;
    }

    private record OptionMapping(QuizOptionCreateDTO source, QuizOption entity) {}

    private record QuestionMapping(
            QuizQuestionCreateDTO source,
            QuizQuestion entity,
            List<OptionMapping> optionMappings
    ) {}

    private void applyQuizDefaults(Quiz quiz) {
        if (quiz.getMaxAttempts() == null) {
            quiz.setMaxAttempts(3);
        }
        if (quiz.getRoundingIncrement() == null || quiz.getRoundingIncrement() <= 0) {
            quiz.setRoundingIncrement(1);
        }
        if (quiz.getGradingMethod() == null) {
            quiz.setGradingMethod(QuizGradingMethod.HIGHEST);
        }
    }

    private Course getCourseOrThrow(Long id) {
        return courseRepository.findById(id).orElseThrow(() -> new NotFoundException("COURSE_NOT_FOUND"));
    }

    private void ensureAuthorOrAdmin(Long actorId, Long authorId) {
        if (!actorId.equals(authorId)) {
            throw new AccessDeniedException("FORBIDDEN");
        }
    }

    private Instant now() {
        return Instant.now(clock);
    }
}
