package com.exe.skillverse_backend.study_service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.study_service.dto.request.CreateStudySessionRequest;
import com.exe.skillverse_backend.study_service.dto.request.CreateTaskRequest;
import com.exe.skillverse_backend.study_service.dto.request.UpdateTaskRequest;
import com.exe.skillverse_backend.study_service.dto.response.StudySessionResponse;
import com.exe.skillverse_backend.study_service.dto.response.TaskColumnResponse;
import com.exe.skillverse_backend.study_service.dto.response.TaskResponse;
import com.exe.skillverse_backend.study_service.entity.StudySessionStatus;
import com.exe.skillverse_backend.study_service.entity.TaskPriority;
import com.exe.skillverse_backend.study_service.service.impl.StudyPlannerServiceImpl;
import com.exe.skillverse_backend.study_service.service.impl.TaskBoardServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;
import com.exe.skillverse_backend.portfolio_service.service.impl.CVGeneratorAIServiceImpl;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@SpringBootTest
@Transactional
public class TaskBoardServiceTest {

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private TaskBoardServiceImpl taskBoardService;

    @Autowired
    private StudyPlannerServiceImpl studyPlannerService;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private CVGeneratorAIServiceImpl cvGeneratorAIService;

    private Long userId;

    @BeforeEach
    void setup() {
        User u = User.builder()
                .email("planner@example.com")
                .password("password")
                .status(com.exe.skillverse_backend.auth_service.entity.UserStatus.ACTIVE)
                .build();
        userId = userRepository.save(u).getId();
    }

    @Test
    void get_board_initializes_default_columns() {
        List<TaskColumnResponse> board = taskBoardService.getBoard(userId);
        assertFalse(board.isEmpty());
        assertEquals(4, board.size());
        assertEquals("To Do", board.get(0).getName());
        assertEquals("In Progress", board.get(1).getName());
        assertEquals("Done", board.get(2).getName());
        assertEquals("Overdue", board.get(3).getName());
    }

    @Test
    void create_task_and_move() {
        List<TaskColumnResponse> board = taskBoardService.getBoard(userId);
        UUID todoColumnId = board.get(0).getId();
        UUID inProgressColumnId = board.get(1).getId();

        CreateTaskRequest request = new CreateTaskRequest();
        request.setTitle("Complete Unit Test");
        request.setDescription("Write tests for TaskBoardService");
        request.setColumnId(todoColumnId);
        request.setPriority(TaskPriority.HIGH);
        request.setDeadline(LocalDateTime.now().plusDays(1));

        TaskResponse task = taskBoardService.createTask(userId, request);
        assertNotNull(task.getId());
        assertEquals("Complete Unit Test", task.getTitle());
        assertEquals(todoColumnId, task.getColumnId());

        // Move task
        taskBoardService.moveTask(task.getId(), inProgressColumnId);
        
        entityManager.flush();
        entityManager.clear();

        // Verify move by getting board again
        board = taskBoardService.getBoard(userId);
        TaskColumnResponse inProgress = board.get(1);
        boolean taskFound = inProgress.getTasks().stream()
                .anyMatch(t -> t.getId().equals(task.getId()));
        assertTrue(taskFound);
    }

    @Test
    void task_auto_complete_when_sessions_completed() {
        // Ensure board is initialized with all columns (including Done)
        taskBoardService.getBoard(userId);

        // 1. Create a Session
        CreateStudySessionRequest sessionRequest = new CreateStudySessionRequest();
        sessionRequest.setTitle("Study for Task");
        sessionRequest.setStartTime(LocalDateTime.now());
        sessionRequest.setEndTime(LocalDateTime.now().plusHours(1));
        StudySessionResponse session = studyPlannerService.createSession(userId, sessionRequest);

        // 2. Create a Task linked to that Session
        List<TaskColumnResponse> board = taskBoardService.getBoard(userId);
        UUID todoColumnId = board.get(0).getId();

        CreateTaskRequest taskRequest = new CreateTaskRequest();
        taskRequest.setTitle("Linked Task");
        taskRequest.setColumnId(todoColumnId);
        taskRequest.setLinkedSessionIds(Collections.singletonList(session.getId()));
        
        TaskResponse task = taskBoardService.createTask(userId, taskRequest);
        
        // 3. Complete the Session
        studyPlannerService.updateStatus(session.getId(), StudySessionStatus.COMPLETED);

        entityManager.flush();
        entityManager.clear();

        // 4. Verify Task is moved to Done
        // We need to fetch the task again to check its status/column
        // Since we don't have getTaskById exposed in service interface easily for test, we can check the board
        board = taskBoardService.getBoard(userId);
        TaskColumnResponse doneColumn = board.stream()
                .filter(c -> "Done".equals(c.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Done column not found"));
        
        boolean taskIsDone = doneColumn.getTasks().stream()
                .anyMatch(t -> t.getId().equals(task.getId()));
        
        assertTrue(taskIsDone, "Task should be moved to Done column after session completion");
    }
}
