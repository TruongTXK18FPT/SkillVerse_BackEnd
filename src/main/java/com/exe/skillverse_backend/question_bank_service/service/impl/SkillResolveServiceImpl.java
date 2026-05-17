package com.exe.skillverse_backend.question_bank_service.service.impl;

import com.exe.skillverse_backend.ai_service.entity.ExpertPromptConfig;
import com.exe.skillverse_backend.ai_service.repository.ExpertPromptConfigRepository;
import com.exe.skillverse_backend.question_bank_service.dto.request.CreateQuestionBankRequest;
import com.exe.skillverse_backend.question_bank_service.dto.response.QuestionBankResponse;
import com.exe.skillverse_backend.question_bank_service.dto.response.SkillResolveResponse;
import com.exe.skillverse_backend.question_bank_service.repository.QuestionBankRepository;
import com.exe.skillverse_backend.question_bank_service.service.QuestionBankService;
import com.exe.skillverse_backend.question_bank_service.service.SkillResolveService;
import com.exe.skillverse_backend.shared.exception.ApiException;
import com.exe.skillverse_backend.shared.exception.ErrorCode;
import com.exe.skillverse_backend.shared.util.SkillNameUtils;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deterministic skill resolver for matching a typed skill to the closest
 * domain / industry / job role from ExpertPromptConfig.
 *
 * This intentionally avoids AI calls. The resolver scores aliases, keywords,
 * role hints, job-role text, industry, and typo-tolerant token similarity.
 */
@Service
@Transactional
public class SkillResolveServiceImpl implements SkillResolveService {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_SEARCH_CHARS = Pattern.compile("[^a-z0-9\\s.#+]");
    private static final Pattern SEPARATORS = Pattern.compile("[_\\-/\\\\]+");
    private static final int AUTO_CREATE_MIN_CONFIDENCE = 50;

    private static final Map<String, List<String>> SKILL_ALIASES = Map.ofEntries(
            Map.entry("react", List.of("react", "reactjs", "react.js", "react js")),
            Map.entry("vue", List.of("vue", "vuejs", "vue.js", "vue js")),
            Map.entry("angular", List.of("angular", "angularjs", "angular.js")),
            Map.entry("node", List.of("node", "nodejs", "node.js", "node js")),
            Map.entry("next", List.of("next", "nextjs", "next.js", "next js")),
            Map.entry("spring", List.of("spring", "springboot", "spring boot", "spring_boot", "spring framework")),
            Map.entry("java", List.of("java", "java se", "java ee", "jdk")),
            Map.entry("python", List.of("python", "python3", "py")),
            Map.entry("javascript", List.of("javascript", "js", "ecmascript", "es6")),
            Map.entry("typescript", List.of("typescript", "ts")),
            Map.entry("csharp", List.of("c#", "csharp", "c sharp", "dotnet", ".net", "asp.net")),
            Map.entry("cpp", List.of("c++", "cpp", "cplusplus")),
            Map.entry("php", List.of("php", "laravel", "symfony")),
            Map.entry("go", List.of("go", "golang")),
            Map.entry("flutter", List.of("flutter", "dart", "flutter dart")),
            Map.entry("docker", List.of("docker", "dockerfile", "docker compose", "docker-compose")),
            Map.entry("kubernetes", List.of("kubernetes", "k8s", "kube")),
            Map.entry("aws", List.of("aws", "amazon web services", "amazon cloud")),
            Map.entry("azure", List.of("azure", "microsoft azure", "ms azure")),
            Map.entry("gcp", List.of("gcp", "google cloud", "google cloud platform")),
            Map.entry("sql", List.of("sql", "mysql", "postgresql", "postgres", "mssql", "sql server")),
            Map.entry("figma", List.of("figma", "figma design")),
            Map.entry("uiux", List.of("ui/ux", "uiux", "ui ux", "ui/ux design", "user experience", "user interface")),
            Map.entry("ml", List.of("ml", "machine learning", "deep learning")),
            Map.entry("ai", List.of("ai", "artificial intelligence", "generative ai", "gen ai", "llm")),
            Map.entry("devops", List.of("devops", "dev ops", "ci/cd", "cicd")),
            Map.entry("qa", List.of("qa", "quality assurance", "testing", "tester", "qc")),
            Map.entry("excel", List.of("excel", "microsoft excel", "ms excel", "spreadsheet")),
            Map.entry("powerbi", List.of("powerbi", "power bi", "power_bi")),
            Map.entry("seo", List.of("seo", "search engine optimization")),
            Map.entry("marketing", List.of("marketing", "digital marketing", "online marketing")),
            Map.entry("sales", List.of("sales", "ban hang", "kinh doanh")),
            Map.entry("accounting", List.of("accounting", "ke toan", "bookkeeping")),
            Map.entry("hr", List.of("hr", "human resources", "nhan su", "tuyen dung"))
    );

