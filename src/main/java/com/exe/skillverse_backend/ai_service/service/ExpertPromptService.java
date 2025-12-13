package com.exe.skillverse_backend.ai_service.service;

import com.exe.skillverse_backend.ai_service.repository.ExpertPromptConfigRepository;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

/**
 * Service to manage specialized expert prompts for different fields and roles.
 * Refactored to delegate domain-specific logic to sub-services.
 */
@Service
@RequiredArgsConstructor
public class ExpertPromptService extends BaseExpertPromptService {

    private final ITPromptService itPromptService;
    private final BusinessPromptService businessPromptService;
    private final DesignPromptService designPromptService;
    private final EngineeringPromptService engineeringPromptService;
    private final HealthcarePromptService healthcarePromptService;
    private final EducationPromptService educationPromptService;
    private final LegalPromptService legalPromptService;
    private final LogisticsPromptService logisticsPromptService;
    private final ArtsPromptService artsPromptService;
    private final ServicePromptService servicePromptService;
    private final SocialCommunityPromptService socialCommunityPromptService;
    private final AgricultureEnvironmentPromptService agricultureEnvironmentPromptService;
    private final ExpertPromptConfigRepository expertPromptConfigRepository;

    public String getSystemPrompt(String domain, String industry, String jobRole) {
        // Normalization
        String normalizedRole = (jobRole == null) ? "" : jobRole.trim().toLowerCase();
        String normalizedIndustry = (industry == null) ? "" : industry.trim().toLowerCase();
        String normalizedDomain = (domain == null) ? "" : domain.trim().toLowerCase();

        // If no specific role, return null (AiChatbotService will use default)
        if (normalizedRole.isEmpty()) {
            return null;
        }

        // 1. Try finding EXACT match in DB first (Best for when frontend sends selected value)
        var exactMatch = expertPromptConfigRepository.findByDomainAndIndustryAndJobRoleAndIsActiveTrue(
            domain, industry, jobRole
        );
        
        if (exactMatch.isPresent()) {
            return exactMatch.get().getSystemPrompt();
        }

        // 2. Try fuzzy matching if exact match fails (For loose search)
        String domainPattern = (domain == null || domain.isBlank()) ? null : "%" + domain + "%";
        String industryPattern = (industry == null || industry.isBlank()) ? null : "%" + industry + "%";
        String rolePattern = "%" + normalizedRole + "%";
        var matchingPrompts = expertPromptConfigRepository.findMatchingPrompts(domainPattern, industryPattern, rolePattern);
        
        if (!matchingPrompts.isEmpty()) {
            // Return the first match
            return matchingPrompts.get(0).getSystemPrompt();
        }

        // 3. Fallback to hardcoded logic via sub-services
        
        // Domain-based delegation
        if (normalizedDomain.contains("it") || normalizedDomain.contains("công nghệ thông tin") || 
            normalizedDomain.contains("technology") || normalizedDomain.contains("software")) {
            return itPromptService.getPrompt(normalizedDomain, normalizedIndustry, normalizedRole);
        }
        if (normalizedDomain.contains("business") || normalizedDomain.contains("kinh doanh") || 
            normalizedDomain.contains("marketing") || normalizedDomain.contains("sales")) {
            return businessPromptService.getPrompt(normalizedDomain, normalizedIndustry, normalizedRole);
        }
        if (normalizedDomain.contains("design") || normalizedDomain.contains("thiết kế") || 
            normalizedDomain.contains("creative") || normalizedDomain.contains("art")) {
            return designPromptService.getPrompt(normalizedDomain, normalizedIndustry, normalizedRole);
        }
        if (normalizedDomain.contains("engineering") || normalizedDomain.contains("kỹ thuật") || 
            normalizedDomain.contains("công nghiệp") || normalizedDomain.contains("manufacturing")) {
            return engineeringPromptService.getPrompt(normalizedDomain, normalizedIndustry, normalizedRole);
        }
        if (normalizedDomain.contains("healthcare") || normalizedDomain.contains("y tế") || 
            normalizedDomain.contains("sức khỏe") || normalizedDomain.contains("medical")) {
            return healthcarePromptService.getPrompt(normalizedIndustry, normalizedRole);
        }
        if (normalizedDomain.contains("education") || normalizedDomain.contains("giáo dục") || 
            normalizedDomain.contains("đào tạo") || normalizedDomain.contains("teaching") ||
            normalizedDomain.contains("edtech") || normalizedDomain.contains("learning")) {
            return educationPromptService.getPrompt(normalizedIndustry, normalizedRole);
        }

        boolean isLogisticsDomain = normalizedDomain.contains("logistics") || normalizedDomain.contains("trade") ||
                                   normalizedDomain.contains("logistics_trade") ||
                                   normalizedDomain.contains("chuỗi cung ứng") || normalizedDomain.contains("xuất nhập khẩu") ||
                                   normalizedDomain.contains("vận tải") || normalizedDomain.contains("kho bãi");

        if (isLogisticsDomain) {
            return logisticsPromptService.getPrompt(normalizedDomain, normalizedIndustry, normalizedRole);
        }

        // Legal & Public Administration
        if (normalizedDomain.contains("legal") || normalizedDomain.contains("luật") || 
           normalizedDomain.contains("pháp lý") || normalizedDomain.contains("law") ||
           normalizedDomain.contains("hành chính") || normalizedDomain.contains("công quyền") ||
           normalizedDomain.contains("public administration") || normalizedDomain.contains("paralegal")) {
           return legalPromptService.getPrompt(normalizedDomain, normalizedIndustry, normalizedRole);
        }

        // Arts & Entertainment
        if (normalizedDomain.contains("arts") || normalizedDomain.contains("art") || 
            normalizedDomain.contains("entertainment") || normalizedDomain.contains("arts_entertainment") ||
            normalizedDomain.contains("nghệ thuật") || normalizedDomain.contains("giải trí") ||
            normalizedDomain.contains("biểu diễn")) {
            return artsPromptService.getPrompt(domain, industry, jobRole);
        }

        // Service & Hospitality
        if (normalizedDomain.contains("service") || normalizedDomain.contains("hospitality") || 
            normalizedDomain.contains("service_hospitality") || normalizedDomain.contains("dịch vụ") ||
            normalizedDomain.contains("nhà hàng") || normalizedDomain.contains("khách sạn") ||
            normalizedDomain.contains("f&b") || normalizedDomain.contains("food") || 
            normalizedDomain.contains("beverage") || normalizedDomain.contains("restaurant")) {
            return servicePromptService.getPrompt(domain, industry, jobRole);
        }

        // Social Community
        if (normalizedDomain.contains("social") || normalizedDomain.contains("community") ||
            normalizedDomain.contains("social_community") || normalizedDomain.contains("công tác xã hội") ||
            normalizedDomain.contains("cộng đồng") || normalizedDomain.contains("phi lợi nhuận") ||
            normalizedDomain.contains("xã hội") || normalizedDomain.contains("thiện nguyện") ||
            normalizedDomain.contains("social work") || normalizedDomain.contains("ngo")) {
            return socialCommunityPromptService.getPrompt(industry, jobRole);
        }

        // Agriculture – Environment
        if (normalizedDomain.contains("agriculture") || normalizedDomain.contains("environment") ||
            normalizedDomain.contains("agriculture_environment") || normalizedDomain.contains("nông nghiệp") ||
            normalizedDomain.contains("môi trường") || normalizedDomain.contains("tài nguyên thiên nhiên") ||
            normalizedDomain.contains("agronomist") || normalizedDomain.contains("crop") ||
            normalizedDomain.contains("horticulture") || normalizedDomain.contains("soil") ||
            normalizedDomain.contains("seed") || normalizedDomain.contains("plant protection")) {
            return agricultureEnvironmentPromptService.getPrompt(industry, jobRole);
        }

        // Fallback if role is provided but not matched in our specific list
        // We return a generic expert prompt for that role
        return getGenericExpertPrompt(jobRole);
    }

