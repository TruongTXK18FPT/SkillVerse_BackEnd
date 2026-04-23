package com.exe.skillverse_backend.ai_knowledge_service.service.impl;

import com.exe.skillverse_backend.ai_knowledge_service.entity.AiKnowledgeDocument;
import com.exe.skillverse_backend.ai_knowledge_service.service.AiKnowledgeAuthorizationService;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.course_service.entity.Assignment;
import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.course_service.entity.Module;
import com.exe.skillverse_backend.course_service.repository.AssignmentRepository;
import com.exe.skillverse_backend.course_service.repository.CourseRepository;
import com.exe.skillverse_backend.course_service.repository.ModuleRepository;
import com.exe.skillverse_backend.mentor_verification_service.service.MentorVerificationService;
import com.exe.skillverse_backend.shared.exception.ApiException;
import com.exe.skillverse_backend.shared.exception.ErrorCode;
import com.exe.skillverse_backend.shared.util.SkillNameUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiKnowledgeAuthorizationServiceImpl implements AiKnowledgeAuthorizationService {

    private final MentorVerificationService mentorVerificationService;
    private final CourseRepository courseRepository;
    private final ModuleRepository moduleRepository;
    private final AssignmentRepository assignmentRepository;

    @Override
    public void assertMentorVerifiedSkill(User mentor, String skillName) {
        if (mentor == null || skillName == null || skillName.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Mentor and skill name are required");
        }

        String normalizedSkill = SkillNameUtils.normalizeRequired(skillName);
        List<String> verifiedSkills = mentorVerificationService.getMyVerifiedSkills(mentor);

        boolean isVerified = verifiedSkills.stream()
                .anyMatch(s -> SkillNameUtils.normalizeRequired(s).equals(normalizedSkill));

        if (!isVerified) {
            throw new ApiException(ErrorCode.FORBIDDEN,
                    "You have not verified the skill: " + skillName);
        }
    }

    @Override
    public void assertMentorOwnsCourse(User mentor, Long courseId) {
        if (mentor == null || courseId == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Mentor and course ID are required");
        }

        Course course = courseRepository.findByIdWithAuthor(courseId);
        if (course == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Course not found: " + courseId);
        }

        if (!course.getAuthor().getId().equals(mentor.getId())) {
            throw new ApiException(ErrorCode.FORBIDDEN,
                    "You do not own this course");
        }
    }

    @Override
    public void assertMentorOwnsModule(User mentor, Long moduleId) {
        if (mentor == null || moduleId == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Mentor and module ID are required");
        }

        Module module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Module not found: " + moduleId));

        Long courseId = module.getCourse().getId();
        assertMentorOwnsCourse(mentor, courseId);
    }

    @Override
    public void assertMentorOwnsAssignment(User mentor, Long assignmentId) {
        if (mentor == null || assignmentId == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Mentor and assignment ID are required");
        }

        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Assignment not found: " + assignmentId));

        Long courseId = assignment.getModule().getCourse().getId();
        assertMentorOwnsCourse(mentor, courseId);
    }

    @Override
    public void assertMentorCanAccessDocument(User mentor, AiKnowledgeDocument document) {
        if (mentor == null || document == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Mentor and document are required");
        }

        if (!mentor.getId().equals(document.getMentorId())) {
            throw new ApiException(ErrorCode.FORBIDDEN,
                    "You do not have access to this document");
        }
    }
}