    private static final Map<String, String> REVERSE_ALIAS_MAP = buildReverseAliasMap();

    private final ExpertPromptConfigRepository expertPromptConfigRepository;
    private final QuestionBankRepository questionBankRepository;
    private final QuestionBankService questionBankService;

    public SkillResolveServiceImpl(
            ExpertPromptConfigRepository expertPromptConfigRepository,
            QuestionBankRepository questionBankRepository,
            QuestionBankService questionBankService
    ) {
        this.expertPromptConfigRepository = expertPromptConfigRepository;
        this.questionBankRepository = questionBankRepository;
        this.questionBankService = questionBankService;
    }

    @Override
    public SkillResolveResponse resolveSkill(String skillName) {
        return doResolve(skillName, false);
    }

    @Override
    public SkillResolveResponse resolveAndCreateQuestionBank(String skillName) {
        return doResolve(skillName, true);
    }

    private SkillResolveResponse doResolve(String skillName, boolean autoCreate) {
        String normalizedSkill = SkillNameUtils.normalizeRequired(skillName);
        List<ExpertPromptConfig> configs = expertPromptConfigRepository
                .findByIsActiveTrueOrderByDomainAscIndustryAscJobRoleAsc();

        if (configs.isEmpty()) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "No expert prompt configs found in database");
        }

        List<ResolveMatch> matches = rankMatches(skillName, configs);
        ResolveMatch best = matches.getFirst();

        Optional<com.exe.skillverse_backend.question_bank_service.entity.QuestionBank> existingBank =
                questionBankRepository.findByDomainAndIsActiveTrue(best.domain()).stream()
                        .filter(b -> {
                            String bankSkill = b.getSkillName();
                            return bankSkill != null && bankSkill.equalsIgnoreCase(normalizedSkill);
                        })
                        .findFirst();

        SkillResolveResponse.SkillResolveResponseBuilder responseBuilder = SkillResolveResponse.builder()
                .skillName(normalizedSkill)
                .domain(best.domain())
                .industry(best.industry())
                .jobRole(best.jobRole())
                .confidence(best.confidence())
                .reasoning(best.reasoning())
                .alternatives(toAlternatives(matches));

        if (existingBank.isPresent()) {
            responseBuilder
                    .questionBankExists(true)
                    .existingQuestionBankId(existingBank.get().getId())
                    .existingQuestionBankTitle(existingBank.get().getTitle());
        } else {
            responseBuilder.questionBankExists(false);
        }

        if (autoCreate && existingBank.isEmpty() && best.confidence() >= AUTO_CREATE_MIN_CONFIDENCE) {
            try {
                QuestionBankResponse createdBank = questionBankService.createBank(
                        CreateQuestionBankRequest.builder()
                                .domain(best.domain())
                                .skillName(normalizedSkill)
                                .title("Question bank dau vao " + best.jobRole() + " - " + formatSkillLabel(normalizedSkill))
                                .description("Bo cau hoi danh gia dau vao cho skill "
                                        + formatSkillLabel(normalizedSkill) + " thuoc vi tri "
                                        + best.jobRole() + " trong nganh " + best.industry() + ".")
                                .build()
                );
                responseBuilder
                        .createdQuestionBankId(createdBank.getId())
                        .createdQuestionBankTitle(createdBank.getTitle())
                        .questionBankExists(true)
                        .existingQuestionBankId(createdBank.getId())
                        .existingQuestionBankTitle(createdBank.getTitle());
            } catch (Exception ignored) {
                // Keep the resolver usable even when the optional bank creation collides or fails.
            }
        }

        return responseBuilder.build();
    }

    private List<ResolveMatch> rankMatches(String skillName, List<ExpertPromptConfig> configs) {
        String input = normalizeSearchText(skillName);
        Set<String> expandedTerms = expandWithAliases(input);
        List<String> inputTokens = tokenize(input);

        List<ResolveMatch> matches = configs.stream()
                .map(config -> scoreConfig(config, input, expandedTerms, inputTokens))
                .sorted(Comparator
                        .comparingInt(ResolveMatch::confidence).reversed()
                        .thenComparing(ResolveMatch::jobRole)
                        .thenComparing(ResolveMatch::industry)
                        .thenComparing(ResolveMatch::domain))
                .toList();

        if (matches.isEmpty()) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "No skill resolution candidates found");
        }
        return matches;
    }

    private ResolveMatch scoreConfig(
            ExpertPromptConfig config,
            String input,
            Set<String> expandedTerms,
            List<String> inputTokens
    ) {
        List<String> candidateKeywords = new ArrayList<>();
        candidateKeywords.addAll(splitKeywords(config.getKeywords()));
        candidateKeywords.addAll(builtInRoleHints(config));
        candidateKeywords = candidateKeywords.stream()
                .map(SkillResolveServiceImpl::normalizeSearchText)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();

        String roleNorm = normalizeSearchText(config.getJobRole());
        String industryNorm = normalizeSearchText(config.getIndustry());
        String domainNorm = normalizeSearchText(config.getDomain());

        Score score = new Score(0, "No strong signal");

        for (String term : expandedTerms) {
            for (String keyword : candidateKeywords) {
                score.accept(scorePhrase(term, keyword, 98, 88, 72),
                        "Matched keyword '" + keyword + "'");
            }
        }

        for (String token : inputTokens) {
            for (String keyword : candidateKeywords) {
                score.accept(scoreTokenAgainstPhrase(token, keyword, 92),
                        "Matched token '" + token + "' to keyword '" + keyword + "'");
            }
        }

        for (String term : expandedTerms) {
            score.accept(scorePhrase(term, roleNorm, 94, 82, 68),
                    "Matched job role '" + config.getJobRole() + "'");
            for (String roleToken : tokenize(roleNorm)) {
                score.accept(Math.round(fuzzyTokenScore(term, roleToken) * 0.78f),
                        "Matched role token '" + roleToken + "'");
            }
        }

        int tokenCoverage = tokenCoverageScore(inputTokens, candidateKeywords, roleNorm);
        score.accept(tokenCoverage, "Matched multiple skill terms");

        for (String term : expandedTerms) {
            score.accept(Math.round(scorePhrase(term, industryNorm, 55, 46, 35) * 0.9f),
                    "Matched industry '" + config.getIndustry() + "'");
            score.accept(Math.round(scorePhrase(term, domainNorm, 42, 35, 25) * 0.85f),
                    "Matched domain '" + config.getDomain() + "'");
        }

        int confidence = Math.max(20, Math.min(100, score.value()));
        return new ResolveMatch(
                config.getDomain(),
                config.getIndustry(),
                config.getJobRole(),
                confidence,
                buildReasoning(score.reason(), confidence)
        );
    }

    private int tokenCoverageScore(List<String> inputTokens, List<String> candidateKeywords, String roleNorm) {
        if (inputTokens.isEmpty()) {
            return 0;
        }

        List<String> candidateTokens = new ArrayList<>();
        for (String keyword : candidateKeywords) {
            candidateTokens.addAll(tokenize(keyword));
        }
        candidateTokens.addAll(tokenize(roleNorm));

        int total = 0;
        int matched = 0;
        for (String inputToken : inputTokens) {
            int best = 0;
            for (String candidateToken : candidateTokens) {
                best = Math.max(best, fuzzyTokenScore(inputToken, candidateToken));
            }
            if (best >= 55) {
                matched++;
                total += best;
            }
        }

        if (matched == 0) {
            return 0;
        }

        int average = Math.round((float) total / inputTokens.size());
        int coverageBonus = Math.round(20f * matched / inputTokens.size());
        return Math.min(90, average + coverageBonus);
    }

    private static int scorePhrase(String term, String target, int exact, int contains, int fuzzy) {
        if (term.isBlank() || target.isBlank()) {
            return 0;
        }
        if (term.equals(target)) {
            return exact;
        }
        if (term.contains(target) || target.contains(term)) {
            int longer = Math.max(term.length(), target.length());
            int shorter = Math.min(term.length(), target.length());
            return Math.min(contains, Math.round(contains * (0.72f + 0.28f * shorter / longer)));
        }
        return Math.round(fuzzyTokenScore(term, target) * fuzzy / 100f);
    }

    private static int scoreTokenAgainstPhrase(String token, String phrase, int maxScore) {
        int best = fuzzyTokenScore(token, phrase);
        for (String phraseToken : tokenize(phrase)) {
            best = Math.max(best, fuzzyTokenScore(token, phraseToken));
        }
        return Math.round(best * maxScore / 100f);
    }

    private static int fuzzyTokenScore(String a, String b) {
        if (a == null || b == null || a.isBlank() || b.isBlank()) {
            return 0;
        }
        if (a.equals(b)) {
            return 100;
        }
        if (a.contains(b) || b.contains(a)) {
            int longer = Math.max(a.length(), b.length());
            int shorter = Math.min(a.length(), b.length());
            return Math.round(62 + 30f * shorter / longer);
        }

        String strippedA = stripCommonSuffixes(a);
        String strippedB = stripCommonSuffixes(b);
        if (!strippedA.isBlank() && strippedA.equals(strippedB)) {
            return 86;
        }
        if (!strippedA.isBlank() && !strippedB.isBlank()
                && (strippedA.contains(strippedB) || strippedB.contains(strippedA))) {
            return 72;
        }

        if (a.length() <= 18 && b.length() <= 18) {
            int distance = levenshteinDistance(a, b);
            int maxLen = Math.max(a.length(), b.length());
            float similarity = 1f - (float) distance / maxLen;
            if (similarity >= 0.68f) {
                return Math.round(similarity * 72);
            }
        }
        return 0;
    }

    private static String stripCommonSuffixes(String value) {
        return value.replaceAll("(js|lang|framework|developer|dev|engineer|specialist)$", "");
    }

    private static int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }
        return dp[s1.length()][s2.length()];
    }

    private List<SkillResolveResponse.AlternativeMatch> toAlternatives(List<ResolveMatch> matches) {
        List<SkillResolveResponse.AlternativeMatch> alternatives = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 1; i < matches.size() && alternatives.size() < 4; i++) {
            ResolveMatch match = matches.get(i);
            if (match.confidence() < 30) {
                break;
            }
            String key = match.domain() + "|" + match.industry() + "|" + match.jobRole();
            if (seen.add(key)) {
                alternatives.add(SkillResolveResponse.AlternativeMatch.builder()
                        .domain(match.domain())
                        .industry(match.industry())
                        .jobRole(match.jobRole())
                        .confidence(match.confidence())
                        .build());
            }
        }
        return alternatives;
    }

    private static List<String> splitKeywords(String keywords) {
        if (keywords == null || keywords.isBlank()) {
            return List.of();
        }
        return List.of(keywords.split("[,;|]")).stream()
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    private static List<String> builtInRoleHints(ExpertPromptConfig config) {
        String role = normalizeSearchText(config.getJobRole());
        String industry = normalizeSearchText(config.getIndustry());
        List<String> hints = new ArrayList<>();

        if (role.contains("backend")) {
            hints.addAll(List.of("backend", "api", "server", "java", "spring", "spring boot", "node", "express",
                    "nestjs", "django", "flask", "laravel", "php", "c#", "asp.net", "sql", "postgresql",
                    "mysql", "redis", "microservices"));
        }
        if (role.contains("frontend")) {
            hints.addAll(List.of("frontend", "react", "reactjs", "vue", "angular", "nextjs", "javascript",
                    "typescript", "html", "css", "tailwind", "web ui"));
        }
        if (role.contains("fullstack") || role.contains("full stack")) {
            hints.addAll(List.of("fullstack", "full stack", "mern", "mean", "react node", "nextjs", "spring react",
                    "web development"));
        }
        if (role.contains("mobile")) {
            hints.addAll(List.of("mobile", "android", "ios", "flutter", "dart", "react native", "kotlin", "swift"));
        }
        if (role.contains("devops")) {
            hints.addAll(List.of("devops", "ci cd", "docker", "kubernetes", "k8s", "terraform", "jenkins",
                    "github actions"));
        }
        if (role.contains("cloud")) {
            hints.addAll(List.of("cloud", "aws", "azure", "gcp", "google cloud", "cloud architecture",
                    "cloud engineer"));
        }
        if (role.contains("qa") || role.contains("tester")) {
            hints.addAll(List.of("qa", "qc", "testing", "tester", "selenium", "automation test", "manual test",
                    "playwright", "cypress"));
        }
        if (role.contains("ui") || role.contains("ux") || role.contains("designer")) {
            hints.addAll(List.of("ui", "ux", "ui ux", "figma", "wireframe", "prototype", "user experience",
                    "user interface", "photoshop", "illustrator"));
        }
        if (role.contains("data analyst")) {
            hints.addAll(List.of("data analyst", "sql", "excel", "power bi", "powerbi", "tableau", "dashboard",
                    "data visualization"));
        }
        if (role.contains("business intelligence") || role.contains("bi")) {
            hints.addAll(List.of("bi", "business intelligence", "power bi", "powerbi", "tableau", "dashboard",
                    "data warehouse"));
        }
        if (role.contains("data engineer")) {
            hints.addAll(List.of("data engineer", "etl", "spark", "airflow", "data pipeline", "warehouse",
                    "big data"));
        }
        if (role.contains("machine learning") || role.contains("ai engineer")) {
            hints.addAll(List.of("machine learning", "ml", "ai", "python", "tensorflow", "pytorch", "llm",
                    "deep learning", "generative ai"));
        }
        if (role.contains("cyber") || role.contains("security") || role.contains("pentester") || role.contains("soc")) {
            hints.addAll(List.of("security", "cybersecurity", "pentest", "penetration testing", "ethical hacker",
                    "soc", "firewall", "network security", "threat"));
        }
        if (role.contains("marketing") || industry.contains("marketing")) {
            hints.addAll(List.of("marketing", "digital marketing", "seo", "ads", "facebook ads", "google ads",
                    "content marketing", "social media", "email marketing", "brand"));
        }
        if (role.contains("sales")) {
            hints.addAll(List.of("sales", "ban hang", "telesales", "b2b sales", "closing", "crm"));
        }
        if (role.contains("business analyst")) {
            hints.addAll(List.of("business analyst", "ba", "requirements", "process", "user story", "brd"));
        }
        if (role.contains("project manager")) {
            hints.addAll(List.of("project manager", "pm", "pmp", "agile", "scrum", "kanban"));
        }
        if (role.contains("hr") || role.contains("recruitment")) {
            hints.addAll(List.of("hr", "human resources", "recruitment", "talent acquisition", "headhunter",
                    "nhan su", "tuyen dung"));
        }
        if (role.contains("accounting") || role.contains("finance")) {
            hints.addAll(List.of("accounting", "finance", "ke toan", "excel", "financial analysis",
                    "bookkeeping"));
        }
        if (role.contains("logistics") || role.contains("supply chain")) {
            hints.addAll(List.of("logistics", "supply chain", "procurement", "inventory", "import export",
                    "customs"));
        }
        return hints;
    }

    private static Set<String> expandWithAliases(String input) {
        Set<String> result = new LinkedHashSet<>();
        if (!input.isBlank()) {
            result.add(input);
        }
        List<String> tokens = tokenize(input);
        result.addAll(tokens);

        for (String term : new ArrayList<>(result)) {
            String canonical = REVERSE_ALIAS_MAP.get(term);
            if (canonical != null) {
                SKILL_ALIASES.getOrDefault(canonical, List.of()).stream()
                        .map(SkillResolveServiceImpl::normalizeSearchText)
                        .forEach(result::add);
            }
        }
        return result;
    }

    private static Map<String, String> buildReverseAliasMap() {
        Map<String, String> reverse = new LinkedHashMap<>();
        SKILL_ALIASES.forEach((canonical, aliases) -> aliases.forEach(alias ->
                reverse.put(normalizeSearchText(alias), canonical)));
        return reverse;
    }

    private static String normalizeSearchText(String value) {
        if (value == null) {
            return "";
        }
        String noDiacritics = DIACRITICS.matcher(
                Normalizer.normalize(value, Normalizer.Form.NFD)
        ).replaceAll("").replace('đ', 'd').replace('Đ', 'D');
        return NON_SEARCH_CHARS.matcher(SEPARATORS.matcher(noDiacritics.toLowerCase(Locale.ROOT))
                        .replaceAll(" "))
                .replaceAll("")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static List<String> tokenize(String text) {
        String normalized = normalizeSearchText(text);
        if (normalized.isBlank()) {
            return List.of();
        }
        return List.of(normalized.split("\\s+")).stream()
                .filter(s -> !s.isBlank())
                .toList();
    }

    private String buildReasoning(String matchReason, int confidence) {
        if (confidence >= 85) {
            return "Smart search found a strong match. " + matchReason + ".";
        }
        if (confidence >= 60) {
            return "Smart search found a likely match. " + matchReason + ".";
        }
        return "Smart search picked the closest available role. " + matchReason + ".";
    }

    private String formatSkillLabel(String normalizedSkill) {
        if (normalizedSkill == null || normalizedSkill.isBlank()) {
            return "";
        }
        String[] parts = normalizedSkill.toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(" ");
            }
            sb.append(part.substring(0, 1).toUpperCase(Locale.ROOT)).append(part.substring(1));
        }
        return sb.toString();
    }

    private record ResolveMatch(
            String domain,
            String industry,
            String jobRole,
            int confidence,
            String reasoning
    ) {
    }

    private static final class Score {
        private int value;
        private String reason;

        private Score(int value, String reason) {
            this.value = value;
            this.reason = reason;
        }

        private void accept(int candidate, String candidateReason) {
            if (candidate > value) {
                value = candidate;
                reason = candidateReason;
            }
        }

        private int value() {
            return value;
        }

        private String reason() {
            return reason;
        }
    }
}
