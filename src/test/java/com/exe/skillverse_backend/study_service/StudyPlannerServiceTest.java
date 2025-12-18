package com.exe.skillverse_backend.study_service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.study_service.dto.request.CreateStudySessionRequest;
import com.exe.skillverse_backend.study_service.dto.response.StudySessionResponse;
import com.exe.skillverse_backend.study_service.entity.StudySessionStatus;
import com.exe.skillverse_backend.study_service.entity.StudySession;
import com.exe.skillverse_backend.study_service.repository.StudySessionRepository;
import com.exe.skillverse_backend.study_service.service.impl.StudyPlannerServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;
import com.exe.skillverse_backend.portfolio_service.service.impl.CVGeneratorAIServiceImpl;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class StudyPlannerServiceTest {

    @Autowired
    private StudyPlannerServiceImpl studyPlannerService;

    @Autowired
    private StudySessionRepository studySessionRepository;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private CVGeneratorAIServiceImpl cvGeneratorAIService;

    private Long userId;

    @BeforeEach
    void setup() {
        User u = User.builder()
                .email("student@example.com")
                .password("password")
                .status(com.exe.skillverse_backend.auth_service.entity.UserStatus.ACTIVE)
                .build();
        userId = userRepository.save(u).getId();
    }

    @Test
    void create_and_get_sessions() {
        CreateStudySessionRequest request = new CreateStudySessionRequest();
        request.setTitle("Learn Java");
        request.setDescription("Study Streams API");
        request.setStartTime(LocalDateTime.now().plusHours(1));
        request.setEndTime(LocalDateTime.now().plusHours(2));

        StudySessionResponse response = studyPlannerService.createSession(userId, request);
        assertNotNull(response.getId());
        assertEquals("Learn Java", response.getTitle());
        assertEquals(StudySessionStatus.SCHEDULED, response.getStatus());

        List<StudySessionResponse> sessions = studyPlannerService.getSessions(userId);
        assertFalse(sessions.isEmpty());
        assertEquals(1, sessions.size());
        assertEquals(response.getId(), sessions.get(0).getId());
    }

    @Test
    void update_session_status() {
        CreateStudySessionRequest request = new CreateStudySessionRequest();
        request.setTitle("Learn Spring");
        request.setStartTime(LocalDateTime.now());
        request.setEndTime(LocalDateTime.now().plusHours(1));
        StudySessionResponse created = studyPlannerService.createSession(userId, request);

        StudySessionResponse updated = studyPlannerService.updateStatus(created.getId(), StudySessionStatus.COMPLETED);
        assertEquals(StudySessionStatus.COMPLETED, updated.getStatus());
    }

    @Test
    void delete_session() {
        // Manually create and save a session to ensure it's persisted before delete
        // This avoids TransientObjectException if references exist
        StudySession session = StudySession.builder()
                .title("To Delete")
                .startTime(LocalDateTime.now())
                .endTime(LocalDateTime.now().plusHours(1))
                .status(StudySessionStatus.SCHEDULED)
                .user(userRepository.findById(userId).orElseThrow())
                .build();
        studySessionRepository.save(session);

        studyPlannerService.deleteSession(session.getId());
        
        List<StudySessionResponse> sessions = studyPlannerService.getSessions(userId);
        assertTrue(sessions.isEmpty());
    }
}