    public String getGenericExpertPrompt(String role) {
        return getBaseExpertPersona() + "\n" +
               "## 🎓 LĨNH VỰC CHUYÊN MÔN: " + role.toUpperCase() + "\n" +
               "Hãy đóng vai trò là chuyên gia hàng đầu trong lĩnh vực " + role + ".\n" +
               "Sử dụng kiến thức sâu rộng nhất về " + role + " để tư vấn lộ trình, kỹ năng và công cụ phù hợp nhất năm 2025.";
    }
    
    // Wrapper methods for Initializer to use (delegating to new services)
    // Information Technology
    public String getBackendDeveloperPrompt() { return itPromptService.getBackendDeveloperPrompt(); }
    public String getFrontendDeveloperPrompt() { return itPromptService.getFrontendDeveloperPrompt(); }
    public String getFullstackDeveloperPrompt() { return itPromptService.getFullstackDeveloperPrompt(); }
    public String getMobileDeveloperPrompt() { return itPromptService.getMobileDeveloperPrompt(); }
    public String getDevOpsEngineerPrompt() { return itPromptService.getDevOpsEngineerPrompt(); }
    public String getSoftwareArchitectPrompt() { return itPromptService.getSoftwareArchitectPrompt(); }
    public String getAutomationQAPrompt() { return itPromptService.getAutomationQAPrompt(); }
    public String getManualTesterPrompt() { return itPromptService.getManualTesterPrompt(); }
    public String getDataAnalystPrompt() { return itPromptService.getDataAnalystPrompt(); }
    public String getBusinessIntelligencePrompt() { return itPromptService.getBusinessIntelligencePrompt(); }
    public String getDataEngineerPrompt() { return itPromptService.getDataEngineerPrompt(); }
    public String getMachineLearningEngineerPrompt() { return itPromptService.getMachineLearningEngineerPrompt(); }
    public String getAiEngineerPrompt() { return itPromptService.getAiEngineerPrompt(); }
    public String getDataScientistPrompt() { return itPromptService.getDataScientistPrompt(); }
    public String getPromptEngineerPrompt() { return itPromptService.getPromptEngineerPrompt(); }
    public String getCybersecurityAnalystPrompt() { return itPromptService.getCybersecurityAnalystPrompt(); }
    public String getSecurityEngineerPrompt() { return itPromptService.getSecurityEngineerPrompt(); }
    public String getPentesterPrompt() { return itPromptService.getPentesterPrompt(); }
    public String getSocAnalystPrompt() { return itPromptService.getSocAnalystPrompt(); }
    public String getNetworkSecurityEngineerPrompt() { return itPromptService.getNetworkSecurityEngineerPrompt(); }
    public String getCloudEngineerPrompt() { return itPromptService.getCloudEngineerPrompt(); }
    public String getCloudArchitectPrompt() { return itPromptService.getCloudArchitectPrompt(); }
    public String getSystemAdministratorPrompt() { return itPromptService.getSystemAdministratorPrompt(); }
    public String getNetworkEngineerPrompt() { return itPromptService.getNetworkEngineerPrompt(); }

    // Business & Marketing
    public String getDigitalMarketingPrompt() { return businessPromptService.getDigitalMarketingPrompt(); }
    public String getContentMarketingPrompt() { return businessPromptService.getContentMarketingPrompt(); }
    public String getSocialMediaExecutivePrompt() { return businessPromptService.getSocialMediaExecutivePrompt(); }
    public String getPerformanceMarketingPrompt() { return businessPromptService.getPerformanceMarketingPrompt(); }
    public String getSeoSpecialistPrompt() { return businessPromptService.getSeoSpecialistPrompt(); }
    public String getEmailMarketingPrompt() { return businessPromptService.getEmailMarketingPrompt(); }
    public String getBrandExecutivePrompt() { return businessPromptService.getBrandExecutivePrompt(); }
    public String getCreativePlannerPrompt() { return businessPromptService.getCreativePlannerPrompt(); }
    public String getCopywriterPrompt() { return businessPromptService.getCopywriterPrompt(); }
    public String getMarketingAnalystPrompt() { return businessPromptService.getMarketingAnalystPrompt(); }
    public String getBusinessAnalystPrompt() { return businessPromptService.getBusinessAnalystPrompt(); }
    public String getOperationsManagerPrompt() { return businessPromptService.getOperationsManagerPrompt(); }
    public String getProductManagerPrompt() { return businessPromptService.getProductManagerPrompt(); }
    public String getProductOwnerPrompt() { return businessPromptService.getProductOwnerPrompt(); }
    public String getProductAnalystPrompt() { return businessPromptService.getProductAnalystPrompt(); }
    public String getProjectManagerBusinessPrompt() { return businessPromptService.getProjectManagerBusinessPrompt(); }
    public String getHrRecruitmentPrompt() { return businessPromptService.getHrRecruitmentPrompt(); }
    public String getHrTalentDevelopmentPrompt() { return businessPromptService.getHrTalentDevelopmentPrompt(); }
    public String getOfficeAdminPrompt() { return businessPromptService.getOfficeAdminPrompt(); }
    public String getCustomerServicePrompt() { return businessPromptService.getCustomerServicePrompt(); }
    public String getSupplyChainPrompt() { return businessPromptService.getSupplyChainPrompt(); }
    public String getLogisticsExecutivePrompt() { return businessPromptService.getLogisticsExecutivePrompt(); }
    public String getSalesExecutivePrompt() { return businessPromptService.getSalesExecutivePrompt(); }
    public String getB2bSalesPrompt() { return businessPromptService.getB2bSalesPrompt(); }
    public String getBusinessDevelopmentPrompt() { return businessPromptService.getBusinessDevelopmentPrompt(); }
    public String getAccountExecutivePrompt() { return businessPromptService.getAccountExecutivePrompt(); }
    public String getKeyAccountManagerPrompt() { return businessPromptService.getKeyAccountManagerPrompt(); }
    public String getGrowthMarketerPrompt() { return businessPromptService.getGrowthMarketerPrompt(); }
    public String getCorporateFinanceAnalystPrompt() { return businessPromptService.getCorporateFinanceAnalystPrompt(); }
    public String getAccountantPrompt() { return businessPromptService.getAccountantPrompt(); }
    public String getInvestmentAnalystPrompt() { return businessPromptService.getInvestmentAnalystPrompt(); }
    public String getBankingOfficerPrompt() { return businessPromptService.getBankingOfficerPrompt(); }
    public String getFintechProductAnalystPrompt() { return businessPromptService.getFintechProductAnalystPrompt(); }
    public String getStartupFounderPrompt() { return businessPromptService.getStartupFounderPrompt(); }
    public String getBusinessConsultantPrompt() { return businessPromptService.getBusinessConsultantPrompt(); }
    public String getEntrepreneurInTrainingPrompt() { return businessPromptService.getEntrepreneurInTrainingPrompt(); }
    public String getFreelancerPrompt() { return businessPromptService.getFreelancerPrompt(); }

