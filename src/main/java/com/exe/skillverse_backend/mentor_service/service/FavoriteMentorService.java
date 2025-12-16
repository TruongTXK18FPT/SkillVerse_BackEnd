package com.exe.skillverse_backend.mentor_service.service;

import com.exe.skillverse_backend.mentor_service.dto.response.MentorProfileResponse;
import java.util.List;

public interface FavoriteMentorService {
    boolean toggleFavorite(Long studentId, Long mentorId);

    List<MentorProfileResponse> getFavoriteMentors(Long studentId);

    boolean isFavorite(Long studentId, Long mentorId);
}