package com.exe.skillverse_backend.business_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.business_service.dto.request.RecruiterProfileUpdateRequest;
import com.exe.skillverse_backend.business_service.dto.response.RecruiterProfileResponse;
import com.exe.skillverse_backend.business_service.entity.RecruiterProfile;
import com.exe.skillverse_backend.business_service.repository.RecruiterProfileRepository;
import com.exe.skillverse_backend.business_service.service.impl.RecruiterProfileServiceImpl;
import com.exe.skillverse_backend.mentor_service.entity.ApplicationStatus;
import com.exe.skillverse_backend.shared.service.CloudinaryService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecruiterProfileServiceImplTest {

    @Mock
    private RecruiterProfileRepository recruiterProfileRepository;

    @Mock
    private UserRepository userRepository;

    private RecruiterProfileServiceImpl service;

    @Mock
    private CloudinaryService cloudinaryService;

    @BeforeEach
    void setUp() {
        service = new RecruiterProfileServiceImpl(recruiterProfileRepository, userRepository, cloudinaryService);
        lenient().when(recruiterProfileRepository.save(any(RecruiterProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("getRecruiterProfile should combine recruiter and user data")
    void getRecruiterProfile_ShouldCombineRecruiterAndUserData() {
        Long userId = 21L;
        RecruiterProfile profile = recruiterProfile(userId);
        User user = User.builder().id(userId).email("recruiter@skillverse.vn").build();

        when(recruiterProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        RecruiterProfileResponse response = service.getRecruiterProfile(userId);

        assertEquals(userId, response.getUserId());
        assertEquals("recruiter@skillverse.vn", response.getEmail());
        assertEquals("SkillVerse Labs", response.getCompanyName());
        assertEquals(ApplicationStatus.APPROVED, response.getApplicationStatus());
    }

    @Test
    @DisplayName("updateRecruiterProfile should update main fields but keep existing document URL when blank")
    void updateRecruiterProfile_ShouldKeepExistingDocumentUrlWhenBlank() {
        Long userId = 21L;
        RecruiterProfile profile = recruiterProfile(userId);
        User user = User.builder().id(userId).email("recruiter@skillverse.vn").build();
        RecruiterProfileUpdateRequest request = RecruiterProfileUpdateRequest.builder()
                .companyName("SkillVerse Hiring")
                .companyWebsite("https://hiring.skillverse.vn")
                .companyAddress("Da Nang")
                .taxCodeOrBusinessRegistrationNumber("TAX-NEW")
                .companyDocumentsUrl("")
                .build();

        when(recruiterProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        RecruiterProfileResponse response = service.updateRecruiterProfile(userId, request);

        assertEquals("SkillVerse Hiring", response.getCompanyName());
        assertEquals("https://hiring.skillverse.vn", response.getCompanyWebsite());
        assertEquals("Da Nang", response.getCompanyAddress());
        assertEquals("TAX-NEW", response.getTaxCodeOrBusinessRegistrationNumber());
        assertEquals("https://cdn.skillverse.vn/docs/company.pdf", response.getCompanyDocumentsUrl());
    }

    private RecruiterProfile recruiterProfile(Long userId) {
        return RecruiterProfile.builder()
                .userId(userId)
                .companyName("SkillVerse Labs")
                .companyWebsite("https://skillverse.vn")
                .companyAddress("Ho Chi Minh City")
                .taxCodeOrBusinessRegistrationNumber("TAX-123")
                .companyDocumentsUrl("https://cdn.skillverse.vn/docs/company.pdf")
                .contactPersonPosition("CEO")
                .companySize("11-50")
                .industry("Education")
                .applicationStatus(ApplicationStatus.APPROVED)
                .build();
    }
}