    // Design & Creative
    public String getGraphicDesignerPrompt() { return designPromptService.getGraphicDesignerPrompt(); }
    public String getBrandDesignerPrompt() { return designPromptService.getBrandDesignerPrompt(); }
    public String getLogoIdentityDesignerPrompt() { return designPromptService.getLogoIdentityDesignerPrompt(); }
    public String getLayoutDesignerPrompt() { return designPromptService.getLayoutDesignerPrompt(); }
    public String getPackagingDesignerPrompt() { return designPromptService.getPackagingDesignerPrompt(); }
    public String getPrintDesignerPrompt() { return designPromptService.getPrintDesignerPrompt(); }
    public String getIllustratorPrompt() { return designPromptService.getIllustratorPrompt(); }
    public String getProductDesignerDesignPrompt() { return designPromptService.getProductDesignerDesignPrompt(); }
    public String getUiDesignerPrompt() { return designPromptService.getUiDesignerPrompt(); }
    public String getUxDesignerPrompt() { return designPromptService.getUxDesignerPrompt(); }
    public String getUxResearcherPrompt() { return designPromptService.getUxResearcherPrompt(); }
    public String getInteractionDesignerPrompt() { return designPromptService.getInteractionDesignerPrompt(); }
    public String getVisualDesignerPrompt() { return designPromptService.getVisualDesignerPrompt(); }
    public String getUxUiDesignerPrompt() { return designPromptService.getUxUiDesignerPrompt(); }
    public String getMotionGraphicDesignerPrompt() { return designPromptService.getMotionGraphicDesignerPrompt(); }
    public String getVideoEditorPrompt() { return designPromptService.getVideoEditorPrompt(); }
    public String getVideographerPrompt() { return designPromptService.getVideographerPrompt(); }
    public String get3dArtistPrompt() { return designPromptService.get3dArtistPrompt(); }
    public String get3dModelerPrompt() { return designPromptService.get3dModelerPrompt(); }
    public String getAnimatorPrompt() { return designPromptService.getAnimatorPrompt(); }
    public String getVfxArtistPrompt() { return designPromptService.getVfxArtistPrompt(); }
    public String getVideoContentProducerPrompt() { return designPromptService.getVideoContentProducerPrompt(); }
    
    public String getCreativeCopywriterPrompt() { return designPromptService.getCreativeCopywriterPrompt(); }
    public String getCreativeStrategistPrompt() { return designPromptService.getCreativeStrategistPrompt(); }
    public String getContentCreatorPrompt() { return designPromptService.getContentCreatorPrompt(); }
    public String getSocialMediaCreativePrompt() { return designPromptService.getSocialMediaCreativePrompt(); }
    public String getArtDirectorPrompt() { return designPromptService.getArtDirectorPrompt(); }
    public String getCreativeDirectorPrompt() { return designPromptService.getCreativeDirectorPrompt(); }

    // Photography - Visual Arts
    public String getPhotographerPrompt() { return designPromptService.getPhotographerPrompt(); }
    public String getPhotoRetoucherPrompt() { return designPromptService.getPhotoRetoucherPrompt(); }
    public String getPhotoEditorPrompt() { return designPromptService.getPhotoEditorPrompt(); }
    public String getConceptArtistPrompt() { return designPromptService.getConceptArtistPrompt(); }
    public String getDigitalPainterPrompt() { return designPromptService.getDigitalPainterPrompt(); }

    // Emerging Creative Tech
    public String getAiArtistPrompt() { return designPromptService.getAiArtistPrompt(); }
    public String getPromptDesignerPrompt() { return designPromptService.getPromptDesignerPrompt(); }
    public String getArVrXrDesignerPrompt() { return designPromptService.getArVrXrDesignerPrompt(); }
    public String getVirtualInfluencerDesignerPrompt() { return designPromptService.getVirtualInfluencerDesignerPrompt(); }
    public String getGameArtistPrompt() { return designPromptService.getGameArtistPrompt(); }
    public String getEnvironmentArtistPrompt() { return designPromptService.getEnvironmentArtistPrompt(); }
    public String getUiArtistGamePrompt() { return designPromptService.getUiArtistGamePrompt(); }
    public String getCharacterDesignerPrompt() { return designPromptService.getCharacterDesignerPrompt(); }

