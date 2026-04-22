package com.exe.skillverse_backend.portfolio_service.service;

import com.exe.skillverse_backend.portfolio_service.dto.CompletedMissionDTO;
import com.exe.skillverse_backend.portfolio_service.dto.CVGenerationRequest;
import com.exe.skillverse_backend.portfolio_service.dto.ExternalCertificateDTO;
import com.exe.skillverse_backend.portfolio_service.dto.MentorReviewDTO;
import com.exe.skillverse_backend.portfolio_service.dto.PortfolioEducationDTO;
import com.exe.skillverse_backend.portfolio_service.dto.PortfolioProjectDTO;
import com.exe.skillverse_backend.portfolio_service.dto.PortfolioWorkExperienceDTO;
import com.exe.skillverse_backend.portfolio_service.dto.UserProfileDTO;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import com.exe.skillverse_backend.portfolio_service.util.MarkdownConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service to map Portfolio data to CV structure WITHOUT using AI.
 * This provides a direct export option where users can manually edit before
 * optionally using AI to enhance specific sections.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CVMapperService {

    private final MarkdownConverter markdownConverter;

    /**
     * Maps portfolio data to CV JSON structure without AI processing.
     * All data is mapped directly with minimal transformation.
     */
    public CVStructuredData mapPortfolioToCV(
            UserProfileDTO profile,
            List<PortfolioProjectDTO> projects,
            List<ExternalCertificateDTO> certificates,
            List<MentorReviewDTO> reviews,
            List<CompletedMissionDTO> completedMissions,
            CVGenerationRequest request) {

        log.info("Mapping portfolio data to CV structure for user: {}", profile.getUserId());

        CVStructuredData cvData = new CVStructuredData();

        // Map personal info
        cvData.setPersonalInfo(mapPersonalInfo(profile));

        // Leave summary empty - user can fill or use AI enhance
        cvData.setSummary("");

        // Map experience from work experiences
        cvData.setExperience(mapExperience(profile.getWorkExperiences()));

        // Map education
        cvData.setEducation(mapEducation(profile.getEducationHistory()));

        // Map skills
        cvData.setSkills(mapSkills(profile.getTopSkills()));

        // Map projects (if requested)
        if (Boolean.TRUE.equals(request.getIncludeProjects())) {
            cvData.setProjects(mapProjects(projects));
        } else {
            cvData.setProjects(new ArrayList<>());
        }

        // Map certificates (if requested)
        if (Boolean.TRUE.equals(request.getIncludeCertificates())) {
            cvData.setCertificates(mapCertificates(certificates));
        } else {
            cvData.setCertificates(new ArrayList<>());
        }

        // Map languages
        cvData.setLanguages(mapLanguages(profile.getLanguagesSpoken()));

        // Map endorsements from reviews (if requested)
        if (Boolean.TRUE.equals(request.getIncludeReviews())) {
            cvData.setEndorsements(mapEndorsements(reviews));
        } else {
            cvData.setEndorsements(new ArrayList<>());
        }

        log.info("Successfully mapped portfolio data to CV structure for user: {}", profile.getUserId());
        return cvData;
    }

    private CVPersonalInfo mapPersonalInfo(UserProfileDTO profile) {
        CVPersonalInfo info = new CVPersonalInfo();
        info.setFullName(profile.getFullName() != null ? profile.getFullName() : "");
        info.setProfessionalTitle(profile.getProfessionalTitle() != null ? profile.getProfessionalTitle() : "");
        info.setEmail(profile.getEmail() != null ? profile.getEmail() : "");
        info.setPhone(profile.getPhone() != null ? profile.getPhone() : "");
        info.setLocation(profile.getLocation() != null ? profile.getLocation() : "");
        info.setLinkedinUrl(profile.getLinkedinUrl());
        info.setGithubUrl(profile.getGithubUrl());
        info.setPortfolioUrl(profile.getPortfolioWebsiteUrl());
        info.setBehanceUrl(profile.getBehanceUrl());
        info.setDribbbleUrl(profile.getDribbbleUrl());
        info.setAvatarUrl(profile.getPortfolioAvatarUrl() != null
                ? profile.getPortfolioAvatarUrl()
                : profile.getBasicAvatarUrl());
        return info;
    }

    private List<CVExperience> mapExperience(List<PortfolioWorkExperienceDTO> experiences) {
        if (experiences == null || experiences.isEmpty()) {
            return new ArrayList<>();
        }

        List<CVExperience> result = new ArrayList<>();
        int index = 1;
        for (PortfolioWorkExperienceDTO exp : experiences) {
            CVExperience cvExp = new CVExperience();
            cvExp.setId("exp_" + index++);
            cvExp.setTitle(exp.getPosition() != null ? exp.getPosition() : "");
            cvExp.setCompany(exp.getCompanyName() != null ? exp.getCompanyName() : "");
            cvExp.setLocation(exp.getLocation());
            cvExp.setStartDate(exp.getStartDate() != null ? exp.getStartDate() : "");
            cvExp.setEndDate(exp.getEndDate());
            cvExp.setCurrent(Boolean.TRUE.equals(exp.getCurrentJob()));
            // Convert markdown description to HTML for proper CV rendering
            String rawDescription = exp.getDescription() != null ? exp.getDescription() : "";
            cvExp.setDescription(markdownConverter.toHtmlWithParagraphs(rawDescription));
            cvExp.setAchievements(new ArrayList<>()); // Empty, user can add
            cvExp.setTechnologies(new ArrayList<>()); // Empty, user can add
            result.add(cvExp);
        }
        return result;
    }

    private List<CVEducation> mapEducation(List<PortfolioEducationDTO> educationList) {
        if (educationList == null || educationList.isEmpty()) {
            return new ArrayList<>();
        }

        List<CVEducation> result = new ArrayList<>();
        int index = 1;
        for (PortfolioEducationDTO edu : educationList) {
            CVEducation cvEdu = new CVEducation();
            cvEdu.setId("edu_" + index++);
            cvEdu.setDegree(edu.getDegree() != null ? edu.getDegree() : "");
            cvEdu.setInstitution(edu.getInstitution() != null ? edu.getInstitution() : "");
            cvEdu.setLocation(edu.getLocation());
            cvEdu.setStartDate(edu.getStartDate() != null ? edu.getStartDate() : "");
            cvEdu.setEndDate(edu.getEndDate());
            cvEdu.setGpa(edu.getGpa()); // Map GPA if available
            cvEdu.setRelevantCourses(edu.getRelevantCourses() != null ? edu.getRelevantCourses() : new ArrayList<>());
            // Convert markdown description to HTML for proper CV rendering
            String rawDescription = edu.getDescription() != null ? edu.getDescription() : "";
            cvEdu.setDescription(markdownConverter.toHtmlWithParagraphs(rawDescription));
            result.add(cvEdu);
        }
        return result;
    }

    private List<CVSkillCategory> mapSkills(String topSkills) {
        List<CVSkillCategory> result = new ArrayList<>();

        if (topSkills == null || topSkills.isBlank()) {
            return result;
        }

        // Parse comma-separated skills
        String[] skills = topSkills.split(",");
        List<CVSkill> skillList = new ArrayList<>();

        for (String skill : skills) {
            String trimmed = skill.trim();
            if (!trimmed.isEmpty()) {
                CVSkill cvSkill = new CVSkill();
                cvSkill.setName(trimmed);
                cvSkill.setLevel(3); // Default intermediate level
                skillList.add(cvSkill);
            }
        }

        if (!skillList.isEmpty()) {
            CVSkillCategory category = new CVSkillCategory();
            category.setCategory("Technical Skills");
            category.setSkills(skillList);
            result.add(category);
        }

        return result;
    }

    private List<CVProject> mapProjects(List<PortfolioProjectDTO> projects) {
        if (projects == null || projects.isEmpty()) {
            return new ArrayList<>();
        }

        List<CVProject> result = new ArrayList<>();
        int index = 1;
        for (PortfolioProjectDTO proj : projects) {
            CVProject cvProj = new CVProject();
            cvProj.setId("proj_" + index++);
            cvProj.setTitle(proj.getTitle() != null ? proj.getTitle() : "");
            // Convert markdown description to HTML
        String rawProjDesc = proj.getDescription() != null ? proj.getDescription() : "";
        cvProj.setDescription(markdownConverter.toHtmlWithParagraphs(rawProjDesc));
            cvProj.setRole(null); // User can specify
            cvProj.setTechnologies(proj.getTools() != null ? proj.getTools() : new ArrayList<>());
            cvProj.setOutcomes(proj.getOutcomes() != null ? proj.getOutcomes() : new ArrayList<>());
            cvProj.setUrl(proj.getProjectUrl());
            cvProj.setDuration(proj.getDuration());
            cvProj.setClientName(proj.getClientName());
            cvProj.setRating(proj.getRating());
            result.add(cvProj);
        }
        return result;
    }

    private List<CVCertificate> mapCertificates(List<ExternalCertificateDTO> certificates) {
        if (certificates == null || certificates.isEmpty()) {
            return new ArrayList<>();
        }

        List<CVCertificate> result = new ArrayList<>();
        int index = 1;
        for (ExternalCertificateDTO cert : certificates) {
            CVCertificate cvCert = new CVCertificate();
            cvCert.setId("cert_" + index++);
            cvCert.setTitle(cert.getTitle() != null ? cert.getTitle() : "");
            cvCert.setIssuingOrganization(cert.getIssuingOrganization() != null ? cert.getIssuingOrganization() : "");
            cvCert.setIssueDate(cert.getIssueDate() != null ? cert.getIssueDate().toString() : "");
            cvCert.setCredentialId(cert.getCredentialId());
            cvCert.setSkills(cert.getSkills() != null ? cert.getSkills() : new ArrayList<>());
            result.add(cvCert);
        }
        return result;
    }

    private List<CVLanguage> mapLanguages(String languagesSpoken) {
        List<CVLanguage> result = new ArrayList<>();

        if (languagesSpoken == null || languagesSpoken.isBlank()) {
            // Default to Vietnamese
            CVLanguage vi = new CVLanguage();
            vi.setName("Vietnamese");
            vi.setProficiency("Native");
            result.add(vi);
            return result;
        }

        // Parse comma-separated languages
        String[] languages = languagesSpoken.split(",");
        for (String lang : languages) {
            String trimmed = lang.trim();
            if (!trimmed.isEmpty()) {
                CVLanguage cvLang = new CVLanguage();
                cvLang.setName(trimmed);
                // Default proficiency based on name
                if (trimmed.equalsIgnoreCase("Vietnamese") || trimmed.equalsIgnoreCase("Tiếng Việt")) {
                    cvLang.setProficiency("Native");
                } else if (trimmed.equalsIgnoreCase("English")) {
                    cvLang.setProficiency("Fluent");
                } else {
                    cvLang.setProficiency("Intermediate");
                }
                result.add(cvLang);
            }
        }

        return result;
    }

    private List<CVEndorsement> mapEndorsements(List<MentorReviewDTO> reviews) {
        if (reviews == null || reviews.isEmpty()) {
            return new ArrayList<>();
        }

        return reviews.stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsPublic()))
                .map(r -> {
                    CVEndorsement end = new CVEndorsement();
                    end.setQuote(r.getFeedback() != null ? r.getFeedback() : "");
                    end.setAuthorName(r.getMentorName() != null ? r.getMentorName() : "");
                    end.setAuthorTitle(r.getMentorTitle());
                    end.setSkillEndorsed(r.getSkillEndorsed());
                    end.setRating(r.getRating());
                    return end;
                })
                .collect(Collectors.toList());
    }

    // ==================== CV Data Classes ====================

    public static class CVStructuredData {
        private CVPersonalInfo personalInfo;
        private String summary;
        private List<CVExperience> experience = new ArrayList<>();
        private List<CVEducation> education = new ArrayList<>();
        private List<CVSkillCategory> skills = new ArrayList<>();
        private List<CVProject> projects = new ArrayList<>();
        private List<CVCertificate> certificates = new ArrayList<>();
        private List<CVLanguage> languages = new ArrayList<>();
        private List<CVEndorsement> endorsements = new ArrayList<>();

        // Getters and Setters
        public CVPersonalInfo getPersonalInfo() { return personalInfo; }
        public void setPersonalInfo(CVPersonalInfo personalInfo) { this.personalInfo = personalInfo; }
        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
        public List<CVExperience> getExperience() { return experience; }
        public void setExperience(List<CVExperience> experience) { this.experience = experience; }
        public List<CVEducation> getEducation() { return education; }
        public void setEducation(List<CVEducation> education) { this.education = education; }
        public List<CVSkillCategory> getSkills() { return skills; }
        public void setSkills(List<CVSkillCategory> skills) { this.skills = skills; }
        public List<CVProject> getProjects() { return projects; }
        public void setProjects(List<CVProject> projects) { this.projects = projects; }
        public List<CVCertificate> getCertificates() { return certificates; }
        public void setCertificates(List<CVCertificate> certificates) { this.certificates = certificates; }
        public List<CVLanguage> getLanguages() { return languages; }
        public void setLanguages(List<CVLanguage> languages) { this.languages = languages; }
        public List<CVEndorsement> getEndorsements() { return endorsements; }
        public void setEndorsements(List<CVEndorsement> endorsements) { this.endorsements = endorsements; }
    }

    public static class CVPersonalInfo {
        private String fullName;
        private String professionalTitle;
        private String email;
        private String phone;
        private String location;
        private String linkedinUrl;
        private String githubUrl;
        private String portfolioUrl;
        private String behanceUrl;
        private String dribbbleUrl;
        private String avatarUrl;

        // Getters and Setters
        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }
        public String getProfessionalTitle() { return professionalTitle; }
        public void setProfessionalTitle(String professionalTitle) { this.professionalTitle = professionalTitle; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }
        public String getLinkedinUrl() { return linkedinUrl; }
        public void setLinkedinUrl(String linkedinUrl) { this.linkedinUrl = linkedinUrl; }
        public String getGithubUrl() { return githubUrl; }
        public void setGithubUrl(String githubUrl) { this.githubUrl = githubUrl; }
        public String getPortfolioUrl() { return portfolioUrl; }
        public void setPortfolioUrl(String portfolioUrl) { this.portfolioUrl = portfolioUrl; }
        public String getBehanceUrl() { return behanceUrl; }
        public void setBehanceUrl(String behanceUrl) { this.behanceUrl = behanceUrl; }
        public String getDribbbleUrl() { return dribbbleUrl; }
        public void setDribbbleUrl(String dribbbleUrl) { this.dribbbleUrl = dribbbleUrl; }
        public String getAvatarUrl() { return avatarUrl; }
        public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    }

    public static class CVExperience {
        private String id;
        private String title;
        private String company;
        private String location;
        private String startDate;
        private String endDate;
        private boolean isCurrent;
        private String description;
        private List<String> achievements = new ArrayList<>();
        private List<String> technologies = new ArrayList<>();

        // Getters and Setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getCompany() { return company; }
        public void setCompany(String company) { this.company = company; }
        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }
        public String getStartDate() { return startDate; }
        public void setStartDate(String startDate) { this.startDate = startDate; }
        public String getEndDate() { return endDate; }
        public void setEndDate(String endDate) { this.endDate = endDate; }
        public boolean isCurrent() { return isCurrent; }
        public void setCurrent(boolean current) { isCurrent = current; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public List<String> getAchievements() { return achievements; }
        public void setAchievements(List<String> achievements) { this.achievements = achievements; }
        public List<String> getTechnologies() { return technologies; }
        public void setTechnologies(List<String> technologies) { this.technologies = technologies; }
    }

    public static class CVEducation {
        private String id;
        private String degree;
        private String institution;
        private String location;
        private String startDate;
        private String endDate;
        private String gpa;
        private String description; // HTML formatted description
        private List<String> relevantCourses = new ArrayList<>();

        // Getters and Setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getDegree() { return degree; }
        public void setDegree(String degree) { this.degree = degree; }
        public String getInstitution() { return institution; }
        public void setInstitution(String institution) { this.institution = institution; }
        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }
        public String getStartDate() { return startDate; }
        public void setStartDate(String startDate) { this.startDate = startDate; }
        public String getEndDate() { return endDate; }
        public void setEndDate(String endDate) { this.endDate = endDate; }
        public String getGpa() { return gpa; }
        public void setGpa(String gpa) { this.gpa = gpa; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public List<String> getRelevantCourses() { return relevantCourses; }
        public void setRelevantCourses(List<String> relevantCourses) { this.relevantCourses = relevantCourses; }
    }

    public static class CVSkillCategory {
        private String category;
        private List<CVSkill> skills = new ArrayList<>();

        // Getters and Setters
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public List<CVSkill> getSkills() { return skills; }
        public void setSkills(List<CVSkill> skills) { this.skills = skills; }
    }

    public static class CVSkill {
        private String name;
        private int level; // 1-5

        // Getters and Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getLevel() { return level; }
        public void setLevel(int level) { this.level = level; }
    }

    public static class CVProject {
        private String id;
        private String title;
        private String description;
        private String role;
        private List<String> technologies = new ArrayList<>();
        private List<String> outcomes = new ArrayList<>();
        private String url;
        private String duration;
        private String clientName;
        private Integer rating;

        // Getters and Setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public List<String> getTechnologies() { return technologies; }
        public void setTechnologies(List<String> technologies) { this.technologies = technologies; }
        public List<String> getOutcomes() { return outcomes; }
        public void setOutcomes(List<String> outcomes) { this.outcomes = outcomes; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getDuration() { return duration; }
        public void setDuration(String duration) { this.duration = duration; }
        public String getClientName() { return clientName; }
        public void setClientName(String clientName) { this.clientName = clientName; }
        public Integer getRating() { return rating; }
        public void setRating(Integer rating) { this.rating = rating; }
    }

    public static class CVCertificate {
        private String id;
        private String title;
        private String issuingOrganization;
        private String issueDate;
        private String credentialId;
        private List<String> skills = new ArrayList<>();

        // Getters and Setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getIssuingOrganization() { return issuingOrganization; }
        public void setIssuingOrganization(String issuingOrganization) { this.issuingOrganization = issuingOrganization; }
        public String getIssueDate() { return issueDate; }
        public void setIssueDate(String issueDate) { this.issueDate = issueDate; }
        public String getCredentialId() { return credentialId; }
        public void setCredentialId(String credentialId) { this.credentialId = credentialId; }
        public List<String> getSkills() { return skills; }
        public void setSkills(List<String> skills) { this.skills = skills; }
    }

    public static class CVLanguage {
        private String name;
        private String proficiency; // Native, Fluent, Advanced, Intermediate, Basic

        // Getters and Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getProficiency() { return proficiency; }
        public void setProficiency(String proficiency) { this.proficiency = proficiency; }
    }

    public static class CVEndorsement {
        private String quote;
        private String authorName;
        private String authorTitle;
        private String skillEndorsed;
        private Integer rating;

        // Getters and Setters
        public String getQuote() { return quote; }
        public void setQuote(String quote) { this.quote = quote; }
        public String getAuthorName() { return authorName; }
        public void setAuthorName(String authorName) { this.authorName = authorName; }
        public String getAuthorTitle() { return authorTitle; }
        public void setAuthorTitle(String authorTitle) { this.authorTitle = authorTitle; }
        public String getSkillEndorsed() { return skillEndorsed; }
        public void setSkillEndorsed(String skillEndorsed) { this.skillEndorsed = skillEndorsed; }
        public Integer getRating() { return rating; }
        public void setRating(Integer rating) { this.rating = rating; }
    }
}
