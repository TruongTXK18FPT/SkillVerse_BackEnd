package com.exe.skillverse_backend.ai_service.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Service
public class TaxonomyService {
    private final Map<String, Set<String>> domainKeywords;
    private final Map<String, Set<String>> roleKeywords;
    private final Map<String, Set<String>> industryKeywords;
    private final com.exe.skillverse_backend.ai_service.repository.TaxonomyEntryRepository taxonomyEntryRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, JsonNode> domainPacks = new HashMap<>();
    private final Map<String, JsonNode> rolePacks = new HashMap<>();
    private final Map<String, String> domainNameToPackId = new HashMap<>();

    public TaxonomyService(com.exe.skillverse_backend.ai_service.repository.TaxonomyEntryRepository taxonomyEntryRepository) {
        domainKeywords = new HashMap<>();
        roleKeywords = new HashMap<>();
        industryKeywords = new HashMap<>();
        this.taxonomyEntryRepository = taxonomyEntryRepository;

        Map<String, Set<String>> defaultDomains = new HashMap<>();
        Map<String, Set<String>> defaultRoles = new HashMap<>();

        defaultDomains.put("IT", Set.of("developer","backend","frontend","software","data","cloud","security","devops","ai","ml","dl","nlp","iot","mobile","android","ios","web"));
        defaultDomains.put("Design", Set.of("design","ui","ux","graphic","video","motion","illustration","branding","visual","product","interaction","animator","vfx","3d","photography"));
        defaultDomains.put("Business", Set.of("marketing","sales","business","seo","brand","content","finance","accounting","product manager","project manager","operations"));
        defaultDomains.put("Engineering", Set.of("engineer","manufacturing","cnc","mechanical","mechatronics","maintenance","industrial"));
        defaultDomains.put("Healthcare", Set.of("health","medical","y tế","nurse","clinics","hospital"));
        defaultDomains.put("Education", Set.of("education","teacher","giáo dục","training","edtech"));
        defaultDomains.put("Logistics", Set.of("logistics","supply chain","warehouse","shipping","trade","import","export"));
        defaultDomains.put("Legal", Set.of("legal","law","paralegal","compliance","regulation"));
        defaultDomains.put("Arts", Set.of("art","artist","vfx","3d","photography","painter","concept","digital art"));
        defaultDomains.put("Service", Set.of("service","hospitality","restaurant","hotel","f&b","food","beverage"));
        defaultDomains.put("SocialCommunity", Set.of("social","community","ngo","nonprofit","charity"));
        defaultDomains.put("AgricultureEnvironment", Set.of("agriculture","environment","soil","crop","horticulture","plant"));

        defaultRoles.put("Backend Developer", Set.of("backend","spring","node","api","server","java","golang","dotnet"));
        defaultRoles.put("Frontend Developer", Set.of("frontend","react","vue","angular","web","ui"));
        defaultRoles.put("Data Analyst", Set.of("data analyst","excel","power bi","tableau","analytics"));
        defaultRoles.put("UX Designer", Set.of("ux","user experience","research","wireframe"));
        defaultRoles.put("UI Designer", Set.of("ui","interface","visual","figma"));
        defaultRoles.put("Digital Marketer", Set.of("marketing","seo","sem","content","social"));

        boolean loadedDb = loadFromDb();
        if (!loadedDb && !loadFromConfig(defaultDomains, defaultRoles)) {
            domainKeywords.putAll(defaultDomains);
            roleKeywords.putAll(defaultRoles);
        }

        domainNameToPackId.put("IT", "information_technology");
        domainNameToPackId.put("Design", "design_creative_content");
        domainNameToPackId.put("Business", "business_marketing_management");
        domainNameToPackId.put("Healthcare", "healthcare");
        domainNameToPackId.put("Engineering", "engineering_industrial_manufacturing");
        domainNameToPackId.put("Education", "education_training_edtech");
        domainNameToPackId.put("Legal", "legal_public_admin");
        domainNameToPackId.put("Logistics", "logistics_supply_chain_import_export");
        domainNameToPackId.put("Arts", "arts_entertainment");
        domainNameToPackId.put("Service", "service_hospitality");
        domainNameToPackId.put("SocialCommunity", "social_work_community_nonprofit");
        domainNameToPackId.put("AgricultureEnvironment", "agriculture_environment");
    }

    @PostConstruct
    public void initExpertPacks() {
        loadExpertPacksFromMarkdown("c:\\WorkSpace\\EXE201\\skillverse_expert_packs_12_domains.md");
    }

