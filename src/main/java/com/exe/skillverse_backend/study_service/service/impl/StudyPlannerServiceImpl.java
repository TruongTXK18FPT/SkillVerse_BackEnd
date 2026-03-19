package com.exe.skillverse_backend.study_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.study_service.dto.request.CreateStudySessionRequest;
import com.exe.skillverse_backend.study_service.dto.response.StudySessionResponse;
import com.exe.skillverse_backend.study_service.entity.StudySession;
import com.exe.skillverse_backend.study_service.entity.StudySessionStatus;
import com.exe.skillverse_backend.study_service.entity.Task;
import com.exe.skillverse_backend.study_service.entity.TaskColumn;
import com.exe.skillverse_backend.study_service.entity.TaskPriority;
import com.exe.skillverse_backend.study_service.repository.StudySessionRepository;
import com.exe.skillverse_backend.study_service.repository.TaskColumnRepository;
import com.exe.skillverse_backend.study_service.repository.TaskRepository;
import com.exe.skillverse_backend.study_service.service.StudyPlannerService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StudyPlannerServiceImpl implements StudyPlannerService {

    private final StudySessionRepository studySessionRepository;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final TaskColumnRepository taskColumnRepository;

    @Override
    @Transactional
    public StudySessionResponse createSession(Long userId, CreateStudySessionRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        StudySession session = StudySession.builder()
                .title(request.getTitle())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .fullDescription(request.getDescription())
                .status(StudySessionStatus.SCHEDULED)
                .user(user)
                .build();

        StudySession savedSession = studySessionRepository.save(session);
        
        // Create corresponding task
        createTaskForSession(user, savedSession);
        
        return mapToResponse(savedSession);
    }

    @Override
    @Transactional
    public List<StudySessionResponse> createSessions(Long userId, List<CreateStudySessionRequest> requests) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<StudySession> sessions = requests.stream().map(request -> StudySession.builder()
                .title(request.getTitle())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .fullDescription(request.getDescription())
                .status(StudySessionStatus.SCHEDULED)
                .user(user)
                .build()).collect(Collectors.toList());

        List<StudySession> savedSessions = studySessionRepository.saveAll(sessions);
        
        // Create corresponding tasks
        TaskColumn targetColumn = getOrCreateDefaultColumn(user);
        List<Task> tasks = savedSessions.stream()
                .map(session -> buildTaskForSession(user, session, targetColumn))
                .collect(Collectors.toList());
        taskRepository.saveAll(tasks);

        return savedSessions.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    private void createTaskForSession(User user, StudySession session) {
        TaskColumn targetColumn = getOrCreateDefaultColumn(user);
        Task task = buildTaskForSession(user, session, targetColumn);
        taskRepository.save(task);
    }

    private Task buildTaskForSession(User user, StudySession session, TaskColumn column) {
        return Task.builder()
                .title(session.getTitle())
                .fullDescription(session.getFullDescription())
                .startDate(session.getStartTime())
                .endDate(session.getEndTime())
                .deadline(session.getEndTime())
                .priority(TaskPriority.MEDIUM)
                .status(column.getName())
                .column(column)
                .user(user)
                .linkedSessions(new ArrayList<>(List.of(session)))
                .userProgress(0)
                .build();
    }

    private TaskColumn getOrCreateDefaultColumn(User user) {
        return taskColumnRepository.findByNameAndUserId("To Do", user.getId())
                .or(() -> taskColumnRepository.findByUserIdOrderByOrderIndexAsc(user.getId()).stream().findFirst())
                .orElseGet(() -> {
                     TaskColumn newCol = TaskColumn.builder()
                         .name("To Do")
                         .user(user)
                         .orderIndex(0)
                         .color("#3498db")
                         .build();
                     return taskColumnRepository.save(newCol);
                });
    }

    @Override
    public List<StudySessionResponse> getSessions(Long userId) {
        return studySessionRepository.findByUserId(userId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<StudySessionResponse> getSessionsInRange(Long userId, LocalDateTime start, LocalDateTime end) {
        return studySessionRepository.findByUserIdAndStartTimeBetween(userId, start, end).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public StudySessionResponse updateStatus(UUID sessionId, StudySessionStatus status) {
        StudySession session = studySessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));
        session.setStatus(status);
        StudySession savedSession = studySessionRepository.save(session);

        // Check if we need to update linked tasks
        if (status == StudySessionStatus.COMPLETED) {
            checkAndCompleteLinkedTasks(sessionId);
        }

        return mapToResponse(savedSession);
    }

    private void checkAndCompleteLinkedTasks(UUID sessionId) {
        // Find tasks that are linked to this session
        List<Task> linkedTasks = taskRepository.findByLinkedSessions_Id(sessionId);
        
        for (Task task : linkedTasks) {
            // Check if all sessions linked to this task are completed
            boolean allSessionsCompleted = task.getLinkedSessions().stream()
                    .allMatch(s -> s.getStatus() == StudySessionStatus.COMPLETED);
            
            if (allSessionsCompleted) {
                // Move task to "Done" column
                taskColumnRepository.findByNameAndUserId("Done", task.getUser().getId())
                        .ifPresent(doneColumn -> {
                            task.setColumn(doneColumn);
                            task.setStatus("Done");
                            taskRepository.save(task);
                        });
            }
        }
    }

    @Override
    @Transactional
    public void deleteSession(UUID sessionId) {
        studySessionRepository.deleteById(sessionId);
    }

    private StudySessionResponse mapToResponse(StudySession session) {
        return StudySessionResponse.builder()
                .id(session.getId())
                .title(session.getTitle())
                .startTime(session.getStartTime())
                .endTime(session.getEndTime())
                .status(session.getStatus())
                .description(session.getFullDescription())
                .build();
    }
}