    // Engineering & Industry
    public String getMechanicalEngineerPrompt() { return engineeringPromptService.getMechanicalEngineerPrompt(); }
    public String getMechatronicsEngineerPrompt() { return engineeringPromptService.getMechatronicsEngineerPrompt(); }
    public String getMaintenanceEngineerPrompt() { return engineeringPromptService.getMaintenanceEngineerPrompt(); }
    public String getCncMachinistPrompt() { return engineeringPromptService.getCncMachinistPrompt(); }
    public String getIndustrialMachineryTechnicianPrompt() { return engineeringPromptService.getIndustrialMachineryTechnicianPrompt(); }
    public String getManufacturingEngineerPrompt() { return engineeringPromptService.getManufacturingEngineerPrompt(); }
    public String getAutomotiveMechanicalTechnicianPrompt() { return engineeringPromptService.getAutomotiveMechanicalTechnicianPrompt(); }
    public String getElectricalEngineerPrompt() { return engineeringPromptService.getElectricalEngineerPrompt(); }
    public String getElectronicsEngineerPrompt() { return engineeringPromptService.getElectronicsEngineerPrompt(); }
    public String getElectricalMaintenanceTechnicianPrompt() { return engineeringPromptService.getElectricalMaintenanceTechnicianPrompt(); }
    public String getPowerSystemsEngineerPrompt() { return engineeringPromptService.getPowerSystemsEngineerPrompt(); }
    public String getRenewableEnergyEngineerPrompt() { return engineeringPromptService.getRenewableEnergyEngineerPrompt(); }
    public String getPcbEngineerPrompt() { return engineeringPromptService.getPcbEngineerPrompt(); }
    public String getSemiconductorProcessTechnicianPrompt() { return engineeringPromptService.getSemiconductorProcessTechnicianPrompt(); }
    public String getAutomationEngineerPrompt() { return engineeringPromptService.getAutomationEngineerPrompt(); }
    public String getPlcEngineerPrompt() { return engineeringPromptService.getPlcEngineerPrompt(); }
    public String getRoboticsEngineerPrompt() { return engineeringPromptService.getRoboticsEngineerPrompt(); }
    public String getIndustrialIoTEngineerPrompt() { return engineeringPromptService.getIndustrialIoTEngineerPrompt(); }
    public String getScadaTechnicianPrompt() { return engineeringPromptService.getScadaTechnicianPrompt(); }
    public String getInstrumentationEngineerPrompt() { return engineeringPromptService.getInstrumentationEngineerPrompt(); }
    public String getCivilEngineerPrompt() { return engineeringPromptService.getCivilEngineerPrompt(); }
    public String getStructuralEngineerPrompt() { return engineeringPromptService.getStructuralEngineerPrompt(); }
    public String getConstructionManagerPrompt() { return engineeringPromptService.getConstructionManagerPrompt(); }
    public String getQuantitySurveyorPrompt() { return engineeringPromptService.getQuantitySurveyorPrompt(); }
    public String getSiteEngineerPrompt() { return engineeringPromptService.getSiteEngineerPrompt(); }
    public String getArchitectureTechnicianPrompt() { return engineeringPromptService.getArchitectureTechnicianPrompt(); }
    public String getBimEngineerPrompt() { return engineeringPromptService.getBimEngineerPrompt(); }
    public String getIndustrialEngineerPrompt() { return engineeringPromptService.getIndustrialEngineerPrompt(); }
    public String getProductionPlannerPrompt() { return engineeringPromptService.getProductionPlannerPrompt(); }
    public String getQualityControlPrompt() { return engineeringPromptService.getQualityControlPrompt(); }
    public String getLeanManufacturingSpecialistPrompt() { return engineeringPromptService.getLeanManufacturingSpecialistPrompt(); }
    public String getSupplyChainEngineerPrompt() { return engineeringPromptService.getSupplyChainEngineerPrompt(); }
    public String getWarehouseOperationsEngineerPrompt() { return engineeringPromptService.getWarehouseOperationsEngineerPrompt(); }
    public String getHseEngineerPrompt() { return engineeringPromptService.getHseEngineerPrompt(); }
    public String getIndustrialHygienistPrompt() { return engineeringPromptService.getIndustrialHygienistPrompt(); }
    public String getFireProtectionEngineerPrompt() { return engineeringPromptService.getFireProtectionEngineerPrompt(); }

    // Healthcare & Medical
    public String getGeneralDoctorPrompt() { return healthcarePromptService.getGeneralDoctorPrompt(); }
    public String getSpecialistDoctorPrompt() { return healthcarePromptService.getSpecialistDoctorPrompt(); }
    public String getPediatricianPrompt() { return healthcarePromptService.getPediatricianPrompt(); }
    public String getCardiologistPrompt() { return healthcarePromptService.getCardiologistPrompt(); }
    public String getDermatologistPrompt() { return healthcarePromptService.getDermatologistPrompt(); }
    public String getRadiologistPrompt() { return healthcarePromptService.getRadiologistPrompt(); }
    public String getSurgeonPrompt() { return healthcarePromptService.getSurgeonPrompt(); }
    public String getRegisteredNursePrompt() { return healthcarePromptService.getRegisteredNursePrompt(); }
    public String getAssistantNursePrompt() { return healthcarePromptService.getAssistantNursePrompt(); }
    public String getClinicalCareSpecialistPrompt() { return healthcarePromptService.getClinicalCareSpecialistPrompt(); }
    public String getIcuNursePrompt() { return healthcarePromptService.getIcuNursePrompt(); }
    public String getEmergencyCareNursePrompt() { return healthcarePromptService.getEmergencyCareNursePrompt(); }
    public String getMedicalLaboratoryTechnicianPrompt() { return healthcarePromptService.getMedicalLaboratoryTechnicianPrompt(); }
    public String getRadiologicTechnologistPrompt() { return healthcarePromptService.getRadiologicTechnologistPrompt(); }
    public String getUltrasoundTechnicianPrompt() { return healthcarePromptService.getUltrasoundTechnicianPrompt(); }
    public String getPharmacyTechnicianPrompt() { return healthcarePromptService.getPharmacyTechnicianPrompt(); }
    public String getBiomedicalEngineerPrompt() { return healthcarePromptService.getBiomedicalEngineerPrompt(); }
    public String getPharmacistPrompt() { return healthcarePromptService.getPharmacistPrompt(); }
    public String getClinicalPharmacistPrompt() { return healthcarePromptService.getClinicalPharmacistPrompt(); }
    public String getPharmacyAssistantPrompt() { return healthcarePromptService.getPharmacyAssistantPrompt(); }
    public String getPharmaceuticalSalesRepresentativePrompt() { return healthcarePromptService.getPharmaceuticalSalesRepresentativePrompt(); }
    public String getPsychologistPrompt() { return healthcarePromptService.getPsychologistPrompt(); }
    public String getPsychotherapistPrompt() { return healthcarePromptService.getPsychotherapistPrompt(); }
    public String getSchoolCounselorPrompt() { return healthcarePromptService.getSchoolCounselorPrompt(); }
    public String getMentalHealthCounselorPrompt() { return healthcarePromptService.getMentalHealthCounselorPrompt(); }
    public String getBehavioralTherapistPrompt() { return healthcarePromptService.getBehavioralTherapistPrompt(); }
    public String getPublicHealthSpecialistPrompt() { return healthcarePromptService.getPublicHealthSpecialistPrompt(); }
    public String getNutritionistPrompt() { return healthcarePromptService.getNutritionistPrompt(); }
    public String getFitnessCoachPrompt() { return healthcarePromptService.getFitnessCoachPrompt(); }
    public String getHealthEducationSpecialistPrompt() { return healthcarePromptService.getHealthEducationSpecialistPrompt(); }
    public String getOccupationalTherapistPrompt() { return healthcarePromptService.getOccupationalTherapistPrompt(); }
    public String getSpeechTherapistPrompt() { return healthcarePromptService.getSpeechTherapistPrompt(); }
    