    private boolean loadFromDb() {
        try {
            java.util.List<com.exe.skillverse_backend.ai_service.entity.TaxonomyEntry> entries = taxonomyEntryRepository.findByActiveTrue();
            if (entries == null || entries.isEmpty()) return false;
            for (var e : entries) {
                String domain = e.getDomain() != null ? e.getDomain() : "";
                String role = e.getRole();
                String industry = e.getIndustry();
                String kw = e.getKeywords();
                if (domain != null && kw != null && !kw.isBlank()) {
                    domainKeywords.computeIfAbsent(domain, k -> new HashSet<>()).addAll(splitKeywords(kw));
                }
                if (role != null && kw != null && !kw.isBlank()) {
                    roleKeywords.computeIfAbsent(role, k -> new HashSet<>()).addAll(splitKeywords(kw));
                }
                if (industry != null && kw != null && !kw.isBlank()) {
                    industryKeywords.computeIfAbsent(industry, k -> new HashSet<>()).addAll(splitKeywords(kw));
                }
            }
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean loadExpertPacksFromMarkdown(String absolutePath) {
        try {
            Path p = Path.of(absolutePath);
            if (!Files.exists(p)) return false;
            List<String> lines = Files.readAllLines(p);
            StringBuilder sb = new StringBuilder();
            boolean inJson = false;
            for (String line : lines) {
                if (!inJson && line.trim().startsWith("```json")) {
                    inJson = true;
                    sb.setLength(0);
                    continue;
                }
                if (inJson && line.trim().startsWith("```")) {
                    String json = sb.toString();
                    JsonNode node = objectMapper.readTree(json);
                    if (node.has("roleId")) {
                        String roleId = node.path("roleId").asText();
                        if (roleId != null && !roleId.isBlank()) rolePacks.put(roleId, node);
                    } else if (node.has("domainId")) {
                        String domainId = node.path("domainId").asText();
                        if (domainId != null && !domainId.isBlank()) domainPacks.put(domainId, node);
                    }
                    inJson = false;
                    sb.setLength(0);
                    continue;
                }
                if (inJson) {
                    sb.append(line).append("\n");
                }
            }
            return !domainPacks.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private Set<String> splitKeywords(String s) {
        Set<String> set = new HashSet<>();
        String[] parts = s.toLowerCase(Locale.ROOT).split(",");
        for (String p : parts) {
            String t = p.trim();
            if (!t.isEmpty()) set.add(t);
        }
        return set;
    }

    private boolean loadFromConfig(Map<String, Set<String>> defaultDomains, Map<String, Set<String>> defaultRoles) {
        try {
            ClassPathResource resource = new ClassPathResource("ai/taxonomy.json");
            if (!resource.exists()) return false;
            try (InputStream is = resource.getInputStream()) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(is);
                JsonNode domains = root.path("domains");
                JsonNode roles = root.path("roles");
                if (domains.isObject()) {
                    Iterator<String> names = domains.fieldNames();
                    while (names.hasNext()) {
                        String name = names.next();
                        Set<String> keys = new HashSet<>();
                        JsonNode arr = domains.get(name);
                        if (arr.isArray()) for (JsonNode k : arr) keys.add(k.asText());
                        domainKeywords.put(name, keys);
                    }
                }
                if (roles.isObject()) {
                    Iterator<String> names = roles.fieldNames();
                    while (names.hasNext()) {
                        String name = names.next();
                        Set<String> keys = new HashSet<>();
                        JsonNode arr = roles.get(name);
                        if (arr.isArray()) for (JsonNode k : arr) keys.add(k.asText());
                        roleKeywords.put(name, keys);
                    }
                }
                if (domainKeywords.isEmpty()) domainKeywords.putAll(defaultDomains);
                if (roleKeywords.isEmpty()) roleKeywords.putAll(defaultRoles);
                return true;
            }
        } catch (Exception e) {
            domainKeywords.putAll(defaultDomains);
            roleKeywords.putAll(defaultRoles);
            return false;
        }
    }

    public String detectDomain(String target, String industry, String role) {
        String ref = normalize(target, role, industry);
        String best = null;
        int bestScore = 0;
        for (Map.Entry<String, Set<String>> e : domainKeywords.entrySet()) {
            int s = score(ref, e.getValue());
            if (s > bestScore) {
                best = e.getKey();
                bestScore = s;
            }
        }
        return best;
    }

    public String detectRoleCategory(String roleOrTarget) {
        String ref = normalize(roleOrTarget, null, null);
        String best = roleOrTarget;
        int bestScore = 0;
        for (Map.Entry<String, Set<String>> e : roleKeywords.entrySet()) {
            int s = score(ref, e.getValue());
            if (s > bestScore) {
                best = e.getKey();
                bestScore = s;
            }
        }
        return best;
    }

    public String detectIndustry(String target, String provided) {
        if (provided != null && !provided.isBlank()) return provided;
        String ref = normalize(target, null, null);
        String best = null;
        int bestScore = 0;
        for (Map.Entry<String, Set<String>> e : industryKeywords.entrySet()) {
            int s = score(ref, e.getValue());
            if (s > bestScore) {
                best = e.getKey();
                bestScore = s;
            }
        }
        return best;
    }

    private String normalize(String a, String b, String c) {
        StringBuilder sb = new StringBuilder();
        if (a != null) sb.append(a).append(" ");
        if (b != null) sb.append(b).append(" ");
        if (c != null) sb.append(c).append(" ");
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    private boolean containsAny(String text, Set<String> keys) {
        for (String k : keys) {
            if (text.contains(k)) return true;
        }
        return false;
    }

    private int score(String text, Set<String> keys) {
        int s = 0;
        for (String k : keys) {
            if (text.contains(k)) s++;
        }
        return s;
    }

    public String mapToDomainPackId(String detectedDomainName) {
        if (detectedDomainName == null) return null;
        return domainNameToPackId.get(detectedDomainName);
    }

    public String normalizeToRoleId(String roleCategoryName) {
        if (roleCategoryName == null) return null;
        String r = roleCategoryName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","_");
        if (r.startsWith("_")) r = r.substring(1);
        if (r.endsWith("_")) r = r.substring(0, r.length()-1);
        return r;
    }

    public boolean isRoleKnown(String domainId, String roleId) {
        if (roleId == null || domainId == null) return false;
        JsonNode rolePack = rolePacks.get(roleId);
        if (rolePack == null) return false;
        String d = rolePack.path("domainId").asText();
        return domainId.equals(d);
    }

    public Set<String> getKnownRolesForDomain(String domainId) {
        Set<String> set = new HashSet<>();
        if (domainId == null) return set;
        for (Map.Entry<String, JsonNode> e : rolePacks.entrySet()) {
            if (domainId.equals(e.getValue().path("domainId").asText())) set.add(e.getKey());
        }
        return set;
    }

    public Set<String> getAllowedTools(String domainId) {
        Set<String> set = new HashSet<>();
        JsonNode domainPack = domainPacks.get(domainId);
        if (domainPack != null) {
            JsonNode tools = domainPack.path("localContext").path("popularTools");
            if (tools.isArray()) for (JsonNode t : tools) set.add(t.asText());
        }
        return set;
    }

    public Set<String> getAllowedSkills(String domainId, String roleId) {
        Set<String> set = new HashSet<>();
        JsonNode rolePack = rolePacks.get(roleId);
        if (rolePack != null) {
            JsonNode nodes = rolePack.path("skillDependencyGraph").path("nodes");
            if (nodes.isArray()) for (JsonNode n : nodes) set.add(n.asText());
        }
        JsonNode domainPack = domainPacks.get(domainId);
        if (domainPack != null) {
            JsonNode tax = domainPack.path("skillTaxonomy");
            addTaxonomySkills(set, tax.path("coreSkills"));
            addTaxonomySkills(set, tax.path("supportingSkills"));
            addTaxonomySkills(set, tax.path("differentiationSkills"));
        }
        return set;
    }

    private void addTaxonomySkills(Set<String> into, JsonNode arr) {
        if (arr != null && arr.isArray()) {
            for (JsonNode n : arr) into.add(n.asText());
        }
    }

    public boolean isSkillKnown(String domainId, String roleId, String skillName) {
        if (skillName == null) return false;
        String s = skillName.toLowerCase(Locale.ROOT);
        for (String k : getAllowedSkills(domainId, roleId)) {
            if (s.equalsIgnoreCase(k) || s.contains(k.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    public boolean isToolKnown(String domainId, String toolName) {
        if (toolName == null) return false;
        String t = toolName.toLowerCase(Locale.ROOT);
        for (String k : getAllowedTools(domainId)) {
            if (t.equalsIgnoreCase(k) || t.contains(k.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }
}
