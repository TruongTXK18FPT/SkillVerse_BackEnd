package com.exe.skillverse_backend.study_service.service;

import com.exe.skillverse_backend.study_service.entity.DashboardNote;
import java.util.List;
import java.util.UUID;

public interface DashboardService {
    List<DashboardNote> getUserNotes(Long userId);
    DashboardNote createNote(Long userId, String content);
    DashboardNote updateNote(UUID noteId, String content);
    void deleteNote(UUID noteId);
}