    // Education & Teaching
    public String getPreschoolTeacherPrompt() { return educationPromptService.getPreschoolTeacherPrompt(); }
    public String getPrimaryTeacherPrompt() { return educationPromptService.getPrimaryTeacherPrompt(); }
    public String getSecondaryTeacherPrompt() { return educationPromptService.getSecondaryTeacherPrompt(); }
    public String getUniversityLecturerPrompt() { return educationPromptService.getUniversityLecturerPrompt(); }
    public String getESLTeacherPrompt() { return educationPromptService.getESLTeacherPrompt(); }
    public String getSTEMTeacherPrompt() { return educationPromptService.getSTEMTeacherPrompt(); }
    public String getTutorPrompt() { return educationPromptService.getTutorPrompt(); }
    public String getTeachingAssistantPrompt() { return educationPromptService.getTeachingAssistantPrompt(); }
    public String getAcademicAdvisorPrompt() { return educationPromptService.getAcademicAdvisorPrompt(); }
    public String getStudentCounselorPrompt() { return educationPromptService.getStudentCounselorPrompt(); }
    public String getSchoolAdministrationOfficerPrompt() { return educationPromptService.getSchoolAdministrationOfficerPrompt(); }
    public String getCurriculumDeveloperPrompt() { return educationPromptService.getCurriculumDeveloperPrompt(); }
    public String getCorporateTrainerPrompt() { return educationPromptService.getCorporateTrainerPrompt(); }
    public String getLearningDevelopmentSpecialistPrompt() { return educationPromptService.getLearningDevelopmentSpecialistPrompt(); }
    public String getSoftSkillsTrainerPrompt() { return educationPromptService.getSoftSkillsTrainerPrompt(); }
    public String getCareerCoachPrompt() { return educationPromptService.getCareerCoachPrompt(); }
    public String getPublicSpeakingCoachPrompt() { return educationPromptService.getPublicSpeakingCoachPrompt(); }
    public String getLeadershipCoachPrompt() { return educationPromptService.getLeadershipCoachPrompt(); }
    public String getSpecialEducationTeacherPrompt() { return educationPromptService.getSpecialEducationTeacherPrompt(); }
    public String getEducationSpeechTherapistPrompt() { return educationPromptService.getSpeechTherapistPrompt(); }
    public String getOccupationalTherapyTeacherPrompt() { return educationPromptService.getOccupationalTherapyTeacherPrompt(); }
    public String getLearningDisabilitiesSpecialistPrompt() { return educationPromptService.getLearningDisabilitiesSpecialistPrompt(); }
    public String getEdTechProductSpecialistPrompt() { return educationPromptService.getEdTechProductSpecialistPrompt(); }
    public String getInstructionalDesignerPrompt() { return educationPromptService.getInstructionalDesignerPrompt(); }
    public String getElearningContentCreatorPrompt() { return educationPromptService.getElearningContentCreatorPrompt(); }
    public String getAcademicContentWriterPrompt() { return educationPromptService.getAcademicContentWriterPrompt(); }
    public String getOnlineCourseCreatorPrompt() { return educationPromptService.getOnlineCourseCreatorPrompt(); }
    public String getAssessmentDesignerPrompt() { return educationPromptService.getAssessmentDesignerPrompt(); }
    
    // Legal & Public Administration
    public String getLawyerPrompt() { return legalPromptService.getLawyerPrompt(); }
    public String getLegalConsultantPrompt() { return legalPromptService.getLegalConsultantPrompt(); }
    public String getLegalExecutivePrompt() { return legalPromptService.getLegalExecutivePrompt(); }
    public String getCorporateLegalSpecialistPrompt() { return legalPromptService.getCorporateLegalSpecialistPrompt(); }
    public String getIntellectualPropertySpecialistPrompt() { return legalPromptService.getIntellectualPropertySpecialistPrompt(); }
    public String getContractSpecialistPrompt() { return legalPromptService.getContractSpecialistPrompt(); }
    public String getComplianceOfficerPrompt() { return legalPromptService.getComplianceOfficerPrompt(); }
    public String getNotaryOfficerPrompt() { return legalPromptService.getNotaryOfficerPrompt(); }
    public String getLegalAssistantPrompt() { return legalPromptService.getLegalAssistantPrompt(); }

    // Judiciary & Court Services
    public String getJudgeAssistantPrompt() { return legalPromptService.getJudgeAssistantPrompt(); }
    public String getCourtClerkPrompt() { return legalPromptService.getCourtClerkPrompt(); }
    public String getProsecutorAssistantPrompt() { return legalPromptService.getProsecutorAssistantPrompt(); }
    public String getMediatorArbitratorPrompt() { return legalPromptService.getMediatorArbitratorPrompt(); }
    public String getEnforcementOfficerPrompt() { return legalPromptService.getEnforcementOfficerPrompt(); }

    // Public Administration
    public String getPublicAdministrationOfficerPrompt() { return legalPromptService.getPublicAdministrationOfficerPrompt(); }
    public String getGovernmentPolicyOfficerPrompt() { return legalPromptService.getGovernmentPolicyOfficerPrompt(); }
    public String getAdministrativeSpecialistPrompt() { return legalPromptService.getAdministrativeSpecialistPrompt(); }
    public String getPlanningStatisticsOfficerPrompt() { return legalPromptService.getPlanningStatisticsOfficerPrompt(); }
    public String getPublicFinanceOfficerPrompt() { return legalPromptService.getPublicFinanceOfficerPrompt(); }
    public String getCivilServantGeneralTrackPrompt() { return legalPromptService.getCivilServantGeneralTrackPrompt(); }
    public String getCommunityDevelopmentOfficerPrompt() { return legalPromptService.getCommunityDevelopmentOfficerPrompt(); }

    // Security – Public Service
    public String getPoliceOfficerPrompt() { return legalPromptService.getPoliceOfficerPrompt(); }
    public String getImmigrationOfficerPrompt() { return legalPromptService.getImmigrationOfficerPrompt(); }
    public String getCustomsOfficerPrompt() { return legalPromptService.getCustomsOfficerPrompt(); }
    public String getFireServiceOfficerPrompt() { return legalPromptService.getFireServiceOfficerPrompt(); }
    public String getSocialSecurityOfficerPrompt() { return legalPromptService.getSocialSecurityOfficerPrompt(); }
    public String getPublicHealthAdministrationOfficerPrompt() { return legalPromptService.getPublicHealthAdministrationOfficerPrompt(); }
    public String getCitizenServiceSpecialistPrompt() { return legalPromptService.getCitizenServiceSpecialistPrompt(); }
    public String getPublicSafetySpecialistPrompt() { return legalPromptService.getPublicSafetySpecialistPrompt(); }
    public String getInspectorPrompt() { return legalPromptService.getInspectorPrompt(); }

