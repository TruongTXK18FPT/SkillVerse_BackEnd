package com.exe.skillverse_backend.mentor_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.mentor_service.dto.response.MentorProfileResponse;
import com.exe.skillverse_backend.mentor_service.entity.FavoriteMentor;
import com.exe.skillverse_backend.mentor_service.entity.MentorProfile;
import com.exe.skillverse_backend.mentor_service.repository.FavoriteMentorRepository;
import com.exe.skillverse_backend.mentor_service.repository.MentorProfileRepository;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FavoriteMentorService {

    private final FavoriteMentorRepository favoriteMentorRepository;
    private final UserRepository userRepository;
    private final MentorProfileRepository mentorProfileRepository;

    @Transactional
    public boolean toggleFavorite(Long studentId, Long mentorId) {
        if (favoriteMentorRepository.existsByStudentIdAndMentorId(studentId, mentorId)) {
            favoriteMentorRepository.deleteByStudentIdAndMentorId(studentId, mentorId);
            return false; // Removed
        } else {
            User student = userRepository.findById(studentId)
                    .orElseThrow(() -> new NotFoundException("Student not found"));
            User mentor = userRepository.findById(mentorId)
                    .orElseThrow(() -> new NotFoundException("Mentor not found"));

            FavoriteMentor favorite = FavoriteMentor.builder()
                    .student(student)
                    .mentor(mentor)
                    .build();
            favoriteMentorRepository.save(favorite);
            return true; // Added
        }
    }

    @Transactional(readOnly = true)
    public List<MentorProfileResponse> getFavoriteMentors(Long studentId) {
        List<FavoriteMentor> favorites = favoriteMentorRepository.findByStudentIdOrderByCreatedAtDesc(studentId);
        
        return favorites.stream()
                .map(fav -> {
                    Long mentorId = fav.getMentor().getId();
                    return mentorProfileRepository.findByUserId(mentorId)
                            .map(this::mapToDTO)
                            .orElse(null);
                })
                .filter(dto -> dto != null)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public boolean isFavorite(Long studentId, Long mentorId) {
        return favoriteMentorRepository.existsByStudentIdAndMentorId(studentId, mentorId);
    }

    // Helper to map MentorProfile to DTO (simplified version of what might be in MentorService)
    private MentorProfileResponse mapToDTO(MentorProfile profile) {
        String firstName = null;
        String lastName = null;
        String fullName = profile.getFullName();
        if (fullName != null && fullName.contains(" ")) {
            String[] parts = fullName.split(" ", 2);
            firstName = parts[0];
            lastName = parts.length > 1 ? parts[1] : null;
        } else {
            firstName = fullName;
        }

        return MentorProfileResponse.builder()
                .id(profile.getUserId())
                .firstName(firstName)
                .lastName(lastName)
                .email(profile.getEmail())
                .bio(profile.getPersonalProfile())
                .specialization(profile.getMainExpertiseAreas())
                .experience(profile.getYearsOfExperience())
                .avatar(profile.getAvatarUrl())
                .skills(new String[]{})
                .achievements(new String[]{})
                .ratingAverage(profile.getRatingAverage())
                .ratingCount(profile.getRatingCount())
                .hourlyRate(profile.getHourlyRate())
                .preChatEnabled(profile.getPreChatEnabled())
                .slug(null)
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .skillPoints(profile.getSkillPoints())
                .currentLevel(profile.getCurrentLevel())
                .badges(new String[]{})
                .build();
    }
}
