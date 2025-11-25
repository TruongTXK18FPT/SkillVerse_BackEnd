package com.exe.skillverse_backend.ai_service.config;

import com.exe.skillverse_backend.ai_service.entity.ExpertPromptConfig;
import com.exe.skillverse_backend.ai_service.repository.ExpertPromptConfigRepository;
import com.exe.skillverse_backend.ai_service.service.ExpertPromptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Initializer to seed hardcoded expert prompts into the database
 * if the database is empty. This ensures a smooth transition from
 * hardcoded logic to database-driven configuration.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExpertPromptInitializer implements CommandLineRunner {

    private final ExpertPromptConfigRepository repository;
    private final ExpertPromptService expertPromptService;
    
    @Override
    public void run(String... args) throws Exception {
        log.info("Seeding/Updating expert prompts into database...");

        // SOFTWARE DEVELOPMENT
        String itDomain = "Information Technology";
        String softwareIndustry = "Software Development";
        createConfig(itDomain, softwareIndustry, "Backend Developer", 
            "backend, api, server, database", expertPromptService.getBackendDeveloperPrompt());
        createConfig(itDomain, softwareIndustry, "Frontend Developer", 
            "frontend, react, vue, angular", expertPromptService.getFrontendDeveloperPrompt());
        createConfig(itDomain, softwareIndustry, "Fullstack Developer", 
            "fullstack, full-stack, web development", expertPromptService.getFullstackDeveloperPrompt());
        createConfig(itDomain, softwareIndustry, "Mobile Developer", 
            "mobile, ios, android, flutter", expertPromptService.getMobileDeveloperPrompt());
        createConfig(itDomain, softwareIndustry, "DevOps Engineer", 
            "devops, ci/cd, docker, kubernetes", expertPromptService.getDevOpsEngineerPrompt());
        createConfig(itDomain, softwareIndustry, "QA/Tester", 
            "qa, tester, testing, automation", expertPromptService.getAutomationQAPrompt());
        createConfig(itDomain, softwareIndustry, "UI/UX Designer", 
            "ui, ux, design, figma", expertPromptService.getUxUiDesignerPrompt());
        createConfig(itDomain, softwareIndustry, "Product Manager", 
            "product manager, pm, product strategy", expertPromptService.getProductManagerPrompt());
        createConfig(itDomain, softwareIndustry, "Product Owner", 
            "product owner, po, scrum, backlog", expertPromptService.getProductOwnerPrompt());
        createConfig(itDomain, softwareIndustry, "Software Architect", 
            "architect, solution architect", expertPromptService.getSoftwareArchitectPrompt());
        createConfig(itDomain, softwareIndustry, "Automation QA", 
            "automation, tester, selenium", expertPromptService.getAutomationQAPrompt());
        createConfig(itDomain, softwareIndustry, "Manual QA", 
            "manual, tester, qc", expertPromptService.getManualTesterPrompt());

        // DATA & AI (Now under Information Technology)
        createConfig("Information Technology", "Data Science", "Data Analyst", 
            "data analyst, da, sql, excel", expertPromptService.getDataAnalystPrompt());
        createConfig("Information Technology", "Data Science", "Business Intelligence Analyst", 
            "bi, business intelligence, powerbi", expertPromptService.getBusinessIntelligencePrompt());
        createConfig("Information Technology", "Data Engineering", "Data Engineer", 
            "data engineer, etl, spark", expertPromptService.getDataEngineerPrompt());
        createConfig("Information Technology", "Artificial Intelligence", "Machine Learning Engineer", 
            "ml, machine learning, ai", expertPromptService.getMachineLearningEngineerPrompt());
        createConfig("Information Technology", "Artificial Intelligence", "AI Engineer", 
            "ai engineer, llm, generative ai", expertPromptService.getAiEngineerPrompt());
        createConfig("Information Technology", "Data Science", "Data Scientist", 
            "data scientist, ds, statistics", expertPromptService.getDataScientistPrompt());
        createConfig("Information Technology", "Artificial Intelligence", "Prompt Engineer", 
            "prompt engineer, prompting", expertPromptService.getPromptEngineerPrompt());

        // CYBERSECURITY
        createConfig("Information Technology", "Cybersecurity", "Cybersecurity Analyst", 
            "security analyst, threat", expertPromptService.getCybersecurityAnalystPrompt());
        createConfig("Information Technology", "Cybersecurity", "Security Engineer", 
            "security engineer, infosec", expertPromptService.getSecurityEngineerPrompt());
        createConfig("Information Technology", "Cybersecurity", "Penetration Tester", 
            "pentester, ethical hacker", expertPromptService.getPentesterPrompt());
        createConfig("Information Technology", "Cybersecurity", "SOC Analyst", 
            "soc, operation center", expertPromptService.getSocAnalystPrompt());
        createConfig("Information Technology", "Cybersecurity", "Network Security Engineer", 
            "network security, firewall", expertPromptService.getNetworkSecurityEngineerPrompt());

        // CLOUD & INFRASTRUCTURE
        createConfig("Information Technology", "Cloud & Infrastructure", "Cloud Engineer", 
            "cloud engineer, aws, azure", expertPromptService.getCloudEngineerPrompt());
        createConfig("Information Technology", "Cloud & Infrastructure", "Cloud Architect", 
            "cloud architect, solutions architect", expertPromptService.getCloudArchitectPrompt());
        createConfig("Information Technology", "Cloud & Infrastructure", "System Administrator", 
            "sysadmin, admin, linux", expertPromptService.getSystemAdministratorPrompt());
        createConfig("Information Technology", "Cloud & Infrastructure", "Network Engineer", 
            "network engineer, cisco", expertPromptService.getNetworkEngineerPrompt());

        // BUSINESS & MARKETING
        String bizDomain = "Kinh doanh – Marketing – Quản trị";
        String marketingIndustry = "Marketing";
        
        createConfig(bizDomain, marketingIndustry, "Digital Marketing", 
            "digital marketing, ads, seo", expertPromptService.getDigitalMarketingPrompt());
        createConfig(bizDomain, marketingIndustry, "Content Marketing", 
            "content marketing, copywriter, writing", expertPromptService.getContentMarketingPrompt());
        createConfig(bizDomain, marketingIndustry, "Social Media Executive", 
            "social media, facebook, tiktok, community", expertPromptService.getSocialMediaExecutivePrompt());
        createConfig(bizDomain, marketingIndustry, "Performance Marketing", 
            "performance marketing, ads, tracking", expertPromptService.getPerformanceMarketingPrompt());
        createConfig(bizDomain, marketingIndustry, "SEO Specialist", 
            "seo, onpage, offpage, keyword", expertPromptService.getSeoSpecialistPrompt());
        createConfig(bizDomain, marketingIndustry, "Email Marketing", 
            "email marketing, automation, crm", expertPromptService.getEmailMarketingPrompt());
        createConfig(bizDomain, marketingIndustry, "Brand Executive/Manager", 
            "brand, branding, identity", expertPromptService.getBrandExecutivePrompt());
        createConfig(bizDomain, marketingIndustry, "Creative Planner", 
            "creative, planner, concept, idea", expertPromptService.getCreativePlannerPrompt());
        createConfig(bizDomain, marketingIndustry, "Copywriter", 
            "copywriter, slogan, content", expertPromptService.getCopywriterPrompt());
        createConfig(bizDomain, marketingIndustry, "Marketing Analyst", 
            "marketing analyst, research, data", expertPromptService.getMarketingAnalystPrompt());

        // BUSINESS & MANAGEMENT (NEW)
        String managementIndustry = "Business & Management";

        createConfig(bizDomain, managementIndustry, "Business Analyst (BA)", 
            "business analyst, ba, process, requirements", expertPromptService.getBusinessAnalystPrompt());
        createConfig(bizDomain, managementIndustry, "Operations Executive/Manager", 
            "operations, vận hành, admin, process", expertPromptService.getOperationsManagerPrompt());
        createConfig(bizDomain, managementIndustry, "Project Manager", 
            "project manager, pm, pmp, agile", expertPromptService.getProjectManagerBusinessPrompt());
        createConfig(bizDomain, managementIndustry, "HR - Recruitment", 
            "hr, recruitment, talent acquisition, headhunter", expertPromptService.getHrRecruitmentPrompt());
        createConfig(bizDomain, managementIndustry, "HR - Talent Development", 
            "hr, l&d, training, development", expertPromptService.getHrTalentDevelopmentPrompt());
        createConfig(bizDomain, managementIndustry, "Office Admin", 
            "admin, hành chính, văn thư, office", expertPromptService.getOfficeAdminPrompt());
        createConfig(bizDomain, managementIndustry, "Customer Service (CSKH)", 
            "customer service, cskh, support, call center", expertPromptService.getCustomerServicePrompt());
        createConfig(bizDomain, managementIndustry, "Supply Chain Coordinator", 
            "supply chain, procurement, inventory", expertPromptService.getSupplyChainPrompt());
        createConfig(bizDomain, managementIndustry, "Logistics Executive", 
            "logistics, import, export, customs", expertPromptService.getLogisticsExecutivePrompt());

        // SALES & GROWTH (NEW)
        String salesIndustry = "Sales & Growth";
        
        createConfig(bizDomain, salesIndustry, "Sales Executive", 
            "sales, telesales, closing", expertPromptService.getSalesExecutivePrompt());
        createConfig(bizDomain, salesIndustry, "B2B Sales", 
            "b2b sales, corporate sales, consultation", expertPromptService.getB2bSalesPrompt());
        createConfig(bizDomain, salesIndustry, "Business Development (BD)", 
            "bd, partnership, development", expertPromptService.getBusinessDevelopmentPrompt());
        createConfig(bizDomain, salesIndustry, "Account Executive (Agency/SaaS)", 
            "account executive, ae, client service", expertPromptService.getAccountExecutivePrompt());
        createConfig(bizDomain, salesIndustry, "Key Account Manager (KAM)", 
            "kam, key account, relationship", expertPromptService.getKeyAccountManagerPrompt());
        createConfig(bizDomain, salesIndustry, "Growth Marketer", 
            "growth marketing, hacking, aarrr, product-led", expertPromptService.getGrowthMarketerPrompt());

        // FINANCE & ECONOMICS (Now under Business Domain)
        String financeIndustry = "Finance & Banking";
        
        createConfig(bizDomain, financeIndustry, "Corporate Finance Analyst", 
            "corporate finance, financial model, dcf, valuation", expertPromptService.getCorporateFinanceAnalystPrompt());
        createConfig(bizDomain, financeIndustry, "Accountant", 
            "accountant, kế toán, tax, audit, cpa", expertPromptService.getAccountantPrompt());
        createConfig(bizDomain, financeIndustry, "Investment Analyst", 
            "investment, stock, cfa, portfolio", expertPromptService.getInvestmentAnalystPrompt());
        createConfig(bizDomain, financeIndustry, "Banking Officer", 
            "banking, credit, rm, bank", expertPromptService.getBankingOfficerPrompt());
        createConfig(bizDomain, financeIndustry, "Fintech Product Analyst", 
            "fintech, payment, wallet, crypto", expertPromptService.getFintechProductAnalystPrompt());

        // ENTREPRENEURSHIP & STARTUP (New under Business Domain)
        String startupIndustry = "Entrepreneurship & Startup";
        
        createConfig(bizDomain, startupIndustry, "Startup Founder", 
            "startup, founder, fundraising, mvp", expertPromptService.getStartupFounderPrompt());
        createConfig(bizDomain, startupIndustry, "Business Consultant", 
            "consultant, strategy, advisory, problem solving", expertPromptService.getBusinessConsultantPrompt());
        createConfig(bizDomain, startupIndustry, "Entrepreneur in training", 
            "entrepreneur, trainee, intrapreneurship", expertPromptService.getEntrepreneurInTrainingPrompt());
        createConfig(bizDomain, startupIndustry, "Freelancer", 
            "freelancer, solopreneur, gig economy", expertPromptService.getFreelancerPrompt());

        // DESIGN & CREATIVE
        String designDomain = "Thiết kế – Sáng tạo – Nội dung";
        String graphicIndustry = "Graphic Design";

        createConfig(designDomain, graphicIndustry, "Graphic Designer", 
            "graphic designer, thiết kế đồ họa, 2d", expertPromptService.getGraphicDesignerPrompt());
        createConfig(designDomain, graphicIndustry, "Brand Designer", 
            "brand designer, branding, identity", expertPromptService.getBrandDesignerPrompt());
        createConfig(designDomain, graphicIndustry, "Logo & Identity Designer", 
            "logo designer, identity, symbol", expertPromptService.getLogoIdentityDesignerPrompt());
        createConfig(designDomain, graphicIndustry, "Layout Designer", 
            "layout designer, editorial, indesign", expertPromptService.getLayoutDesignerPrompt());
        createConfig(designDomain, graphicIndustry, "Packaging Designer", 
            "packaging, bao bì, 3d packaging", expertPromptService.getPackagingDesignerPrompt());
        createConfig(designDomain, graphicIndustry, "Print Designer", 
            "print, in ấn, pre-press", expertPromptService.getPrintDesignerPrompt());
        createConfig(designDomain, graphicIndustry, "Illustrator", 
            "illustrator, minh họa, artist, drawing", expertPromptService.getIllustratorPrompt());

        // UI/UX - PRODUCT DESIGN
        String uxIndustry = "UI/UX & Product Design";
        
        createConfig(designDomain, uxIndustry, "Product Designer", 
            "product designer, ux research, interaction, design system", expertPromptService.getProductDesignerDesignPrompt());
        createConfig(designDomain, uxIndustry, "UI Designer", 
            "ui designer, interface, visual, figma", expertPromptService.getUiDesignerPrompt());
        createConfig(designDomain, uxIndustry, "UX Designer", 
            "ux designer, user experience, wireframe, research", expertPromptService.getUxDesignerPrompt());
        createConfig(designDomain, uxIndustry, "UX Researcher", 
            "ux researcher, research, user research, testing", expertPromptService.getUxResearcherPrompt());
        createConfig(designDomain, uxIndustry, "Interaction Designer", 
            "interaction designer, ixd, motion, prototype", expertPromptService.getInteractionDesignerPrompt());
        createConfig(designDomain, uxIndustry, "Visual Designer", 
            "visual designer, digital branding, moodboard", expertPromptService.getVisualDesignerPrompt());

        // MOTION - VIDEO - MULTIMEDIA
        String multimediaIndustry = "Motion & Video & Multimedia";
        
        createConfig(designDomain, multimediaIndustry, "Motion Graphic Designer", 
            "motion graphic, animation, after effects", expertPromptService.getMotionGraphicDesignerPrompt());
        createConfig(designDomain, multimediaIndustry, "Video Editor", 
            "video editor, premiere, final cut, editing", expertPromptService.getVideoEditorPrompt());
        createConfig(designDomain, multimediaIndustry, "Videographer", 
            "videographer, camera, filming, video production", expertPromptService.getVideographerPrompt());
        createConfig(designDomain, multimediaIndustry, "3D Artist", 
            "3d artist, blender, maya, 3d art", expertPromptService.get3dArtistPrompt());
        createConfig(designDomain, multimediaIndustry, "3D Modeler", 
            "3d modeler, modeling, zbrush, sculpting", expertPromptService.get3dModelerPrompt());
        createConfig(designDomain, multimediaIndustry, "Animator (2D/3D)", 
            "animator, 2d animation, 3d animation", expertPromptService.getAnimatorPrompt());
        createConfig(designDomain, multimediaIndustry, "VFX Artist", 
            "vfx artist, visual effects, compositing", expertPromptService.getVfxArtistPrompt());
        createConfig(designDomain, multimediaIndustry, "Video Content Producer", 
            "video producer, content producer, video strategy", expertPromptService.getVideoContentProducerPrompt());

        // CREATIVE CONTENT & COMMUNICATION
        String creativeIndustry = "Creative Content & Communication";
        
        createConfig(designDomain, creativeIndustry, "Creative Copywriter", 
            "creative copywriter, concept, storytelling, idea", expertPromptService.getCreativeCopywriterPrompt());
        createConfig(designDomain, creativeIndustry, "Creative Strategist", 
            "creative strategist, strategy, insight, planning", expertPromptService.getCreativeStrategistPrompt());
        createConfig(designDomain, creativeIndustry, "Content Creator", 
            "content creator, tiktoker, youtuber, influencer", expertPromptService.getContentCreatorPrompt());
        createConfig(designDomain, creativeIndustry, "Social Media Creative", 
            "social media creative, visual, meme, trend", expertPromptService.getSocialMediaCreativePrompt());
        createConfig(designDomain, creativeIndustry, "Art Director (AD)", 
            "art director, ad, visual strategy, concept", expertPromptService.getArtDirectorPrompt());
        createConfig(designDomain, creativeIndustry, "Creative Director (CD)", 
            "creative director, cd, leadership, vision", expertPromptService.getCreativeDirectorPrompt());

        // PHOTOGRAPHY - VISUAL ARTS
        String photoIndustry = "Photography - Visual Arts";
        
        createConfig(designDomain, photoIndustry, "Photographer", 
            "photographer, nhiếp ảnh, chụp ảnh, camera", expertPromptService.getPhotographerPrompt());
        createConfig(designDomain, photoIndustry, "Photo Retoucher", 
            "photo retoucher, retoucher, retouching, skin", expertPromptService.getPhotoRetoucherPrompt());
        createConfig(designDomain, photoIndustry, "Photo Editor", 
            "photo editor, photo editing, lightroom, color", expertPromptService.getPhotoEditorPrompt());
        createConfig(designDomain, photoIndustry, "Concept Artist", 
            "concept artist, concept art, character design, environment", expertPromptService.getConceptArtistPrompt());
        createConfig(designDomain, photoIndustry, "Digital Painter", 
            "digital painter, digital painting, illustration", expertPromptService.getDigitalPainterPrompt());

        // EMERGING CREATIVE TECH
        String emergingTechIndustry = "Emerging Creative Tech";
        
        createConfig(designDomain, emergingTechIndustry, "AI Artist / AI Art Designer", 
            "ai artist, ai art, midjourney, stable diffusion", expertPromptService.getAiArtistPrompt());
        createConfig(designDomain, emergingTechIndustry, "Prompt Designer", 
            "prompt designer, prompting, prompt engineering", expertPromptService.getPromptDesignerPrompt());
        createConfig(designDomain, emergingTechIndustry, "AR/VR/XR Designer", 
            "ar, vr, xr, augmented reality, virtual reality", expertPromptService.getArVrXrDesignerPrompt());
        createConfig(designDomain, emergingTechIndustry, "Virtual Influencer Designer", 
            "virtual influencer, vtuber, 3d character", expertPromptService.getVirtualInfluencerDesignerPrompt());
        createConfig(designDomain, emergingTechIndustry, "Game Artist (2D/3D)", 
            "game artist, 2d artist, 3d artist, game asset", expertPromptService.getGameArtistPrompt());
        createConfig(designDomain, emergingTechIndustry, "Environment Artist", 
            "environment artist, world building, level art", expertPromptService.getEnvironmentArtistPrompt());
        createConfig(designDomain, emergingTechIndustry, "UI Artist (Game)", 
            "ui artist, game ui, hud, game interface", expertPromptService.getUiArtistGamePrompt());
        createConfig(designDomain, emergingTechIndustry, "Character Designer", 
            "character designer, character art, concept art", expertPromptService.getCharacterDesignerPrompt());

        // ENGINEERING & INDUSTRY
        String engineeringDomain = "Kỹ thuật – Công nghiệp – Sản xuất";
        String mechanicalIndustry = "Mechanical Engineering";
        
        createConfig(engineeringDomain, mechanicalIndustry, "Mechanical Engineer", 
            "mechanical engineer, cơ khí, design, cad", expertPromptService.getMechanicalEngineerPrompt());
        createConfig(engineeringDomain, mechanicalIndustry, "Mechatronics Engineer", 
            "mechatronics, automation, robotics, plc", expertPromptService.getMechatronicsEngineerPrompt());
        createConfig(engineeringDomain, mechanicalIndustry, "Maintenance Engineer", 
            "maintenance, bảo trì, reliability, cmms", expertPromptService.getMaintenanceEngineerPrompt());
        createConfig(engineeringDomain, mechanicalIndustry, "CNC Machinist", 
            "cnc, machinist, gia công chính xác, g-code", expertPromptService.getCncMachinistPrompt());
        createConfig(engineeringDomain, mechanicalIndustry, "Industrial Machinery Technician", 
            "machinery, technician, hydraulic, pneumatic", expertPromptService.getIndustrialMachineryTechnicianPrompt());
        createConfig(engineeringDomain, mechanicalIndustry, "Manufacturing Engineer", 
            "manufacturing, sản xuất, lean, process", expertPromptService.getManufacturingEngineerPrompt());
        createConfig(engineeringDomain, mechanicalIndustry, "Automotive Mechanical Technician", 
            "automotive, ô tô, sửa chữa, engine", expertPromptService.getAutomotiveMechanicalTechnicianPrompt());

        // ELECTRICAL & ELECTRONICS ENGINEERING
        String electricalIndustry = "Electrical – Electronics Engineering";
        
        createConfig(engineeringDomain, electricalIndustry, "Electrical Engineer", 
            "electrical engineer, power systems, high voltage", expertPromptService.getElectricalEngineerPrompt());
        createConfig(engineeringDomain, electricalIndustry, "Electronics Engineer", 
            "electronics engineer, embedded, iot, circuit", expertPromptService.getElectronicsEngineerPrompt());
        createConfig(engineeringDomain, electricalIndustry, "Electrical Maintenance Technician", 
            "electrical maintenance, technician, troubleshooting", expertPromptService.getElectricalMaintenanceTechnicianPrompt());
        createConfig(engineeringDomain, electricalIndustry, "Power Systems Engineer", 
            "power systems, grid, transmission, smart grid", expertPromptService.getPowerSystemsEngineerPrompt());
        createConfig(engineeringDomain, electricalIndustry, "Renewable Energy Engineer", 
            "renewable energy, solar, wind, năng lượng tái tạo", expertPromptService.getRenewableEnergyEngineerPrompt());
        createConfig(engineeringDomain, electricalIndustry, "PCB Engineer", 
            "pcb, circuit design, altium, layout", expertPromptService.getPcbEngineerPrompt());
        createConfig(engineeringDomain, electricalIndustry, "Semiconductor Process Technician", 
            "semiconductor, wafer, fabrication, cleanroom", expertPromptService.getSemiconductorProcessTechnicianPrompt());

        // AUTOMATION & ROBOTICS
        String automationIndustry = "Automation – Robotics – Control Systems";
        
        createConfig(engineeringDomain, automationIndustry, "Automation Engineer", 
            "automation engineer, control systems, plc, scada", expertPromptService.getAutomationEngineerPrompt());
        createConfig(engineeringDomain, automationIndustry, "PLC Engineer", 
            "plc engineer, ladder logic, siemens, rockwell", expertPromptService.getPlcEngineerPrompt());
        createConfig(engineeringDomain, automationIndustry, "Robotics Engineer", 
            "robotics engineer, abb, kuka, fanuc, robot", expertPromptService.getRoboticsEngineerPrompt());
        createConfig(engineeringDomain, automationIndustry, "Industrial IoT Engineer", 
            "industrial iot, iiot, mqtt, edge computing", expertPromptService.getIndustrialIoTEngineerPrompt());
        createConfig(engineeringDomain, automationIndustry, "SCADA Technician", 
            "scada, hmi, wincc, ignition", expertPromptService.getScadaTechnicianPrompt());
        createConfig(engineeringDomain, automationIndustry, "Instrumentation Engineer", 
            "instrumentation, calibration, sensors, control valves", expertPromptService.getInstrumentationEngineerPrompt());

        // CIVIL ENGINEERING & CONSTRUCTION
        String civilIndustry = "Civil Engineering – Construction";
        
        createConfig(engineeringDomain, civilIndustry, "Civil Engineer", 
            "civil engineer, xây dựng, hạ tầng, giao thông", expertPromptService.getCivilEngineerPrompt());
        createConfig(engineeringDomain, civilIndustry, "Structural Engineer", 
            "structural engineer, kết cấu, thép, bê tông", expertPromptService.getStructuralEngineerPrompt());
        createConfig(engineeringDomain, civilIndustry, "Construction Manager", 
            "construction manager, quản lý dự án, công trường", expertPromptService.getConstructionManagerPrompt());
        createConfig(engineeringDomain, civilIndustry, "Quantity Surveyor", 
            "quantity surveyor, dự toán, chi phí, đấu thầu", expertPromptService.getQuantitySurveyorPrompt());
        createConfig(engineeringDomain, civilIndustry, "Site Engineer", 
            "site engineer, giám sát thi công, công trường", expertPromptService.getSiteEngineerPrompt());
        createConfig(engineeringDomain, civilIndustry, "Architecture Technician", 
            "architecture technician, bản vẽ, autocad, revit", expertPromptService.getArchitectureTechnicianPrompt());
        createConfig(engineeringDomain, civilIndustry, "BIM Engineer", 
            "bim engineer, building information modeling, revit, navisworks", expertPromptService.getBimEngineerPrompt());

        // INDUSTRIAL & MANUFACTURING
        String industrialIndustry = "Industrial – Manufacturing – Supply Chain";
        
        createConfig(engineeringDomain, industrialIndustry, "Industrial Engineer", 
            "industrial engineer, process optimization, lean, ie", expertPromptService.getIndustrialEngineerPrompt());
        createConfig(engineeringDomain, industrialIndustry, "Production Planner", 
            "production planner, planning, scheduling, mrp", expertPromptService.getProductionPlannerPrompt());
        createConfig(engineeringDomain, industrialIndustry, "Quality Control (QC/QA)", 
            "quality control, qc, qa, inspection, iso", expertPromptService.getQualityControlPrompt());
        createConfig(engineeringDomain, industrialIndustry, "Lean Manufacturing Specialist", 
            "lean manufacturing, lean, toyota, kaizen", expertPromptService.getLeanManufacturingSpecialistPrompt());
        createConfig(engineeringDomain, industrialIndustry, "Supply Chain Engineer", 
            "supply chain, logistics, network design", expertPromptService.getSupplyChainEngineerPrompt());
        createConfig(engineeringDomain, industrialIndustry, "Warehouse & Operations Engineer", 
            "warehouse, operations, wms, logistics", expertPromptService.getWarehouseOperationsEngineerPrompt());

        // FIRE SAFETY & ENVIRONMENT
        String hseIndustry = "Fire Safety – Environment – Occupational Safety";
        
        createConfig(engineeringDomain, hseIndustry, "HSE Engineer (Health – Safety – Environment)", 
            "hse engineer, health safety environment, safety officer", expertPromptService.getHseEngineerPrompt());
        createConfig(engineeringDomain, hseIndustry, "Environmental Engineer", 
            "environmental engineer, môi trường, treatment, pollution", expertPromptService.getEnvironmentalEngineerPrompt());
        createConfig(engineeringDomain, hseIndustry, "Industrial Hygienist", 
            "industrial hygienist, occupational health, safety", expertPromptService.getIndustrialHygienistPrompt());
        createConfig(engineeringDomain, hseIndustry, "Fire Protection Engineer", 
            "fire protection engineer, pccc, fire safety", expertPromptService.getFireProtectionEngineerPrompt());

        // HEALTHCARE & MEDICAL
        String healthcareDomain = "Healthcare";
        String medicalIndustry = "Medical Practice";
        
        createConfig(healthcareDomain, medicalIndustry, "General Doctor", 
            "general doctor, bác sĩ đa khoa, family doctor", expertPromptService.getGeneralDoctorPrompt());
        createConfig(healthcareDomain, medicalIndustry, "Specialist Doctor", 
            "specialist doctor, bác sĩ chuyên khoa, specialist", expertPromptService.getSpecialistDoctorPrompt());
        createConfig(healthcareDomain, medicalIndustry, "Pediatrician", 
            "pediatrician, nhi khoa, children doctor", expertPromptService.getPediatricianPrompt());
        createConfig(healthcareDomain, medicalIndustry, "Cardiologist", 
            "cardiologist, tim mạch, heart doctor", expertPromptService.getCardiologistPrompt());
        createConfig(healthcareDomain, medicalIndustry, "Dermatologist", 
            "dermatologist, da liễu, skin doctor", expertPromptService.getDermatologistPrompt());
        createConfig(healthcareDomain, medicalIndustry, "Radiologist", 
            "radiologist, chẩn đoán hình ảnh, imaging", expertPromptService.getRadiologistPrompt());
        createConfig(healthcareDomain, medicalIndustry, "Surgeon", 
            "surgeon, phẫu thuật, surgery", expertPromptService.getSurgeonPrompt());

        // NURSING & CLINICAL CARE
        String nursingIndustry = "Nursing & Clinical Care";
        
        createConfig(healthcareDomain, nursingIndustry, "Registered Nurse", 
            "registered nurse, điều dưỡng, nurse", expertPromptService.getRegisteredNursePrompt());
        createConfig(healthcareDomain, nursingIndustry, "Assistant Nurse", 
            "assistant nurse, y tá, nursing assistant", expertPromptService.getAssistantNursePrompt());
        createConfig(healthcareDomain, nursingIndustry, "Clinical Care Specialist", 
            "clinical care specialist, chuyên gia chăm sóc", expertPromptService.getClinicalCareSpecialistPrompt());
        createConfig(healthcareDomain, nursingIndustry, "ICU Nurse", 
            "icu nurse, hồi sức cấp cứu, critical care", expertPromptService.getIcuNursePrompt());
        createConfig(healthcareDomain, nursingIndustry, "Emergency Care Nurse", 
            "emergency care nurse, cấp cứu, ed nurse", expertPromptService.getEmergencyCareNursePrompt());

        // MEDICAL TECHNOLOGY & LABORATORY
        String medTechIndustry = "Medical Technology – Xét nghiệm – Thiết bị";
        
        createConfig(healthcareDomain, medTechIndustry, "Medical Laboratory Technician", 
            "medical laboratory technician, ktv xét nghiệm, lab tech", expertPromptService.getMedicalLaboratoryTechnicianPrompt());
        createConfig(healthcareDomain, medTechIndustry, "Radiologic Technologist", 
            "radiologic technologist, ktv chẩn đoán hình ảnh, rad tech", expertPromptService.getRadiologicTechnologistPrompt());
        createConfig(healthcareDomain, medTechIndustry, "Ultrasound Technician", 
            "ultrasound technician, ktv siêu âm, sonographer", expertPromptService.getUltrasoundTechnicianPrompt());
        createConfig(healthcareDomain, medTechIndustry, "Pharmacy Technician", 
            "pharmacy technician, ktv dược, pharmacy tech", expertPromptService.getPharmacyTechnicianPrompt());
        createConfig(healthcareDomain, medTechIndustry, "Biomedical Engineer", 
            "biomedical engineer, ktv thiết bị y tế, biomedical tech", expertPromptService.getBiomedicalEngineerPrompt());

        // PHARMACY & PHARMACEUTICAL
        String pharmacyIndustry = "Pharmacy – Dược";
        
        createConfig(healthcareDomain, pharmacyIndustry, "Pharmacist", 
            "pharmacist, dược sĩ, pharmacist", expertPromptService.getPharmacistPrompt());
        createConfig(healthcareDomain, pharmacyIndustry, "Clinical Pharmacist", 
            "clinical pharmacist, dược sĩ lâm sàng, clinical pharmacy", expertPromptService.getClinicalPharmacistPrompt());
        createConfig(healthcareDomain, pharmacyIndustry, "Pharmacy Assistant", 
            "pharmacy assistant, trợ lý dược, pharmacy assistant", expertPromptService.getPharmacyAssistantPrompt());
        createConfig(healthcareDomain, pharmacyIndustry, "Pharmaceutical Sales Representative", 
            "pharmaceutical sales representative, kinh doanh dược, medical rep", expertPromptService.getPharmaceuticalSalesRepresentativePrompt());

        // MENTAL HEALTH & PSYCHOLOGY
        String mentalHealthIndustry = "Mental Health – Psychology (Sức khỏe tinh thần)";
        
        createConfig(healthcareDomain, mentalHealthIndustry, "Psychologist", 
            "psychologist, chuyên gia tâm lý, psychology", expertPromptService.getPsychologistPrompt());
        createConfig(healthcareDomain, mentalHealthIndustry, "Psychotherapist", 
            "psychotherapist, nhà trị liệu tâm lý, therapist", expertPromptService.getPsychotherapistPrompt());
        createConfig(healthcareDomain, mentalHealthIndustry, "School Counselor", 
            "school counselor, cố vấn học đường, guidance counselor", expertPromptService.getSchoolCounselorPrompt());
        createConfig(healthcareDomain, mentalHealthIndustry, "Mental Health Counselor", 
            "mental health counselor, cố vấn sức khỏe tinh thần, counselor", expertPromptService.getMentalHealthCounselorPrompt());
        createConfig(healthcareDomain, mentalHealthIndustry, "Behavioral Therapist", 
            "behavioral therapist, nhà trị liệu hành vi, aba therapist", expertPromptService.getBehavioralTherapistPrompt());

        // PUBLIC HEALTH & FITNESS & NUTRITION
        String publicHealthIndustry = "Public Health – Fitness – Nutrition (Sức khỏe cộng đồng – dinh dưỡng)";
        
        createConfig(healthcareDomain, publicHealthIndustry, "Public Health Specialist", 
            "public health specialist, chuyên gia sức khỏe cộng đồng, epidemiologist", expertPromptService.getPublicHealthSpecialistPrompt());
        createConfig(healthcareDomain, publicHealthIndustry, "Nutritionist", 
            "nutritionist, chuyên gia dinh dưỡng, dietitian", expertPromptService.getNutritionistPrompt());
        createConfig(healthcareDomain, publicHealthIndustry, "Fitness Coach", 
            "fitness coach, pt, personal trainer, huấn luyện viên thể hình", expertPromptService.getFitnessCoachPrompt());
        createConfig(healthcareDomain, publicHealthIndustry, "Health Education Specialist", 
            "health education specialist, chuyên gia giáo dục sức khỏe, health educator", expertPromptService.getHealthEducationSpecialistPrompt());
        createConfig(healthcareDomain, publicHealthIndustry, "Occupational Therapist", 
            "occupational therapist, trị liệu phục hồi chức năng, ot", expertPromptService.getOccupationalTherapistPrompt());
        createConfig(healthcareDomain, publicHealthIndustry, "Speech Therapist", 
            "speech therapist, trị liệu ngôn ngữ, speech pathologist", expertPromptService.getSpeechTherapistPrompt());

        // EDUCATION & TEACHING
        String educationDomain = "Education – Đào tạo – EdTech";
        String teachingIndustry = "Teaching (Giảng dạy – giáo viên)";
        
        createConfig(educationDomain, teachingIndustry, "Preschool Teacher", 
            "preschool teacher, giáo viên mầm non, kindergarten teacher", expertPromptService.getPreschoolTeacherPrompt());
        createConfig(educationDomain, teachingIndustry, "Primary Teacher", 
            "primary teacher, giáo viên tiểu học, elementary teacher", expertPromptService.getPrimaryTeacherPrompt());
        createConfig(educationDomain, teachingIndustry, "Secondary Teacher", 
            "secondary teacher, giáo viên thcs thpt, high school teacher", expertPromptService.getSecondaryTeacherPrompt());
        createConfig(educationDomain, teachingIndustry, "University Lecturer", 
            "university lecturer, giảng viên đại học, professor", expertPromptService.getUniversityLecturerPrompt());
        createConfig(educationDomain, teachingIndustry, "ESL Teacher", 
            "esl teacher, giáo viên tiếng anh, english teacher", expertPromptService.getESLTeacherPrompt());
        createConfig(educationDomain, teachingIndustry, "STEM Teacher", 
            "stem teacher, giáo viên stem, science teacher", expertPromptService.getSTEMTeacherPrompt());
        createConfig(educationDomain, teachingIndustry, "Tutor", 
            "tutor, gia sư, private teacher", expertPromptService.getTutorPrompt());

        // EDUCATIONAL SUPPORT
        String educationalSupportIndustry = "Educational Support (Hỗ trợ giáo dục)";
        
        createConfig(educationDomain, educationalSupportIndustry, "Teaching Assistant", 
            "teaching assistant, trợ giảng, ta", expertPromptService.getTeachingAssistantPrompt());
        createConfig(educationDomain, educationalSupportIndustry, "Academic Advisor", 
            "academic advisor, cố vấn học thuật, academic counselor", expertPromptService.getAcademicAdvisorPrompt());
        createConfig(educationDomain, educationalSupportIndustry, "Student Counselor", 
            "student counselor, cố vấn học sinh, school counselor", expertPromptService.getStudentCounselorPrompt());
        createConfig(educationDomain, educationalSupportIndustry, "School Administration Officer", 
            "school administration officer, quản lý nhà trường, school admin", expertPromptService.getSchoolAdministrationOfficerPrompt());
        createConfig(educationDomain, educationalSupportIndustry, "Curriculum Developer", 
            "curriculum developer, chuyên viên phát triển chương trình học, curriculum specialist", expertPromptService.getCurriculumDeveloperPrompt());

        // TRAINING & COACHING
        String trainingCoachingIndustry = "Training – Coaching (Đào tạo kỹ năng & doanh nghiệp)";
        
        createConfig(educationDomain, trainingCoachingIndustry, "Corporate Trainer", 
            "corporate trainer, đào tạo doanh nghiệp, training specialist", expertPromptService.getCorporateTrainerPrompt());
        createConfig(educationDomain, trainingCoachingIndustry, "Learning & Development Specialist", 
            "learning & development specialist, l&d specialist, chuyên gia phát triển học tập", expertPromptService.getLearningDevelopmentSpecialistPrompt());
        createConfig(educationDomain, trainingCoachingIndustry, "Soft Skills Trainer", 
            "soft skills trainer, đào tạo kỹ năng mềm, communication trainer", expertPromptService.getSoftSkillsTrainerPrompt());
        createConfig(educationDomain, trainingCoachingIndustry, "Career Coach", 
            "career coach, mentor, cố vấn sự nghiệp, career consultant", expertPromptService.getCareerCoachPrompt());
        createConfig(educationDomain, trainingCoachingIndustry, "Public Speaking Coach", 
            "public speaking coach, huấn luyện viên nói trước đám đông, presentation coach", expertPromptService.getPublicSpeakingCoachPrompt());
        createConfig(educationDomain, trainingCoachingIndustry, "Leadership Coach", 
            "leadership coach, huấn luyện viên lãnh đạo, executive coach", expertPromptService.getLeadershipCoachPrompt());

        // SPECIAL EDUCATION
        String specialEducationIndustry = "Special Education (Giáo dục đặc biệt)";
        
        createConfig(educationDomain, specialEducationIndustry, "Special Education Teacher", 
            "special education teacher, giáo viên giáo dục đặc biệt, special needs teacher", expertPromptService.getSpecialEducationTeacherPrompt());
        createConfig(educationDomain, specialEducationIndustry, "Speech Therapist", 
            "speech therapist, trị liệu ngôn ngữ, speech pathologist", expertPromptService.getSpeechTherapistPrompt());
        createConfig(educationDomain, specialEducationIndustry, "Occupational Therapy Teacher", 
            "occupational therapy teacher, trị liệu chức năng, occupational therapist", expertPromptService.getOccupationalTherapyTeacherPrompt());
        createConfig(educationDomain, specialEducationIndustry, "Learning Disabilities Specialist", 
            "learning disabilities specialist, chuyên gia rối loạn học tập, educational psychologist", expertPromptService.getLearningDisabilitiesSpecialistPrompt());

        // EDTECH & EDUCATIONAL INNOVATION
        String edTechIndustry = "EdTech – Đổi mới giáo dục";
        
        createConfig(educationDomain, edTechIndustry, "EdTech Product Specialist", 
            "edtech product specialist, chuyên gia sản phẩm edtech, product manager education", expertPromptService.getEdTechProductSpecialistPrompt());
        createConfig(educationDomain, edTechIndustry, "Instructional Designer", 
            "instructional designer, nhà thiết kế giảng dạy, learning designer", expertPromptService.getInstructionalDesignerPrompt());
        createConfig(educationDomain, edTechIndustry, "E-learning Content Creator", 
            "e-learning content creator, tạo nội dung học tập trực tuyến, content developer", expertPromptService.getElearningContentCreatorPrompt());
        createConfig(educationDomain, edTechIndustry, "Academic Content Writer", 
            "academic content writer, viết nội dung học thuật, educational writer", expertPromptService.getAcademicContentWriterPrompt());
        createConfig(educationDomain, edTechIndustry, "Online Course Creator", 
            "online course creator, tạo khóa học online, course developer", expertPromptService.getOnlineCourseCreatorPrompt());

        // LEGAL & PUBLIC ADMINISTRATION
        String legalDomain = "Legal & Public Administration";
        String legalPracticeIndustry = "Legal Practice (Luật – pháp lý)";
        
        // Legal & Public Administration - Legal Practice
        createConfig(legalDomain, legalPracticeIndustry, "Lawyer", "lawyer, luật sư, attorney", expertPromptService.getLawyerPrompt());
        createConfig(legalDomain, legalPracticeIndustry, "Legal Consultant", "legal consultant, tư vấn pháp lý, legal advisor", expertPromptService.getLegalConsultantPrompt());
        createConfig(legalDomain, legalPracticeIndustry, "Legal Executive", "legal executive, chuyên viên pháp lý, legal officer", expertPromptService.getLegalExecutivePrompt());
        createConfig(legalDomain, legalPracticeIndustry, "Corporate Legal Specialist", "corporate legal specialist, pháp chế doanh nghiệp, in-house counsel", expertPromptService.getCorporateLegalSpecialistPrompt());
        createConfig(legalDomain, legalPracticeIndustry, "Intellectual Property Specialist", "intellectual property specialist, sở hữu trí tuệ, IP specialist", expertPromptService.getIntellectualPropertySpecialistPrompt());
        createConfig(legalDomain, legalPracticeIndustry, "Contract Specialist", "contract specialist, chuyên viên hợp đồng, contract manager", expertPromptService.getContractSpecialistPrompt());
        createConfig(legalDomain, legalPracticeIndustry, "Compliance Officer", "compliance officer, tuân thủ pháp luật, compliance manager", expertPromptService.getComplianceOfficerPrompt());
        createConfig(legalDomain, legalPracticeIndustry, "Notary Officer", "notary officer, công chứng viên, notary public", expertPromptService.getNotaryOfficerPrompt());
        createConfig(legalDomain, legalPracticeIndustry, "Legal Assistant/Paralegal", "legal assistant, paralegal, trợ lý pháp lý", expertPromptService.getLegalAssistantPrompt());

        // Legal & Public Administration - Judiciary & Court Services
        String judiciaryIndustry = "Judiciary & Court Services (Tư pháp – tòa án)";
        createConfig(legalDomain, judiciaryIndustry, "Judge Assistant", "judge assistant, thư ký tòa án, court secretary", expertPromptService.getJudgeAssistantPrompt());
        createConfig(legalDomain, judiciaryIndustry, "Court Clerk", "court clerk, thư ký tòa, court administrator", expertPromptService.getCourtClerkPrompt());
        createConfig(legalDomain, judiciaryIndustry, "Prosecutor Assistant", "prosecutor assistant, trợ lý kiểm sát viên, prosecution assistant", expertPromptService.getProsecutorAssistantPrompt());
        createConfig(legalDomain, judiciaryIndustry, "Mediator/Arbitrator", "mediator, arbitrator, trọng tài viên, hòa giải, mediator/arbitrator", expertPromptService.getMediatorArbitratorPrompt());
        createConfig(legalDomain, judiciaryIndustry, "Enforcement Officer", "enforcement officer, thi hành án, bailiff, enforcement agent", expertPromptService.getEnforcementOfficerPrompt());

        // Legal & Public Administration - Public Administration
        String publicAdminIndustry = "Public Administration (Hành chính công)";
        createConfig(legalDomain, publicAdminIndustry, "Public Administration Officer", "public administration officer, cán bộ hành chính, administrative officer", expertPromptService.getPublicAdministrationOfficerPrompt());
        createConfig(legalDomain, publicAdminIndustry, "Government Policy Officer", "government policy officer, cán bộ chính sách, policy analyst", expertPromptService.getGovernmentPolicyOfficerPrompt());
        createConfig(legalDomain, publicAdminIndustry, "Administrative Specialist", "administrative specialist, hành chính văn phòng, office specialist", expertPromptService.getAdministrativeSpecialistPrompt());
        createConfig(legalDomain, publicAdminIndustry, "Planning & Statistics Officer", "planning statistics officer, cán bộ quy hoạch thống kê, planning officer", expertPromptService.getPlanningStatisticsOfficerPrompt());
        createConfig(legalDomain, publicAdminIndustry, "Public Finance Officer", "public finance officer, cán bộ tài chính công, finance officer", expertPromptService.getPublicFinanceOfficerPrompt());
        createConfig(legalDomain, publicAdminIndustry, "Civil Servant General Track", "civil servant, công chức, public servant", expertPromptService.getCivilServantGeneralTrackPrompt());
        createConfig(legalDomain, publicAdminIndustry, "Community Development Officer", "community development officer, cán bộ phát triển cộng đồng, community officer", expertPromptService.getCommunityDevelopmentOfficerPrompt());

        // Legal & Public Administration - Security – Public Service
        String securityIndustry = "Security – Public Service (An ninh – công vụ)";
        createConfig(legalDomain, securityIndustry, "Police Officer", "police officer, công an, police, law enforcement", expertPromptService.getPoliceOfficerPrompt());
        createConfig(legalDomain, securityIndustry, "Immigration Officer", "immigration officer, xuất nhập cảnh, border control, visa", expertPromptService.getImmigrationOfficerPrompt());
        createConfig(legalDomain, securityIndustry, "Customs Officer", "customs officer, hải quan, customs, trade", expertPromptService.getCustomsOfficerPrompt());
        createConfig(legalDomain, securityIndustry, "Fire Service Officer", "fire service officer, cứu hỏa, firefighter, fire safety", expertPromptService.getFireServiceOfficerPrompt());
        createConfig(legalDomain, securityIndustry, "Social Security Officer", "social security officer, bảo hiểm xã hội, BHXH, BHYT", expertPromptService.getSocialSecurityOfficerPrompt());
        createConfig(legalDomain, securityIndustry, "Public Health Administration Officer", "public health administration officer, y tế công cộng, public health", expertPromptService.getPublicHealthAdministrationOfficerPrompt());
        createConfig(legalDomain, securityIndustry, "Citizen Service Specialist", "citizen service specialist, phục vụ công dân, public service", expertPromptService.getCitizenServiceSpecialistPrompt());
        createConfig(legalDomain, securityIndustry, "Public Safety Specialist", "public safety specialist, an toàn công cộng, safety specialist", expertPromptService.getPublicSafetySpecialistPrompt());
        createConfig(legalDomain, securityIndustry, "Inspector", "inspector, thanh tra nhà nước, state inspector", expertPromptService.getInspectorPrompt());

        // Logistics – Trade - Logistics Operations
        String logisticsDomain = "Logistics – Chuỗi cung ứng – Xuất nhập khẩu";
        String logisticsOpsIndustry = "Logistics Operations (Vận hành Logistics)";
        createConfig(logisticsDomain, logisticsOpsIndustry, "Logistics Coordinator", "logistics coordinator, điều phối logistics, logistics coordinator", expertPromptService.getLogisticsCoordinatorPrompt());
        createConfig(logisticsDomain, logisticsOpsIndustry, "Warehouse Staff", "warehouse staff, nhân viên kho, warehouse worker", expertPromptService.getWarehouseStaffPrompt());
        createConfig(logisticsDomain, logisticsOpsIndustry, "Warehouse Manager", "warehouse manager, quản lý kho, warehouse supervisor", expertPromptService.getWarehouseManagerPrompt());
        createConfig(logisticsDomain, logisticsOpsIndustry, "Inventory Controller", "inventory controller, kiểm soát tồn kho, inventory specialist", expertPromptService.getInventoryControllerPrompt());
        createConfig(logisticsDomain, logisticsOpsIndustry, "Fulfillment Specialist", "fulfillment specialist, chuyên viên hoàn thành đơn, order fulfillment", expertPromptService.getFulfillmentSpecialistPrompt());
        createConfig(logisticsDomain, logisticsOpsIndustry, "Supply Chain Planner", "supply chain planner, nhà hoạch định chuỗi cung ứng, supply planning", expertPromptService.getSupplyChainPlannerPrompt());
        createConfig(logisticsDomain, logisticsOpsIndustry, "Transport Planner", "transport planner, nhà hoạch định vận tải, transportation planner", expertPromptService.getTransportPlannerPrompt());
        createConfig(logisticsDomain, logisticsOpsIndustry, "Fleet Manager", "fleet manager, quản lý đội xe, fleet supervisor", expertPromptService.getFleetManagerPrompt());
        createConfig(logisticsDomain, logisticsOpsIndustry, "Distribution Center Operator", "distribution center operator, vận hành trung tâm phân phối, distribution operator", expertPromptService.getDistributionCenterOperatorPrompt());

        // Logistics – Trade - Freight & Shipping
        String freightShippingIndustry = "Freight & Shipping (Giao nhận – vận tải quốc tế)";
        createConfig(logisticsDomain, freightShippingIndustry, "Freight Forwarder", "freight forwarder, giao nhận vận tải quốc tế, freight forwarding", expertPromptService.getFreightForwarderPrompt());
        createConfig(logisticsDomain, freightShippingIndustry, "Ocean Freight Specialist", "ocean freight specialist, đường biển, sea freight specialist", expertPromptService.getOceanFreightSpecialistPrompt());
        createConfig(logisticsDomain, freightShippingIndustry, "Air Freight Specialist", "air freight specialist, đường hàng không, air cargo specialist", expertPromptService.getAirFreightSpecialistPrompt());
        createConfig(logisticsDomain, freightShippingIndustry, "Road Freight Coordinator", "road freight coordinator, đường bộ, road transport coordinator", expertPromptService.getRoadFreightCoordinatorPrompt());
        createConfig(logisticsDomain, freightShippingIndustry, "Customs Clearance Staff", "customs clearance staff, thông quan, customs broker", expertPromptService.getCustomsClearanceStaffPrompt());
        createConfig(logisticsDomain, freightShippingIndustry, "Import – Export Executive", "import export executive, xnk, import export specialist", expertPromptService.getImportExportExecutivePrompt());
        createConfig(logisticsDomain, freightShippingIndustry, "Shipping Documentation Officer", "shipping documentation officer, tài liệu vận tải, documentation specialist", expertPromptService.getShippingDocumentationOfficerPrompt());
        createConfig(logisticsDomain, freightShippingIndustry, "Vessel Planner", "vessel planner, hãng tàu, shipping planner", expertPromptService.getVesselPlannerPrompt());

        // Logistics – Trade - Supply Chain Management
        String supplyChainIndustry = "Supply Chain Management (Chuỗi cung ứng)";
        createConfig(logisticsDomain, supplyChainIndustry, "Supply Chain Analyst", "supply chain analyst, phân tích chuỗi cung ứng, supply chain analysis", expertPromptService.getSupplyChainAnalystPrompt());
        createConfig(logisticsDomain, supplyChainIndustry, "Supply Chain Manager", "supply chain manager, quản lý chuỗi cung ứng, supply chain management", expertPromptService.getSupplyChainManagerPrompt());
        createConfig(logisticsDomain, supplyChainIndustry, "Demand Planner", "demand planner, hoạch định nhu cầu, demand planning", expertPromptService.getDemandPlannerPrompt());
        createConfig(logisticsDomain, supplyChainIndustry, "Procurement Officer", "procurement officer, mua hàng, purchasing officer", expertPromptService.getProcurementOfficerPrompt());
        createConfig(logisticsDomain, supplyChainIndustry, "Vendor Management Specialist", "vendor management specialist, quản lý nhà cung cấp, vendor specialist", expertPromptService.getVendorManagementSpecialistPrompt());
        createConfig(logisticsDomain, supplyChainIndustry, "Order Management Specialist", "order management specialist, quản lý đơn hàng, order specialist", expertPromptService.getOrderManagementSpecialistPrompt());
        createConfig(logisticsDomain, supplyChainIndustry, "Production Planner", "production planner, kế hoạch sản xuất, production planning", expertPromptService.getLogisticsProductionPlannerPrompt());

        // Logistics – Trade - International Business – Trade
        String internationalTradeIndustry = "International Business – Trade (Kinh doanh quốc tế)";
        createConfig(logisticsDomain, internationalTradeIndustry, "International Sales Executive", "international sales executive, bán hàng quốc tế, export sales", expertPromptService.getInternationalSalesExecutivePrompt());
        createConfig(logisticsDomain, internationalTradeIndustry, "Trade Compliance Specialist", "trade compliance specialist, tuân thủ thương mại, compliance specialist", expertPromptService.getTradeComplianceSpecialistPrompt());
        createConfig(logisticsDomain, internationalTradeIndustry, "Global Sourcing Specialist", "global sourcing specialist, mua hàng toàn cầu, sourcing specialist", expertPromptService.getGlobalSourcingSpecialistPrompt());
        createConfig(logisticsDomain, internationalTradeIndustry, "International Business Development", "international business development, phát triển kinh doanh quốc tế, business development", expertPromptService.getInternationalBusinessDevelopmentPrompt());
        createConfig(logisticsDomain, internationalTradeIndustry, "Foreign Trade Analyst", "foreign trade analyst, phân tích thương mại nước ngoài, trade analyst", expertPromptService.getForeignTradeAnalystPrompt());
        createConfig(logisticsDomain, internationalTradeIndustry, "Commercial Invoice Specialist", "commercial invoice specialist, hóa đơn thương mại, invoice specialist", expertPromptService.getCommercialInvoiceSpecialistPrompt());
        createConfig(logisticsDomain, internationalTradeIndustry, "E-commerce Fulfillment Specialist", "ecommerce fulfillment specialist, hoàn thành đơn TMĐT, fulfillment specialist", expertPromptService.getEcommerceFulfillmentSpecialistPrompt());

        // Arts & Entertainment - Performing Arts
        String artsDomain = "Arts & Entertainment (Nghệ thuật – Biểu diễn – Giải trí)";
        String performingArtsIndustry = "Performing Arts (Biểu diễn nghệ thuật)";
        createConfig(artsDomain, performingArtsIndustry, "Singer", "singer, ca sĩ, vocalist, thanh nhạc", expertPromptService.getSingerPrompt());
        createConfig(artsDomain, performingArtsIndustry, "Dancer", "dancer, vũ công, dancer, múa", expertPromptService.getDancerPrompt());
        createConfig(artsDomain, performingArtsIndustry, "Actor / Actress", "actor, actress, diễn viên, diễn xuất", expertPromptService.getActorPrompt());
        createConfig(artsDomain, performingArtsIndustry, "Stage Performer", "stage performer, người biểu diễn sân khấu, live performer", expertPromptService.getStagePerformerPrompt());
        createConfig(artsDomain, performingArtsIndustry, "Theatre Actor", "theatre actor, diễn viên kịch, stage actor", expertPromptService.getTheatreActorPrompt());
        createConfig(artsDomain, performingArtsIndustry, "Musical Performer", "musical performer, người biểu diễn âm nhạc, music performer", expertPromptService.getMusicalPerformerPrompt());
        createConfig(artsDomain, performingArtsIndustry, "Stunt Performer", "stunt performer, diễn viên đóng thế, action performer", expertPromptService.getStuntPerformerPrompt());

        // Arts & Entertainment - Audio – Music – Voice
        String audioMusicIndustry = "Audio – Music – Voice (Âm nhạc – âm thanh)";
        createConfig(artsDomain, audioMusicIndustry, "Music Producer", "music producer, nhà sản xuất âm nhạc, music production", expertPromptService.getMusicProducerPrompt());
        createConfig(artsDomain, audioMusicIndustry, "Music Composer", "music composer, nhà sáng tác âm nhạc, music composition", expertPromptService.getMusicComposerPrompt());
        createConfig(artsDomain, audioMusicIndustry, "Sound Designer", "sound designer, nhà thiết kế âm thanh, sound design", expertPromptService.getSoundDesignerPrompt());
        createConfig(artsDomain, audioMusicIndustry, "Audio Engineer", "audio engineer, kỹ sư âm thanh, sound engineering", expertPromptService.getAudioEngineerPrompt());
        createConfig(artsDomain, audioMusicIndustry, "Voice Actor", "voice actor, diễn viên lồng tiếng, voice acting", expertPromptService.getVoiceActorPrompt());
        createConfig(artsDomain, audioMusicIndustry, "DJ / Electronic Music Artist", "dj, electronic music artist, nghệ sĩ điện tử, EDM artist", expertPromptService.getDjElectronicMusicArtistPrompt());

        // Arts & Entertainment - Entertainment – Digital Creator
        String digitalCreatorIndustry = "Entertainment – Digital Creator";
        createConfig(artsDomain, digitalCreatorIndustry, "Streamer", "streamer, người phát trực tiếp, livestreamer, gaming streamer", expertPromptService.getStreamerPrompt());
        createConfig(artsDomain, digitalCreatorIndustry, "KOL / KOC / Influencer", "kol, koc, influencer, người ảnh hưởng, content creator", expertPromptService.getKolKocInfluencerPrompt());
        createConfig(artsDomain, digitalCreatorIndustry, "Social Media Entertainer", "social media entertainer, người giải trí mạng xã hội, tiktok creator", expertPromptService.getSocialMediaEntertainerPrompt());
        createConfig(artsDomain, digitalCreatorIndustry, "Cosplayer", "cosplayer, người hóa trang, costume player, cosplay artist", expertPromptService.getCosplayerPrompt());
        createConfig(artsDomain, digitalCreatorIndustry, "Virtual Idol Performer", "virtual idol, idol ảo, vtuber, virtual performer", expertPromptService.getVirtualIdolPerformerPrompt());
        createConfig(artsDomain, digitalCreatorIndustry, "Host / MC", "host, mc, người dẫn chương trình, event host, tv host", expertPromptService.getHostMCPrompt());
        createConfig(artsDomain, digitalCreatorIndustry, "Podcaster", "podcaster, người sản xuất podcast, podcast creator, audio storyteller", expertPromptService.getPodcasterPrompt());

        // Arts & Entertainment - Fashion – Modeling – Beauty
        String fashionBeautyIndustry = "Fashion – Modeling – Beauty";
        createConfig(artsDomain, fashionBeautyIndustry, "Fashion Model", "fashion model, người mẫu thời trang, model, fashion modeling", expertPromptService.getFashionModelPrompt());
        createConfig(artsDomain, fashionBeautyIndustry, "Runway Model", "runway model, người mẫu diễn viên, catwalk model, fashion show model", expertPromptService.getRunwayModelPrompt());
        createConfig(artsDomain, fashionBeautyIndustry, "Commercial Model", "commercial model, người mẫu quảng cáo, advertising model, product model", expertPromptService.getCommercialModelPrompt());
        createConfig(artsDomain, fashionBeautyIndustry, "Fashion Stylist", "fashion stylist, stylist thời trang, personal stylist, wardrobe consultant", expertPromptService.getFashionStylistPrompt());
        createConfig(artsDomain, fashionBeautyIndustry, "Makeup Artist", "makeup artist, chuyên gia trang điểm, MUA, beauty artist", expertPromptService.getMakeupArtistPrompt());
        createConfig(artsDomain, fashionBeautyIndustry, "Costume Designer", "costume designer, nhà thiết kế trang phục, costume maker, wardrobe designer", expertPromptService.getCostumeDesignerPrompt());
        createConfig(artsDomain, fashionBeautyIndustry, "Image Consultant", "image consultant, chuyên gia hình ảnh, personal image consultant, branding expert", expertPromptService.getImageConsultantPrompt());

        // Arts & Entertainment - Film – Stage – Production
        String filmProductionIndustry = "Film – Stage – Production (Hậu kỳ & sản xuất)";
        createConfig(artsDomain, filmProductionIndustry, "Film Director", "film director, đạo diễn phim, movie director, filmmaker", expertPromptService.getFilmDirectorPrompt());
        createConfig(artsDomain, filmProductionIndustry, "Assistant Director", "assistant director, trợ lý đạo diễn, AD, film AD", expertPromptService.getAssistantDirectorPrompt());
        createConfig(artsDomain, filmProductionIndustry, "Producer", "producer, nhà sản xuất, film producer, movie producer", expertPromptService.getProducerPrompt());
        createConfig(artsDomain, filmProductionIndustry, "Screenwriter", "screenwriter, biên kịch, scriptwriter, script writer", expertPromptService.getScreenwriterPrompt());
        createConfig(artsDomain, filmProductionIndustry, "Choreographer", "choreographer, biên đạo múa, dance choreographer, movement director", expertPromptService.getChoreographerPrompt());
        createConfig(artsDomain, filmProductionIndustry, "Stage Manager", "stage manager, quản lý sân khấu, theater manager, production manager", expertPromptService.getStageManagerPrompt());
        createConfig(artsDomain, filmProductionIndustry, "Casting Director", "casting director, giám đốc tuyển chọn, casting director, talent casting", expertPromptService.getCastingDirectorPrompt());
        createConfig(artsDomain, filmProductionIndustry, "Production Assistant", "production assistant, trợ lý sản xuất, PA, film crew", expertPromptService.getProductionAssistantPrompt());

        // Service & Hospitality - Food & Beverage
        String serviceHospitalityDomain = "Service & Hospitality";
        String foodBeverageIndustry = "Food & Beverage (Nhà hàng – F&B)";
        createConfig(serviceHospitalityDomain, foodBeverageIndustry, "Waiter/Waitress", "waiter, waitress, nhân viên phục vụ bàn, server, restaurant staff", expertPromptService.getWaiterWaitressPrompt());
        createConfig(serviceHospitalityDomain, foodBeverageIndustry, "Host / Reception F&B", "host, reception f&b, lễ tân nhà hàng, hostess, restaurant host", expertPromptService.getHostReceptionFBPrompt());
        createConfig(serviceHospitalityDomain, foodBeverageIndustry, "Barista", "barista, pha chế, coffee maker, café barista, specialist coffee", expertPromptService.getBaristaPrompt());
        createConfig(serviceHospitalityDomain, foodBeverageIndustry, "Bartender", "bartender, pha chế rượu, mixologist, barman, cocktail maker", expertPromptService.getBartenderPrompt());
        createConfig(serviceHospitalityDomain, foodBeverageIndustry, "Cashier F&B", "cashier f&b, thu ngân nhà hàng, restaurant cashier, payment clerk", expertPromptService.getCashierFBPrompt());
        createConfig(serviceHospitalityDomain, foodBeverageIndustry, "F&B Supervisor", "f&b supervisor, giám sát nhà hàng, restaurant supervisor, floor manager", expertPromptService.getFBSupervisorPrompt());
        createConfig(serviceHospitalityDomain, foodBeverageIndustry, "Restaurant Manager", "restaurant manager, quản lý nhà hàng, f&b manager, dining manager", expertPromptService.getRestaurantManagerPrompt());
        createConfig(serviceHospitalityDomain, foodBeverageIndustry, "Banquet Server", "banquet server, phục vụ tiệc, event server, banquet staff", expertPromptService.getBanquetServerPrompt());
        createConfig(serviceHospitalityDomain, foodBeverageIndustry, "Catering Coordinator", "catering coordinator, điều phối catering, event catering, food service coordinator", expertPromptService.getCateringCoordinatorPrompt());

        // Service & Hospitality - Hotel & Hospitality
        String hotelHospitalityIndustry = "Hotel & Hospitality (Khách sạn – lưu trú)";
        createConfig(serviceHospitalityDomain, hotelHospitalityIndustry, "Hotel Receptionist", "hotel receptionist, lễ tân khách sạn, front desk agent, receptionist", expertPromptService.getHotelReceptionistPrompt());
        createConfig(serviceHospitalityDomain, hotelHospitalityIndustry, "Concierge", "concierge, hỗ trợ khách lưu trú, guest services, hotel concierge", expertPromptService.getConciergePrompt());
        createConfig(serviceHospitalityDomain, hotelHospitalityIndustry, "Bellman", "bellman, nhân viên khuân hành lý, porter, luggage attendant", expertPromptService.getBellmanPrompt());
        createConfig(serviceHospitalityDomain, hotelHospitalityIndustry, "Housekeeping", "housekeeping, buồng phòng, room attendant, hotel cleaning", expertPromptService.getHousekeepingPrompt());
        createConfig(serviceHospitalityDomain, hotelHospitalityIndustry, "Housekeeping Supervisor", "housekeeping supervisor, giám sát buồng phòng, executive housekeeper", expertPromptService.getHousekeepingSupervisorPrompt());
        createConfig(serviceHospitalityDomain, hotelHospitalityIndustry, "Guest Relations Officer", "guest relations officer, chăm sóc khách lưu trú, guest service, customer care", expertPromptService.getGuestRelationsOfficerPrompt());
        createConfig(serviceHospitalityDomain, hotelHospitalityIndustry, "Front Office Manager", "front office manager, quản lý lễ tân, front desk manager, reception manager", expertPromptService.getFrontOfficeManagerPrompt());
        createConfig(serviceHospitalityDomain, hotelHospitalityIndustry, "Hotel General Manager", "hotel general manager, quản lý khách sạn, hotel manager, gm hotel", expertPromptService.getHotelGeneralManagerPrompt());
        createConfig(serviceHospitalityDomain, hotelHospitalityIndustry, "Resort Staff", "resort staff, nhân viên resort, resort attendant, hospitality staff", expertPromptService.getResortStaffPrompt());
        createConfig(serviceHospitalityDomain, hotelHospitalityIndustry, "Tour Desk Officer", "tour desk officer, chuyên viên tour, tour coordinator, travel desk", expertPromptService.getTourDeskOfficerPrompt());

        // Service & Hospitality - Travel – Tourism – Event
        String travelTourismEventIndustry = "Travel – Tourism – Event (Du lịch – Sự kiện)";
        createConfig(serviceHospitalityDomain, travelTourismEventIndustry, "Tour Guide", "tour guide, hướng dẫn viên du lịch, tourist guide, travel guide", expertPromptService.getTourGuidePrompt());
        createConfig(serviceHospitalityDomain, travelTourismEventIndustry, "Travel Consultant", "travel consultant, tư vấn du lịch, travel advisor, travel agent", expertPromptService.getTravelConsultantPrompt());
        createConfig(serviceHospitalityDomain, travelTourismEventIndustry, "Event Assistant", "event assistant, trợ lý sự kiện, event coordinator assistant", expertPromptService.getEventAssistantPrompt());
        createConfig(serviceHospitalityDomain, travelTourismEventIndustry, "Event Coordinator", "event coordinator, điều phối sự kiện, event planner, event organizer", expertPromptService.getEventCoordinatorPrompt());
        createConfig(serviceHospitalityDomain, travelTourismEventIndustry, "Event Manager", "event manager, quản lý sự kiện, event director, event production", expertPromptService.getEventManagerPrompt());
        createConfig(serviceHospitalityDomain, travelTourismEventIndustry, "Ticketing Officer", "ticketing officer, nhân viên bán vé, ticket agent, box office", expertPromptService.getTicketingOfficerPrompt());
        createConfig(serviceHospitalityDomain, travelTourismEventIndustry, "Cruise Service Staff", "cruise service staff, nhân viên du thuyền, cruise staff, ship crew", expertPromptService.getCruiseServiceStaffPrompt());

        // Service & Hospitality - Beauty – Spa – Wellness
        String beautySpaWellnessIndustry = "Beauty – Spa – Wellness (Làm đẹp – chăm sóc)";
        createConfig(serviceHospitalityDomain, beautySpaWellnessIndustry, "Spa Therapist", "spa therapist, chuyên viên spa, massage therapist, spa specialist", expertPromptService.getSpaTherapistPrompt());
        createConfig(serviceHospitalityDomain, beautySpaWellnessIndustry, "Nail Technician", "nail technician, kỹ thuật viên làm móng, nail artist, manicurist", expertPromptService.getNailTechnicianPrompt());
        createConfig(serviceHospitalityDomain, beautySpaWellnessIndustry, "Hair Stylist", "hair stylist, tạo mẫu tóc, hairdresser, hair designer", expertPromptService.getHairStylistPrompt());
        createConfig(serviceHospitalityDomain, beautySpaWellnessIndustry, "Masseuse / Massage Therapist", "masseuse, massage therapist, chuyên viên massage, massage specialist", expertPromptService.getMasseuseMassageTherapistPrompt());
        createConfig(serviceHospitalityDomain, beautySpaWellnessIndustry, "Beauty Consultant", "beauty consultant, tư vấn viên làm đẹp, beauty advisor, makeup consultant", expertPromptService.getBeautyConsultantPrompt());
        createConfig(serviceHospitalityDomain, beautySpaWellnessIndustry, "Skincare Specialist", "skincare specialist, chuyên viên chăm sóc da, esthetician, skincare therapist", expertPromptService.getSkincareSpecialistPrompt());

        // Service & Hospitality - Customer Service – Call Center
        String customerServiceCallCenterIndustry = "Customer Service – Call Center (CSKH – Tổng đài)";
        createConfig(serviceHospitalityDomain, customerServiceCallCenterIndustry, "Customer Service Representative", "customer service representative, cskh, chăm sóc khách hàng, customer service", expertPromptService.getCustomerServiceRepresentativePrompt());
        createConfig(serviceHospitalityDomain, customerServiceCallCenterIndustry, "Call Center Agent", "call center agent, điện thoại viên, telemarketer, phone operator", expertPromptService.getCallCenterAgentPrompt());
        createConfig(serviceHospitalityDomain, customerServiceCallCenterIndustry, "Live Chat Support", "live chat support, chat support, hỗ trợ trực tuyến, online support", expertPromptService.getLiveChatSupportPrompt());
        createConfig(serviceHospitalityDomain, customerServiceCallCenterIndustry, "Service Quality Officer", "service quality officer, chất lượng dịch vụ, quality assurance, QA specialist", expertPromptService.getServiceQualityOfficerPrompt());
        createConfig(serviceHospitalityDomain, customerServiceCallCenterIndustry, "Customer Experience Specialist", "customer experience specialist, cx specialist, trải nghiệm khách hàng, customer experience", expertPromptService.getCustomerExperienceSpecialistPrompt());
        createConfig(serviceHospitalityDomain, customerServiceCallCenterIndustry, "Technical Support", "technical support, hỗ trợ kỹ thuật, tech support, IT support", expertPromptService.getTechnicalSupportPrompt());

        // Service & Hospitality - Retail – Store Operations
        String retailStoreOperationsIndustry = "Retail – Store Operations (Bán lẻ – Vận hành cửa hàng)";
        createConfig(serviceHospitalityDomain, retailStoreOperationsIndustry, "Sales Associate", "sales associate, nhân viên bán hàng, salesperson, retail sales", expertPromptService.getSalesAssociatePrompt());
        createConfig(serviceHospitalityDomain, retailStoreOperationsIndustry, "Store Supervisor", "store supervisor, giám sát cửa hàng, shift leader, retail supervisor", expertPromptService.getStoreSupervisorPrompt());
        createConfig(serviceHospitalityDomain, retailStoreOperationsIndustry, "Retail Manager", "retail manager, quản lý bán lẻ, store manager, shop manager", expertPromptService.getRetailManagerPrompt());
        createConfig(serviceHospitalityDomain, retailStoreOperationsIndustry, "Visual Merchandiser", "visual merchandiser, trưng bày sản phẩm, merchandiser, display specialist", expertPromptService.getVisualMerchandiserPrompt());

        // Social Community - Social Work
        String socialCommunityDomain = "Công tác xã hội – Dịch vụ cộng đồng – Tổ chức phi lợi nhuận";
        String socialWorkIndustry = "Social Work (Công tác xã hội chuyên nghiệp)";
        createConfig(socialCommunityDomain, socialWorkIndustry, "Social Worker", "social worker, nhân viên công tác xã hội, caseworker, social services", expertPromptService.getSocialWorkerPrompt());
        createConfig(socialCommunityDomain, socialWorkIndustry, "Case Manager", "case manager, quản lý hồ sơ ca, care coordinator, case worker", expertPromptService.getCaseManagerPrompt());
        createConfig(socialCommunityDomain, socialWorkIndustry, "Family Support Worker", "family support worker, hỗ trợ gia đình, family services, child and family worker", expertPromptService.getFamilySupportWorkerPrompt());
        createConfig(socialCommunityDomain, socialWorkIndustry, "Child Protection Officer", "child protection officer, bảo vệ trẻ em, child welfare officer, child protection worker", expertPromptService.getChildProtectionOfficerPrompt());
        createConfig(socialCommunityDomain, socialWorkIndustry, "Elderly Care Worker", "elderly care worker, chăm sóc người cao tuổi, aged care worker, geriatric care", expertPromptService.getElderlyCareWorkerPrompt());
        createConfig(socialCommunityDomain, socialWorkIndustry, "Disability Support Worker", "disability support worker, hỗ trợ người khuyết tật, support worker, disability services", expertPromptService.getDisabilitySupportWorkerPrompt());
        createConfig(socialCommunityDomain, socialWorkIndustry, "Crisis Intervention Specialist", "crisis intervention specialist, hỗ trợ khủng hoảng, crisis counselor, crisis worker", expertPromptService.getCrisisInterventionSpecialistPrompt());

        // Social Community - Community Development
        String communityDevelopmentIndustry = "Community Development (Phát triển cộng đồng)";
        createConfig(socialCommunityDomain, communityDevelopmentIndustry, "Community Development Officer", "community development officer, phát triển cộng đồng, community officer, development worker", expertPromptService.getCommunityDevelopmentOfficerPrompt());
        createConfig(socialCommunityDomain, communityDevelopmentIndustry, "Youth Worker", "youth worker, cán bộ thanh thiếu niên, youth counselor, youth development", expertPromptService.getYouthWorkerPrompt());
        createConfig(socialCommunityDomain, communityDevelopmentIndustry, "Community Outreach Coordinator", "community outreach coordinator, tiếp cận cộng đồng, outreach coordinator, community liaison", expertPromptService.getCommunityOutreachCoordinatorPrompt());
        createConfig(socialCommunityDomain, communityDevelopmentIndustry, "Social Program Coordinator", "social program coordinator, điều phối chương trình xã hội, program coordinator, social services", expertPromptService.getSocialProgramCoordinatorPrompt());
        createConfig(socialCommunityDomain, communityDevelopmentIndustry, "Community Health Worker", "community health worker, nhân viên y tế cộng đồng, health worker, public health", expertPromptService.getCommunityHealthWorkerPrompt());
        createConfig(socialCommunityDomain, communityDevelopmentIndustry, "NGO Project Officer", "ngo project officer, chuyên viên dự án phi chính phủ, project officer, ngo worker", expertPromptService.getNGOProjectOfficerPrompt());
        createConfig(socialCommunityDomain, communityDevelopmentIndustry, "Fundraising Specialist", "fundraising specialist, gây quỹ phi lợi nhuận, fundraiser, development officer", expertPromptService.getFundraisingSpecialistPrompt());

        // Social Community - Counseling – Support Services
        String counselingSupportServicesIndustry = "Counseling – Support Services (Tư vấn – Dịch vụ hỗ trợ)";
        createConfig(socialCommunityDomain, counselingSupportServicesIndustry, "Social Counselor", "social counselor, tư vấn xã hội, counselor, social services", expertPromptService.getSocialCounselorPrompt());
        createConfig(socialCommunityDomain, counselingSupportServicesIndustry, "School Counselor", "school counselor, cố vấn trường học, educational counselor, guidance counselor", expertPromptService.getSchoolCounselorPrompt());
        createConfig(socialCommunityDomain, counselingSupportServicesIndustry, "Family Counselor", "family counselor, cố vấn gia đình, marriage counselor, family therapist", expertPromptService.getFamilyCounselorPrompt());
        createConfig(socialCommunityDomain, counselingSupportServicesIndustry, "Rehabilitation Counselor", "rehabilitation counselor, tư vấn phục hồi chức năng, rehab counselor, disability counselor", expertPromptService.getRehabilitationCounselorPrompt());
        createConfig(socialCommunityDomain, counselingSupportServicesIndustry, "Addiction Counselor", "addiction counselor, tư vấn nghiện, substance abuse counselor, addiction specialist", expertPromptService.getAddictionCounselorPrompt());
        createConfig(socialCommunityDomain, counselingSupportServicesIndustry, "Trauma Support Specialist", "trauma support specialist, hỗ trợ sang chấn, trauma counselor, trauma therapist", expertPromptService.getTraumaSupportSpecialistPrompt());

        // Social Community - Nonprofit & Public Service
        String nonprofitPublicServiceIndustry = "Nonprofit & Public Service (Tổ chức phi lợi nhuận – công vụ cộng đồng)";
        createConfig(socialCommunityDomain, nonprofitPublicServiceIndustry, "NGO Coordinator", "ngo coordinator, điều phối tổ chức phi chính phủ, ngo manager, nonprofit coordinator", expertPromptService.getNGOCoordinatorPrompt());
        createConfig(socialCommunityDomain, nonprofitPublicServiceIndustry, "Volunteer Coordinator", "volunteer coordinator, điều phối tình nguyện viên, volunteer manager, community volunteer", expertPromptService.getVolunteerCoordinatorPrompt());
        createConfig(socialCommunityDomain, nonprofitPublicServiceIndustry, "Program Evaluator", "program evaluator, chuyên viên đánh giá chương trình, evaluation specialist, monitoring & evaluation", expertPromptService.getProgramEvaluatorPrompt());
        createConfig(socialCommunityDomain, nonprofitPublicServiceIndustry, "Humanitarian Aid Worker", "humanitarian aid worker, nhân viên trợ giúp nhân đạo, aid worker, emergency response", expertPromptService.getHumanitarianAidWorkerPrompt());
        createConfig(socialCommunityDomain, nonprofitPublicServiceIndustry, "Community Service Manager", "community service manager, quản lý dịch vụ cộng đồng, service manager, community programs", expertPromptService.getCommunityServiceManagerPrompt());
        createConfig(socialCommunityDomain, nonprofitPublicServiceIndustry, "Public Welfare Officer", "public welfare officer, cán bộ phúc lợi công, welfare officer, social services", expertPromptService.getPublicWelfareOfficerPrompt());

        // Agriculture – Environment
        String agricultureEnvironmentDomain = "Agriculture – Environment";
        createConfig(agricultureEnvironmentDomain, "Agriculture (Nông nghiệp)", "Agronomist", "agronomist, kỹ sư nông học, crop scientist, plant scientist", expertPromptService.getAgronomistPrompt());
        createConfig(agricultureEnvironmentDomain, "Agriculture (Nông nghiệp)", "Crop Production Specialist", "crop production specialist, chuyên viên trồng trọt, crop manager, farming specialist", expertPromptService.getCropProductionSpecialistPrompt());
        createConfig(agricultureEnvironmentDomain, "Agriculture (Nông nghiệp)", "Horticulturist", "horticulturist, kỹ sư cây cảnh, hoa kiểng, landscape specialist", expertPromptService.getHorticulturistPrompt());
        createConfig(agricultureEnvironmentDomain, "Agriculture (Nông nghiệp)", "Smart Farming Technician", "smart farming technician, nông nghiệp thông minh, agri-tech specialist, precision agriculture", expertPromptService.getSmartFarmingTechnicianPrompt());
        createConfig(agricultureEnvironmentDomain, "Agriculture (Nông nghiệp)", "Agricultural Technician", "agricultural technician, kỹ thuật viên nông nghiệp, farm technician, agronomy technician", expertPromptService.getAgriculturalTechnicianPrompt());
        createConfig(agricultureEnvironmentDomain, "Agriculture (Nông nghiệp)", "Plant Protection Specialist", "plant protection specialist, chuyên viên bảo vệ thực vật, bvtv, pest management", expertPromptService.getPlantProtectionSpecialistPrompt());
        createConfig(agricultureEnvironmentDomain, "Agriculture (Nông nghiệp)", "Soil Science Specialist", "soil science specialist, chuyên viên đất, dinh dưỡng, soil scientist", expertPromptService.getSoilScienceSpecialistPrompt());
        createConfig(agricultureEnvironmentDomain, "Agriculture (Nông nghiệp)", "Seed Production Specialist", "seed production specialist, sản xuất giống, seed technologist, breeding specialist", expertPromptService.getSeedProductionSpecialistPrompt());

        // Agriculture – Environment - Livestock – Veterinary
        createConfig(agricultureEnvironmentDomain, "Livestock – Veterinary (Chăn nuôi – Thú y)", "Livestock Technician", "livestock technician, chăn nuôi, animal husbandry, farm technician", expertPromptService.getLivestockTechnicianPrompt());
        createConfig(agricultureEnvironmentDomain, "Livestock – Veterinary (Chăn nuôi – Thú y)", "Animal Nutritionist", "animal nutritionist, dinh dưỡng vật nuôi, feed formulation, animal feed", expertPromptService.getAnimalNutritionistPrompt());
        createConfig(agricultureEnvironmentDomain, "Livestock – Veterinary (Chăn nuôi – Thú y)", "Veterinarian", "veterinarian, bác sĩ thú y, veterinary doctor, animal doctor", expertPromptService.getVeterinarianPrompt());
        createConfig(agricultureEnvironmentDomain, "Livestock – Veterinary (Chăn nuôi – Thú y)", "Veterinary Technician", "veterinary technician, ktv thú y, vet tech, veterinary assistant", expertPromptService.getVeterinaryTechnicianPrompt());
        createConfig(agricultureEnvironmentDomain, "Livestock – Veterinary (Chăn nuôi – Thú y)", "Animal Care Specialist", "animal care specialist, chăm sóc động vật, animal welfare, pet care", expertPromptService.getAnimalCareSpecialistPrompt());

        // Agriculture – Environment - Aquaculture – Fisheries
        createConfig(agricultureEnvironmentDomain, "Aquaculture – Fisheries (Thủy sản)", "Aquaculture Specialist", "aquaculture specialist, nuôi trồng thủy sản, fish farming, aquaculture technician", expertPromptService.getAquacultureSpecialistPrompt());
        createConfig(agricultureEnvironmentDomain, "Aquaculture – Fisheries (Thủy sản)", "Fisheries Technician", "fisheries technician, kỹ thuật viên thủy sản, fisheries management, fish stock", expertPromptService.getFisheriesTechnicianPrompt());
        createConfig(agricultureEnvironmentDomain, "Aquaculture – Fisheries (Thủy sản)", "Marine Conservation Officer", "marine conservation officer, cán bộ bảo vệ biển, marine protection, ocean conservation", expertPromptService.getMarineConservationOfficerPrompt());
        createConfig(agricultureEnvironmentDomain, "Aquaculture – Fisheries (Thủy sản)", "Water Quality Technician", "water quality technician, kỹ thuật viên chất lượng nước, water testing, environmental monitoring", expertPromptService.getWaterQualityTechnicianPrompt());

        // Agriculture – Environment - Biotechnology & Food Science
        createConfig(agricultureEnvironmentDomain, "Biotechnology & Food Science (Sinh học – Công nghệ thực phẩm)", "Biotechnologist", "biotechnologist, nhà sinh học, molecular biology, genetic engineering", expertPromptService.getBiotechnologistPrompt());
        createConfig(agricultureEnvironmentDomain, "Biotechnology & Food Science (Sinh học – Công nghệ thực phẩm)", "Lab Technician – Biology", "lab technician biology, kỹ thuật viên lab sinh học, biology lab, laboratory technician", expertPromptService.getLabTechnicianBiologyPrompt());
        createConfig(agricultureEnvironmentDomain, "Biotechnology & Food Science (Sinh học – Công nghệ thực phẩm)", "Food Technology Specialist", "food technology specialist, chuyên viên công nghệ thực phẩm, food science, food processing", expertPromptService.getFoodTechnologySpecialistPrompt());
        createConfig(agricultureEnvironmentDomain, "Biotechnology & Food Science (Sinh học – Công nghệ thực phẩm)", "Food Safety Inspector", "food safety inspector, thanh tra an toàn thực phẩm, food safety, haccp", expertPromptService.getFoodSafetyInspectorPrompt());
        createConfig(agricultureEnvironmentDomain, "Biotechnology & Food Science (Sinh học – Công nghệ thực phẩm)", "Microbiology Technician", "microbiology technician, kỹ thuật viên vi sinh vật, microbiology, microbial testing", expertPromptService.getMicrobiologyTechnicianPrompt());

        // Agriculture – Environment - Environment – Conservation
        createConfig(agricultureEnvironmentDomain, "Environment – Conservation (Môi trường – Tài nguyên)", "Environmental Engineer", "environmental engineer, kỹ sư môi trường, environmental engineering, pollution control", expertPromptService.getEnvironmentalEngineerPrompt());
        createConfig(agricultureEnvironmentDomain, "Environment – Conservation (Môi trường – Tài nguyên)", "Environmental Scientist", "environmental scientist, nhà khoa học môi trường, environmental science, ecology", expertPromptService.getEnvironmentalScientistPrompt());
        createConfig(agricultureEnvironmentDomain, "Environment – Conservation (Môi trường – Tài nguyên)", "Waste Management Specialist", "waste management specialist, chuyên viên quản lý chất thải, waste management, recycling", expertPromptService.getWasteManagementSpecialistPrompt());
        createConfig(agricultureEnvironmentDomain, "Environment – Conservation (Môi trường – Tài nguyên)", "Ecology Researcher", "ecology researcher, nhà nghiên cứu sinh thái, ecology, biodiversity", expertPromptService.getEcologyResearcherPrompt());
        createConfig(agricultureEnvironmentDomain, "Environment – Conservation (Môi trường – Tài nguyên)", "Renewable Energy Technician", "renewable energy technician, kỹ thuật viên năng lượng tái tạo, renewable energy, solar energy", expertPromptService.getRenewableEnergyTechnicianPrompt());
        createConfig(agricultureEnvironmentDomain, "Environment – Conservation (Môi trường – Tài nguyên)", "Forest Conservation Officer", "forest conservation officer, cán bộ bảo vệ rừng, lâm nghiệp, forest ranger", expertPromptService.getForestConservationOfficerPrompt());
        createConfig(agricultureEnvironmentDomain, "Environment – Conservation (Môi trường – Tài nguyên)", "GIS Specialist", "gis specialist, chuyên viên hệ thống thông tin địa lý, geographic information systems, mapping", expertPromptService.getGISSpecialistPrompt());

        // Agriculture – Environment - Climate – Water – Meteorology
        createConfig(agricultureEnvironmentDomain, "Climate – Water – Meteorology (Khí tượng – Thủy văn)", "Hydrologist", "hydrologist, tài nguyên nước, hydrology, water resources", expertPromptService.getHydrologistPrompt());
        createConfig(agricultureEnvironmentDomain, "Climate – Water – Meteorology (Khí tượng – Thủy văn)", "Meteorologist", "meteorologist, khí tượng thủy văn, meteorology, weather forecasting", expertPromptService.getMeteorologistPrompt());
        createConfig(agricultureEnvironmentDomain, "Climate – Water – Meteorology (Khí tượng – Thủy văn)", "Climate Change Analyst", "climate change analyst, phân tích biến đổi khí hậu, climate science, carbon management", expertPromptService.getClimateChangeAnalystPrompt());
        createConfig(agricultureEnvironmentDomain, "Climate – Water – Meteorology (Khí tượng – Thủy văn)", "Water Resources Engineer", "water resources engineer, kỹ sư tài nguyên nước, hydraulic engineering, dam engineering", expertPromptService.getWaterResourcesEngineerPrompt());

        log.info("Expert prompts seeded/updated successfully!");
    }

    private void createConfig(String domain, String industry, String role, String keywords, String prompt) {
        // ... (rest of the code remains the same)
        var existingConfig = repository.findByDomainAndIndustryAndJobRoleAndIsActiveTrue(domain, industry, role);
        
        if (existingConfig.isPresent()) {
            ExpertPromptConfig config = existingConfig.get();
            // Update prompt if changed (allows updating prompt content from code)
            if (!config.getSystemPrompt().equals(prompt)) {
                config.setSystemPrompt(prompt);
                repository.save(config);
                log.info("Updated prompt for role: {}", role);
            }
        } else {
            repository.save(ExpertPromptConfig.builder()
                    .domain(domain)
                    .industry(industry)
                    .jobRole(role)
                    .keywords(keywords)
                    .systemPrompt(prompt)
                    .isActive(true)
                    .build());
            log.info("Created new prompt config for role: {}", role);
        }
    }
}