    // Logistics Operations
    public String getLogisticsCoordinatorPrompt() { return logisticsPromptService.getLogisticsCoordinatorPrompt(); }
    public String getWarehouseStaffPrompt() { return logisticsPromptService.getWarehouseStaffPrompt(); }
    public String getWarehouseManagerPrompt() { return logisticsPromptService.getWarehouseManagerPrompt(); }
    public String getInventoryControllerPrompt() { return logisticsPromptService.getInventoryControllerPrompt(); }
    public String getFulfillmentSpecialistPrompt() { return logisticsPromptService.getFulfillmentSpecialistPrompt(); }
    public String getSupplyChainPlannerPrompt() { return logisticsPromptService.getSupplyChainPlannerPrompt(); }
    public String getTransportPlannerPrompt() { return logisticsPromptService.getTransportPlannerPrompt(); }
    public String getFleetManagerPrompt() { return logisticsPromptService.getFleetManagerPrompt(); }
    public String getDistributionCenterOperatorPrompt() { return logisticsPromptService.getDistributionCenterOperatorPrompt(); }

    // Freight & Shipping
    public String getFreightForwarderPrompt() { return logisticsPromptService.getFreightForwarderPrompt(); }
    public String getOceanFreightSpecialistPrompt() { return logisticsPromptService.getOceanFreightSpecialistPrompt(); }
    public String getAirFreightSpecialistPrompt() { return logisticsPromptService.getAirFreightSpecialistPrompt(); }
    public String getRoadFreightCoordinatorPrompt() { return logisticsPromptService.getRoadFreightCoordinatorPrompt(); }
    public String getCustomsClearanceStaffPrompt() { return logisticsPromptService.getCustomsClearanceStaffPrompt(); }
    public String getImportExportExecutivePrompt() { return logisticsPromptService.getImportExportExecutivePrompt(); }
    public String getShippingDocumentationOfficerPrompt() { return logisticsPromptService.getShippingDocumentationOfficerPrompt(); }
    public String getVesselPlannerPrompt() { return logisticsPromptService.getVesselPlannerPrompt(); }

    // Supply Chain Management
    public String getSupplyChainAnalystPrompt() { return logisticsPromptService.getSupplyChainAnalystPrompt(); }
    public String getSupplyChainManagerPrompt() { return logisticsPromptService.getSupplyChainManagerPrompt(); }
    public String getDemandPlannerPrompt() { return logisticsPromptService.getDemandPlannerPrompt(); }
    public String getProcurementOfficerPrompt() { return logisticsPromptService.getProcurementOfficerPrompt(); }
    public String getVendorManagementSpecialistPrompt() { return logisticsPromptService.getVendorManagementSpecialistPrompt(); }
    public String getOrderManagementSpecialistPrompt() { return logisticsPromptService.getOrderManagementSpecialistPrompt(); }
    public String getLogisticsProductionPlannerPrompt() { return logisticsPromptService.getProductionPlannerPrompt(); }

    // International Business – Trade
    public String getInternationalSalesExecutivePrompt() { return logisticsPromptService.getInternationalSalesExecutivePrompt(); }
    public String getTradeComplianceSpecialistPrompt() { return logisticsPromptService.getTradeComplianceSpecialistPrompt(); }
    public String getGlobalSourcingSpecialistPrompt() { return logisticsPromptService.getGlobalSourcingSpecialistPrompt(); }
    public String getInternationalBusinessDevelopmentPrompt() { return logisticsPromptService.getInternationalBusinessDevelopmentPrompt(); }
    public String getForeignTradeAnalystPrompt() { return logisticsPromptService.getForeignTradeAnalystPrompt(); }
    public String getCommercialInvoiceSpecialistPrompt() { return logisticsPromptService.getCommercialInvoiceSpecialistPrompt(); }
    public String getEcommerceFulfillmentSpecialistPrompt() { return logisticsPromptService.getEcommerceFulfillmentSpecialistPrompt(); }

    // Performing Arts
    public String getSingerPrompt() { return artsPromptService.getSingerPrompt(); }
    public String getDancerPrompt() { return artsPromptService.getDancerPrompt(); }
    public String getActorPrompt() { return artsPromptService.getActorPrompt(); }
    public String getStagePerformerPrompt() { return artsPromptService.getStagePerformerPrompt(); }
    public String getTheatreActorPrompt() { return artsPromptService.getTheatreActorPrompt(); }
    public String getMusicalPerformerPrompt() { return artsPromptService.getMusicalPerformerPrompt(); }
    public String getStuntPerformerPrompt() { return artsPromptService.getStuntPerformerPrompt(); }

    // Audio – Music – Voice
    public String getMusicProducerPrompt() { return artsPromptService.getMusicProducerPrompt(); }
    public String getMusicComposerPrompt() { return artsPromptService.getMusicComposerPrompt(); }
    public String getSoundDesignerPrompt() { return artsPromptService.getSoundDesignerPrompt(); }
    public String getAudioEngineerPrompt() { return artsPromptService.getAudioEngineerPrompt(); }
    public String getVoiceActorPrompt() { return artsPromptService.getVoiceActorPrompt(); }
    public String getDjElectronicMusicArtistPrompt() { return artsPromptService.getDjElectronicMusicArtistPrompt(); }

    // Entertainment – Digital Creator
    public String getStreamerPrompt() { return artsPromptService.getStreamerPrompt(); }
    public String getKolKocInfluencerPrompt() { return artsPromptService.getKolKocInfluencerPrompt(); }
    public String getSocialMediaEntertainerPrompt() { return artsPromptService.getSocialMediaEntertainerPrompt(); }
    public String getCosplayerPrompt() { return artsPromptService.getCosplayerPrompt(); }
    public String getVirtualIdolPerformerPrompt() { return artsPromptService.getVirtualIdolPerformerPrompt(); }
    public String getHostMCPrompt() { return artsPromptService.getHostMCPrompt(); }
    public String getPodcasterPrompt() { return artsPromptService.getPodcasterPrompt(); }

    // Fashion – Modeling – Beauty
    public String getFashionModelPrompt() { return artsPromptService.getFashionModelPrompt(); }
    public String getRunwayModelPrompt() { return artsPromptService.getRunwayModelPrompt(); }
    public String getCommercialModelPrompt() { return artsPromptService.getCommercialModelPrompt(); }
    public String getFashionStylistPrompt() { return artsPromptService.getFashionStylistPrompt(); }
    public String getMakeupArtistPrompt() { return artsPromptService.getMakeupArtistPrompt(); }
    public String getCostumeDesignerPrompt() { return artsPromptService.getCostumeDesignerPrompt(); }
    public String getImageConsultantPrompt() { return artsPromptService.getImageConsultantPrompt(); }

