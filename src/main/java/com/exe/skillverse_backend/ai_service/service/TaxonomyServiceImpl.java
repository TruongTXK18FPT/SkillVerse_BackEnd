package com.exe.skillverse_backend.ai_service.service;

import com.exe.skillverse_backend.ai_service.repository.TaxonomyEntryRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import com.exe.skillverse_backend.ai_service.entity.TaxonomyEntry;

@Service
@Slf4j
public class TaxonomyServiceImpl implements TaxonomyService {
    private static final String EXPERT_PACK_PATH_ENV = "SKILLVERSE_EXPERT_PACK_PATH";
    private static final String EXPERT_PACK_PATH_PROPERTY = "skillverse.expert-pack.path";

    private final Map<String, Set<String>> domainKeywords;
    private final Map<String, Set<String>> roleKeywords;
    private final Map<String, Set<String>> industryKeywords;
    private final TaxonomyEntryRepository taxonomyEntryRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, JsonNode> domainPacks = new HashMap<>();
    private final Map<String, JsonNode> rolePacks = new HashMap<>();
    private final Map<String, String> domainNameToPackId = new HashMap<>();

    public TaxonomyServiceImpl(TaxonomyEntryRepository taxonomyEntryRepository) {
        domainKeywords = new HashMap<>();
        roleKeywords = new HashMap<>();
        industryKeywords = new HashMap<>();
        this.taxonomyEntryRepository = taxonomyEntryRepository;

        Map<String, Set<String>> defaultDomains = new HashMap<>();
        Map<String, Set<String>> defaultRoles = new HashMap<>();

        defaultDomains.put("IT", Set.of("developer", "backend", "frontend", "software", "data", "cloud", "security",
                "devops", "ai", "ml", "dl", "nlp", "iot", "mobile", "android", "ios", "web"));
        defaultDomains.put("Design", Set.of("design", "ui", "ux", "graphic", "video", "motion", "illustration",
                "branding", "visual", "product", "interaction", "animator", "vfx", "3d", "photography"));
        defaultDomains.put("Business", Set.of("marketing", "sales", "business", "seo", "brand", "content", "finance",
                "accounting", "product manager", "project manager", "operations"));
        defaultDomains.put("Engineering",
                Set.of("engineer", "manufacturing", "cnc", "mechanical", "mechatronics", "maintenance", "industrial"));
        defaultDomains.put("Healthcare", Set.of("health", "medical", "y tế", "nurse", "clinics", "hospital"));
        defaultDomains.put("Education", Set.of("education", "teacher", "giáo dục", "training", "edtech"));
        defaultDomains.put("Logistics",
                Set.of("logistics", "supply chain", "warehouse", "shipping", "trade", "import", "export"));
        defaultDomains.put("Legal", Set.of("legal", "law", "paralegal", "compliance", "regulation"));
        defaultDomains.put("Arts",
                Set.of("art", "artist", "vfx", "3d", "photography", "painter", "concept", "digital art"));
        defaultDomains.put("Service",
                Set.of("service", "hospitality", "restaurant", "hotel", "f&b", "food", "beverage"));
        defaultDomains.put("SocialCommunity", Set.of("social", "community", "ngo", "nonprofit", "charity"));
        defaultDomains.put("AgricultureEnvironment",
                Set.of("agriculture", "environment", "soil", "crop", "horticulture", "plant"));

        defaultRoles.put("Backend Developer",
                Set.of("backend", "spring", "node", "api", "server", "java", "golang", "dotnet"));
        defaultRoles.put("Frontend Developer", Set.of("frontend", "react", "vue", "angular", "web", "ui"));
        defaultRoles.put("Data Analyst", Set.of("data analyst", "excel", "power bi", "tableau", "analytics"));
        defaultRoles.put("UX Designer", Set.of("ux", "user experience", "research", "wireframe"));
        defaultRoles.put("UI Designer", Set.of("ui", "interface", "visual", "figma"));
        defaultRoles.put("Digital Marketer", Set.of("marketing", "seo", "sem", "content", "social"));

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
        boolean loaded = false;

        String configuredPath = resolveConfiguredExpertPackPath();
        if (configuredPath != null) {
            loaded = loadExpertPacksFromMarkdown(configuredPath);
            if (loaded) {
                log.info("Loaded taxonomy expert packs from configured path");
            }
        }

        if (!loaded) {
            loaded = loadExpertPacksFromClasspath("ai/skillverse_expert_packs_12_domains.md");
            if (loaded) {
                log.info("Loaded taxonomy expert packs from classpath resource");
            }
        }

        if (!loaded) {
            loaded = loadExpertPacksFromMarkdown("skillverse_expert_packs_12_domains.md");
            if (loaded) {
                log.info("Loaded taxonomy expert packs from local workspace path");
            }
        }

        if (!loaded) {
            log.warn("Taxonomy expert packs not found; continuing with lightweight taxonomy keywords only");
        }
    }

