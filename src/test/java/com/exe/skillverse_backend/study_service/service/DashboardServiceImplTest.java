package com.exe.skillverse_backend.study_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.study_service.entity.DashboardNote;
import com.exe.skillverse_backend.study_service.repository.DashboardNoteRepository;
import com.exe.skillverse_backend.study_service.service.impl.DashboardServiceImpl;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceImplTest {

    @Mock
    private DashboardNoteRepository dashboardNoteRepository;

    @Mock
    private UserRepository userRepository;

    private DashboardServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DashboardServiceImpl(dashboardNoteRepository, userRepository);
        lenient().when(dashboardNoteRepository.save(any(DashboardNote.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("createNote should attach the note to an existing user")
    void createNote_ShouldAttachNoteToExistingUser() {
        User user = User.builder().id(14L).email("learner@skillverse.vn").build();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        service.createNote(user.getId(), "Practice system design");

        ArgumentCaptor<DashboardNote> captor = ArgumentCaptor.forClass(DashboardNote.class);
        verify(dashboardNoteRepository).save(captor.capture());
        assertEquals(user, captor.getValue().getUser());
        assertEquals("Practice system design", captor.getValue().getContent());
    }

    @Test
    @DisplayName("createNote should reject unknown users")
    void createNote_ShouldRejectUnknownUsers() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> service.createNote(99L, "Missing user"));
    }

    @Test
    @DisplayName("updateNote should mutate note content and save it")
    void updateNote_ShouldMutateContentAndSaveIt() {
        UUID noteId = UUID.randomUUID();
        DashboardNote note = DashboardNote.builder().id(noteId).content("Old content").build();
        when(dashboardNoteRepository.findById(noteId)).thenReturn(Optional.of(note));

        DashboardNote updated = service.updateNote(noteId, "New content");

        assertEquals("New content", updated.getContent());
        verify(dashboardNoteRepository).save(note);
    }

    @Test
    @DisplayName("getUserNotes should delegate to the repository")
    void getUserNotes_ShouldDelegateToRepository() {
        DashboardNote note = DashboardNote.builder().content("Pinned note").build();
        when(dashboardNoteRepository.findByUser_Id(15L)).thenReturn(List.of(note));

        List<DashboardNote> notes = service.getUserNotes(15L);

        assertEquals(1, notes.size());
        assertEquals("Pinned note", notes.get(0).getContent());
    }
}