    // Film – Stage – Production
    public String getFilmDirectorPrompt() { return artsPromptService.getFilmDirectorPrompt(); }
    public String getAssistantDirectorPrompt() { return artsPromptService.getAssistantDirectorPrompt(); }
    public String getProducerPrompt() { return artsPromptService.getProducerPrompt(); }
    public String getScreenwriterPrompt() { return artsPromptService.getScreenwriterPrompt(); }
    public String getChoreographerPrompt() { return artsPromptService.getChoreographerPrompt(); }
    public String getStageManagerPrompt() { return artsPromptService.getStageManagerPrompt(); }
    public String getCastingDirectorPrompt() { return artsPromptService.getCastingDirectorPrompt(); }
    public String getProductionAssistantPrompt() { return artsPromptService.getProductionAssistantPrompt(); }

    // Food & Beverage
    public String getWaiterWaitressPrompt() { return servicePromptService.getWaiterWaitressPrompt(); }
    public String getHostReceptionFBPrompt() { return servicePromptService.getHostReceptionFBPrompt(); }
    public String getBaristaPrompt() { return servicePromptService.getBaristaPrompt(); }
    public String getBartenderPrompt() { return servicePromptService.getBartenderPrompt(); }
    public String getCashierFBPrompt() { return servicePromptService.getCashierFBPrompt(); }
    public String getFBSupervisorPrompt() { return servicePromptService.getFBSupervisorPrompt(); }
    public String getRestaurantManagerPrompt() { return servicePromptService.getRestaurantManagerPrompt(); }
    public String getBanquetServerPrompt() { return servicePromptService.getBanquetServerPrompt(); }
    public String getCateringCoordinatorPrompt() { return servicePromptService.getCateringCoordinatorPrompt(); }

    // Hotel & Hospitality
    public String getHotelReceptionistPrompt() { return servicePromptService.getHotelReceptionistPrompt(); }
    public String getConciergePrompt() { return servicePromptService.getConciergePrompt(); }
    public String getBellmanPrompt() { return servicePromptService.getBellmanPrompt(); }
    public String getHousekeepingPrompt() { return servicePromptService.getHousekeepingPrompt(); }
    public String getHousekeepingSupervisorPrompt() { return servicePromptService.getHousekeepingSupervisorPrompt(); }
    public String getGuestRelationsOfficerPrompt() { return servicePromptService.getGuestRelationsOfficerPrompt(); }
    public String getFrontOfficeManagerPrompt() { return servicePromptService.getFrontOfficeManagerPrompt(); }
    public String getHotelGeneralManagerPrompt() { return servicePromptService.getHotelGeneralManagerPrompt(); }
    public String getResortStaffPrompt() { return servicePromptService.getResortStaffPrompt(); }
    public String getTourDeskOfficerPrompt() { return servicePromptService.getTourDeskOfficerPrompt(); }

    // Travel – Tourism – Event
    public String getTourGuidePrompt() { return servicePromptService.getTourGuidePrompt(); }
    public String getTravelConsultantPrompt() { return servicePromptService.getTravelConsultantPrompt(); }
    public String getEventAssistantPrompt() { return servicePromptService.getEventAssistantPrompt(); }
    public String getEventCoordinatorPrompt() { return servicePromptService.getEventCoordinatorPrompt(); }
    public String getEventManagerPrompt() { return servicePromptService.getEventManagerPrompt(); }
    public String getTicketingOfficerPrompt() { return servicePromptService.getTicketingOfficerPrompt(); }
    public String getCruiseServiceStaffPrompt() { return servicePromptService.getCruiseServiceStaffPrompt(); }

    // Beauty – Spa – Wellness
    public String getSpaTherapistPrompt() { return servicePromptService.getSpaTherapistPrompt(); }
    public String getNailTechnicianPrompt() { return servicePromptService.getNailTechnicianPrompt(); }
    public String getHairStylistPrompt() { return servicePromptService.getHairStylistPrompt(); }
    public String getMasseuseMassageTherapistPrompt() { return servicePromptService.getMasseuseMassageTherapistPrompt(); }
    public String getBeautyConsultantPrompt() { return servicePromptService.getBeautyConsultantPrompt(); }
    public String getSkincareSpecialistPrompt() { return servicePromptService.getSkincareSpecialistPrompt(); }

    // Customer Service – Call Center
    public String getCustomerServiceRepresentativePrompt() { return servicePromptService.getCustomerServiceRepresentativePrompt(); }
    public String getCallCenterAgentPrompt() { return servicePromptService.getCallCenterAgentPrompt(); }
    public String getLiveChatSupportPrompt() { return servicePromptService.getLiveChatSupportPrompt(); }
    public String getServiceQualityOfficerPrompt() { return servicePromptService.getServiceQualityOfficerPrompt(); }
    public String getCustomerExperienceSpecialistPrompt() { return servicePromptService.getCustomerExperienceSpecialistPrompt(); }
    public String getTechnicalSupportPrompt() { return servicePromptService.getTechnicalSupportPrompt(); }

    // Retail – Store Operations
    public String getSalesAssociatePrompt() { return servicePromptService.getSalesAssociatePrompt(); }
    public String getStoreSupervisorPrompt() { return servicePromptService.getStoreSupervisorPrompt(); }
    public String getRetailManagerPrompt() { return servicePromptService.getRetailManagerPrompt(); }
    public String getVisualMerchandiserPrompt() { return servicePromptService.getVisualMerchandiserPrompt(); }

    // Social Work - Social Community
    public String getSocialWorkerPrompt() { return socialCommunityPromptService.getSocialWorkerPrompt(); }
    public String getCaseManagerPrompt() { return socialCommunityPromptService.getCaseManagerPrompt(); }
    public String getFamilySupportWorkerPrompt() { return socialCommunityPromptService.getFamilySupportWorkerPrompt(); }
    public String getChildProtectionOfficerPrompt() { return socialCommunityPromptService.getChildProtectionOfficerPrompt(); }
    public String getElderlyCareWorkerPrompt() { return socialCommunityPromptService.getElderlyCareWorkerPrompt(); }
    public String getDisabilitySupportWorkerPrompt() { return socialCommunityPromptService.getDisabilitySupportWorkerPrompt(); }
    public String getCrisisInterventionSpecialistPrompt() { return socialCommunityPromptService.getCrisisInterventionSpecialistPrompt(); }