    private String resolveConfiguredExpertPackPath() {
        String configured = System.getProperty(EXPERT_PACK_PATH_PROPERTY);
        if (configured == null || configured.isBlank()) {
            configured = System.getenv(EXPERT_PACK_PATH_ENV);
        }
        if (configured == null) {
            return null;
        }
        String trimmed = configured.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean loadFromDb() {
        try {
            List<TaxonomyEntry> entries = taxonomyEntryRepository
                    .findByActiveTrue();
            if (entries == null || entries.isEmpty())
                return false;
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

    private boolean loadExpertPacksFromMarkdown(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return false;
        }
        try {
            Path p = Path.of(filePath.trim());
            if (!Files.exists(p))
                return false;
            List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
            return loadExpertPacksFromLines(lines);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean loadExpertPacksFromClasspath(String resourcePath) {
        if (resourcePath == null || resourcePath.isBlank()) {
            return false;
        }
        try {
            ClassPathResource resource = new ClassPathResource(resourcePath.trim());
            if (!resource.exists()) {
                return false;
            }
            try (InputStream is = resource.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                List<String> lines = reader.lines().collect(Collectors.toList());
                return loadExpertPacksFromLines(lines);
            }
        } catch (Exception e) {
            return false;
        }
    }

    private boolean loadExpertPacksFromLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return false;
        }

        Map<String, JsonNode> parsedDomainPacks = new HashMap<>();
        Map<String, JsonNode> parsedRolePacks = new HashMap<>();

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
                try {
                    JsonNode node = objectMapper.readTree(json);
                    if (node.has("roleId")) {
                        String roleId = node.path("roleId").asText();
                        if (roleId != null && !roleId.isBlank()) {
                            parsedRolePacks.put(roleId, node);
                        }
                    } else if (node.has("domainId")) {
                        String domainId = node.path("domainId").asText();
                        if (domainId != null && !domainId.isBlank()) {
                            parsedDomainPacks.put(domainId, node);
                        }
                    }
                } catch (Exception ignored) {
                    // Ignore malformed fenced JSON blocks and continue parsing remaining blocks.
                }
                inJson = false;
                sb.setLength(0);
                continue;
            }
            if (inJson) {
                sb.append(line).append("\n");
            }
        }

        if (parsedDomainPacks.isEmpty() && parsedRolePacks.isEmpty()) {
            return false;
        }

        domainPacks.putAll(parsedDomainPacks);
        rolePacks.putAll(parsedRolePacks);
        return !domainPacks.isEmpty();
    }

    private Set<String> splitKeywords(String s) {
        Set<String> set = new HashSet<>();
        String[] parts = s.toLowerCase(Locale.ROOT).split(",");
        for (String p : parts) {
            String t = p.trim();
            if (!t.isEmpty())
                set.add(t);
        }
        return set;
    }

    private boolean loadFromConfig(Map<String, Set<String>> defaultDomains, Map<String, Set<String>> defaultRoles) {
        try {
            ClassPathResource resource = new ClassPathResource("ai/taxonomy.json");
            if (!resource.exists())
                return false;
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
                        if (arr.isArray())
                            for (JsonNode k : arr)
                                keys.add(k.asText());
                        domainKeywords.put(name, keys);
                    }
                }
                if (roles.isObject()) {
                    Iterator<String> names = roles.fieldNames();
                    while (names.hasNext()) {
                        String name = names.next();
                        Set<String> keys = new HashSet<>();
                        JsonNode arr = roles.get(name);
                        if (arr.isArray())
                            for (JsonNode k : arr)
                                keys.add(k.asText());
                        roleKeywords.put(name, keys);
                    }
                }
                if (domainKeywords.isEmpty())
                    domainKeywords.putAll(defaultDomains);
                if (roleKeywords.isEmpty())
                    roleKeywords.putAll(defaultRoles);
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
        if (provided != null && !provided.isBlank())
            return provided;
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
        if (a != null)
            sb.append(a).append(" ");
        if (b != null)
            sb.append(b).append(" ");
        if (c != null)
            sb.append(c).append(" ");
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    private boolean containsAny(String text, Set<String> keys) {
        for (String k : keys) {
            if (text.contains(k))
                return true;
        }
        return false;
    }

    private int score(String text, Set<String> keys) {
        int s = 0;
        for (String k : keys) {
            if (text.contains(k))
                s++;
        }
        return s;
    }

    public String mapToDomainPackId(String detectedDomainName) {
        if (detectedDomainName == null)
            return null;
        return domainNameToPackId.get(detectedDomainName);
    }

    public String normalizeToRoleId(String roleCategoryName) {
        if (roleCategoryName == null)
            return null;
        String r = roleCategoryName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        if (r.startsWith("_"))
            r = r.substring(1);
        if (r.endsWith("_"))
            r = r.substring(0, r.length() - 1);
        return r;
    }

    public boolean isRoleKnown(String domainId, String roleId) {
        if (roleId == null || domainId == null)
            return false;
        JsonNode rolePack = rolePacks.get(roleId);
        if (rolePack == null)
            return false;
        String d = rolePack.path("domainId").asText();
        return domainId.equals(d);
    }

