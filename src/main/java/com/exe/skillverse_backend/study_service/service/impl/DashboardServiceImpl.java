package com.exe.skillverse_backend.study_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.study_service.entity.DashboardNote;
import com.exe.skillverse_backend.study_service.repository.DashboardNoteRepository;
import com.exe.skillverse_backend.study_service.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final DashboardNoteRepository dashboardNoteRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<DashboardNote> getUserNotes(Long userId) {
        return dashboardNoteRepository.findByUser_Id(userId);
    }

    @Override
    @Transactional
    public DashboardNote createNote(Long userId, String content) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        DashboardNote note = DashboardNote.builder()
                .content(content)
                .user(user)
                .build();
        
        return dashboardNoteRepository.save(note);
    }

    @Override
    @Transactional
    public DashboardNote updateNote(UUID noteId, String content) {
        DashboardNote note = dashboardNoteRepository.findById(noteId)
                .orElseThrow(() -> new RuntimeException("Note not found"));
        
        note.setContent(content);
        return dashboardNoteRepository.save(note);
    }

    @Override
    @Transactional
    public void deleteNote(UUID noteId) {
        dashboardNoteRepository.deleteById(noteId);
    }
}