    // Community Development - Social Community
    public String getYouthWorkerPrompt() { return socialCommunityPromptService.getYouthWorkerPrompt(); }
    public String getCommunityOutreachCoordinatorPrompt() { return socialCommunityPromptService.getCommunityOutreachCoordinatorPrompt(); }
    public String getSocialProgramCoordinatorPrompt() { return socialCommunityPromptService.getSocialProgramCoordinatorPrompt(); }
    public String getCommunityHealthWorkerPrompt() { return socialCommunityPromptService.getCommunityHealthWorkerPrompt(); }
    public String getNGOProjectOfficerPrompt() { return socialCommunityPromptService.getNGOProjectOfficerPrompt(); }
    public String getFundraisingSpecialistPrompt() { return socialCommunityPromptService.getFundraisingSpecialistPrompt(); }

    // Counseling – Support Services - Social Community
    public String getSocialCounselorPrompt() { return socialCommunityPromptService.getSocialCounselorPrompt(); }
    public String getFamilyCounselorPrompt() { return socialCommunityPromptService.getFamilyCounselorPrompt(); }
    public String getRehabilitationCounselorPrompt() { return socialCommunityPromptService.getRehabilitationCounselorPrompt(); }
    public String getAddictionCounselorPrompt() { return socialCommunityPromptService.getAddictionCounselorPrompt(); }
    public String getTraumaSupportSpecialistPrompt() { return socialCommunityPromptService.getTraumaSupportSpecialistPrompt(); }

    // Nonprofit & Public Service - Social Community
    public String getNGOCoordinatorPrompt() { return socialCommunityPromptService.getNGOCoordinatorPrompt(); }
    public String getVolunteerCoordinatorPrompt() { return socialCommunityPromptService.getVolunteerCoordinatorPrompt(); }
    public String getProgramEvaluatorPrompt() { return socialCommunityPromptService.getProgramEvaluatorPrompt(); }
    public String getHumanitarianAidWorkerPrompt() { return socialCommunityPromptService.getHumanitarianAidWorkerPrompt(); }
    public String getCommunityServiceManagerPrompt() { return socialCommunityPromptService.getCommunityServiceManagerPrompt(); }
    public String getPublicWelfareOfficerPrompt() { return socialCommunityPromptService.getPublicWelfareOfficerPrompt(); }

    // Agriculture - Agriculture Environment
    public String getAgronomistPrompt() { return agricultureEnvironmentPromptService.getAgronomistPrompt(); }
    public String getCropProductionSpecialistPrompt() { return agricultureEnvironmentPromptService.getCropProductionSpecialistPrompt(); }
    public String getHorticulturistPrompt() { return agricultureEnvironmentPromptService.getHorticulturistPrompt(); }
    public String getSmartFarmingTechnicianPrompt() { return agricultureEnvironmentPromptService.getSmartFarmingTechnicianPrompt(); }
    public String getAgriculturalTechnicianPrompt() { return agricultureEnvironmentPromptService.getAgriculturalTechnicianPrompt(); }
    public String getPlantProtectionSpecialistPrompt() { return agricultureEnvironmentPromptService.getPlantProtectionSpecialistPrompt(); }
    public String getSoilScienceSpecialistPrompt() { return agricultureEnvironmentPromptService.getSoilScienceSpecialistPrompt(); }
    public String getSeedProductionSpecialistPrompt() { return agricultureEnvironmentPromptService.getSeedProductionSpecialistPrompt(); }

    // Livestock – Veterinary - Agriculture Environment
    public String getLivestockTechnicianPrompt() { return agricultureEnvironmentPromptService.getLivestockTechnicianPrompt(); }
    public String getAnimalNutritionistPrompt() { return agricultureEnvironmentPromptService.getAnimalNutritionistPrompt(); }
    public String getVeterinarianPrompt() { return agricultureEnvironmentPromptService.getVeterinarianPrompt(); }
    public String getVeterinaryTechnicianPrompt() { return agricultureEnvironmentPromptService.getVeterinaryTechnicianPrompt(); }
    public String getAnimalCareSpecialistPrompt() { return agricultureEnvironmentPromptService.getAnimalCareSpecialistPrompt(); }

    // Aquaculture – Fisheries - Agriculture Environment
    public String getAquacultureSpecialistPrompt() { return agricultureEnvironmentPromptService.getAquacultureSpecialistPrompt(); }
    public String getFisheriesTechnicianPrompt() { return agricultureEnvironmentPromptService.getFisheriesTechnicianPrompt(); }
    public String getMarineConservationOfficerPrompt() { return agricultureEnvironmentPromptService.getMarineConservationOfficerPrompt(); }
    public String getWaterQualityTechnicianPrompt() { return agricultureEnvironmentPromptService.getWaterQualityTechnicianPrompt(); }

    // Biotechnology & Food Science - Agriculture Environment
    public String getBiotechnologistPrompt() { return agricultureEnvironmentPromptService.getBiotechnologistPrompt(); }
    public String getLabTechnicianBiologyPrompt() { return agricultureEnvironmentPromptService.getLabTechnicianBiologyPrompt(); }
    public String getFoodTechnologySpecialistPrompt() { return agricultureEnvironmentPromptService.getFoodTechnologySpecialistPrompt(); }
    public String getFoodSafetyInspectorPrompt() { return agricultureEnvironmentPromptService.getFoodSafetyInspectorPrompt(); }
    public String getMicrobiologyTechnicianPrompt() { return agricultureEnvironmentPromptService.getMicrobiologyTechnicianPrompt(); }

    // Environment – Conservation - Agriculture Environment
    public String getEnvironmentalEngineerPrompt() { return agricultureEnvironmentPromptService.getEnvironmentalEngineerPrompt(); }
    public String getEnvironmentalScientistPrompt() { return agricultureEnvironmentPromptService.getEnvironmentalScientistPrompt(); }
    public String getWasteManagementSpecialistPrompt() { return agricultureEnvironmentPromptService.getWasteManagementSpecialistPrompt(); }
    public String getEcologyResearcherPrompt() { return agricultureEnvironmentPromptService.getEcologyResearcherPrompt(); }
    public String getRenewableEnergyTechnicianPrompt() { return agricultureEnvironmentPromptService.getRenewableEnergyTechnicianPrompt(); }
    public String getForestConservationOfficerPrompt() { return agricultureEnvironmentPromptService.getForestConservationOfficerPrompt(); }
    public String getGISSpecialistPrompt() { return agricultureEnvironmentPromptService.getGISSpecialistPrompt(); }

    // Climate – Water – Meteorology - Agriculture Environment
    public String getHydrologistPrompt() { return agricultureEnvironmentPromptService.getHydrologistPrompt(); }
    public String getMeteorologistPrompt() { return agricultureEnvironmentPromptService.getMeteorologistPrompt(); }
    public String getClimateChangeAnalystPrompt() { return agricultureEnvironmentPromptService.getClimateChangeAnalystPrompt(); }
    public String getWaterResourcesEngineerPrompt() { return agricultureEnvironmentPromptService.getWaterResourcesEngineerPrompt(); }
}