    public Set<String> getKnownRolesForDomain(String domainId) {
        Set<String> set = new HashSet<>();
        if (domainId == null)
            return set;
        for (Map.Entry<String, JsonNode> e : rolePacks.entrySet()) {
            if (domainId.equals(e.getValue().path("domainId").asText()))
                set.add(e.getKey());
        }
        return set;
    }

    public Set<String> getAllowedTools(String domainId) {
        Set<String> set = new HashSet<>();
        JsonNode domainPack = domainPacks.get(domainId);
        if (domainPack != null) {
            JsonNode tools = domainPack.path("localContext").path("popularTools");
            if (tools.isArray())
                for (JsonNode t : tools)
                    set.add(t.asText());
        }
        return set;
    }

    public Set<String> getAllowedSkills(String domainId, String roleId) {
        Set<String> set = new HashSet<>();
        JsonNode rolePack = rolePacks.get(roleId);
        if (rolePack != null) {
            JsonNode nodes = rolePack.path("skillDependencyGraph").path("nodes");
            if (nodes.isArray())
                for (JsonNode n : nodes)
                    set.add(n.asText());
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
            for (JsonNode n : arr)
                into.add(n.asText());
        }
    }

    public boolean isSkillKnown(String domainId, String roleId, String skillName) {
        if (skillName == null)
            return false;
        String s = skillName.toLowerCase(Locale.ROOT);
        for (String k : getAllowedSkills(domainId, roleId)) {
            if (s.equalsIgnoreCase(k) || s.contains(k.toLowerCase(Locale.ROOT)))
                return true;
        }
        return false;
    }

    public boolean isToolKnown(String domainId, String toolName) {
        if (toolName == null)
            return false;
        String t = toolName.toLowerCase(Locale.ROOT);
        for (String k : getAllowedTools(domainId)) {
            if (t.equalsIgnoreCase(k) || t.contains(k.toLowerCase(Locale.ROOT)))
                return true;
        }
        return false;
    }

    @Override
    public Set<String> expandQueryWithTaxonomy(String rawQuery, Set<String> originalTerms, int maxExpansionTerms) {
        LinkedHashSet<String> expanded = new LinkedHashSet<>();
        if (originalTerms != null) {
            for (String term : originalTerms) {
                String normalized = normalizeCandidate(term);
                if (!normalized.isBlank()) {
                    expanded.add(normalized);
                }
            }
        }

        if (expanded.isEmpty() || maxExpansionTerms <= 0) {
            return expanded;
        }

        try {
            String safeRaw = rawQuery != null ? rawQuery : "";
            String detectedDomain = detectDomain(safeRaw, null, null);
            String detectedRole = detectRoleCategory(safeRaw);
            String domainId = mapToDomainPackId(detectedDomain);
            String roleId = normalizeToRoleId(detectedRole);

            Set<String> candidates = new LinkedHashSet<>();
            if (domainId != null) {
                candidates.addAll(getAllowedSkills(domainId, roleId));
                candidates.addAll(getAllowedTools(domainId));
            }

            if (detectedDomain != null) {
                candidates.addAll(domainKeywords.getOrDefault(detectedDomain, Set.of()));
            }
            if (detectedRole != null) {
                candidates.addAll(roleKeywords.getOrDefault(detectedRole, Set.of()));
            }

            String normalizedRaw = normalize(safeRaw, null, null);
            List<String> baseTerms = new ArrayList<>(expanded);

            List<String> rankedCandidates = candidates.stream()
                    .map(this::normalizeCandidate)
                    .filter(term -> term.length() >= 2)
                    .filter(term -> !expanded.contains(term))
                    .map(term -> new RankedTerm(term, scoreCandidate(term, baseTerms, normalizedRaw)))
                    .filter(rt -> rt.score() > 0)
                    .sorted((a, b) -> Integer.compare(b.score(), a.score()))
                    .limit(maxExpansionTerms)
                    .map(RankedTerm::term)
                    .collect(Collectors.toList());

            expanded.addAll(rankedCandidates);
            return expanded;
        } catch (Exception ex) {
            return expanded;
        }
    }

    private String normalizeCandidate(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private int scoreCandidate(String candidate, List<String> baseTerms, String normalizedRaw) {
        int score = 0;

        if (normalizedRaw.contains(candidate)) {
            score += 4;
        }

        for (String base : baseTerms) {
            if (candidate.equals(base)) {
                score += 5;
            } else if (candidate.contains(base) || base.contains(candidate)) {
                score += 3;
            } else if (sharesToken(candidate, base)) {
                score += 1;
            }
        }

        if (candidate.contains(" ")) {
            score += 1;
        }

        return score;
    }

    private boolean sharesToken(String left, String right) {
        if (left.isBlank() || right.isBlank()) {
            return false;
        }

        Set<String> leftTokens = new HashSet<>();
        for (String token : left.split("\\s+")) {
            if (!token.isBlank()) {
                leftTokens.add(token);
            }
        }
        for (String token : right.split("\\s+")) {
            if (leftTokens.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private record RankedTerm(String term, int score) {}
}
