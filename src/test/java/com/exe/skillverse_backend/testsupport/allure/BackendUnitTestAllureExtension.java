package com.exe.skillverse_backend.testsupport.allure;

import io.qameta.allure.Allure;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class BackendUnitTestAllureExtension
        implements BeforeAllCallback, BeforeEachCallback, AfterTestExecutionCallback {

    private static final AtomicBoolean METADATA_WRITTEN = new AtomicBoolean(false);
    private static final Pattern CAMEL_CASE_BOUNDARY = Pattern.compile("(?<=[a-z0-9])(?=[A-Z])");
    private static final Set<String> LAYER_SEGMENTS = Set.of(
            "controller",
            "service",
            "impl",
            "mapper",
            "policy",
            "validation");

    @Override
    public void beforeAll(ExtensionContext context) {
        if (!METADATA_WRITTEN.compareAndSet(false, true)) {
            return;
        }

        try {
            Path resultsDirectory = resolveResultsDirectory();
            Files.createDirectories(resultsDirectory);
            writeEnvironmentProperties(resultsDirectory);
            writeExecutorMetadata(resultsDirectory);
            writeCategories(resultsDirectory);
        } catch (IOException ex) {
            context.publishReportEntry("allure.metadata.error", ex.getMessage());
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        TestMetadata metadata = buildMetadata(context);

        Allure.label("parentSuite", "Backend Unit Tests");
        Allure.label("suite", metadata.module());
        Allure.label("subSuite", metadata.layer());
        Allure.label("epic", metadata.module());
        Allure.label("feature", metadata.layer());
        Allure.label("story", metadata.story());
        Allure.parameter("package", metadata.packageName());
        Allure.parameter("testClass", metadata.className());
        Allure.parameter("testMethod", metadata.methodName());

        if (!context.getTags().isEmpty()) {
            Allure.parameter("tags", String.join(", ", new TreeSet<>(context.getTags())));
        }

        Allure.description(String.format(
                Locale.ROOT,
                "Auto-generated metadata for `%s#%s` in `%s`.",
                metadata.className(),
                metadata.methodName(),
                metadata.packageName()));
    }

    @Override
    public void afterTestExecution(ExtensionContext context) {
        context.getExecutionException().ifPresent(error -> Allure.addAttachment(
                "Failure diagnostics",
                "text/plain",
                buildFailureDiagnostics(context, error),
                ".txt"));
    }

    private static TestMetadata buildMetadata(ExtensionContext context) {
        Class<?> testClass = context.getRequiredTestClass();
        String packageName = testClass.getPackageName();
        String[] parts = packageName.split("\\.");
        int anchor = findAnchor(parts, "skillverse_backend");

        String moduleSegment = anchor >= 0 && anchor + 1 < parts.length ? parts[anchor + 1] : "backend";
        String layerSegment = anchor >= 0 && anchor + 2 < parts.length ? parts[anchor + 2] : "tests";

        if (!LAYER_SEGMENTS.contains(layerSegment)) {
            layerSegment = "tests";
        }

        String module = humanizeSegment(moduleSegment);
        String layer = switch (layerSegment) {
            case "controller" -> "Controller Tests";
            case "service" -> "Service Tests";
            case "impl" -> "Implementation Tests";
            case "mapper" -> "Mapper Tests";
            case "policy" -> "Policy Tests";
            case "validation" -> "Validation Tests";
            default -> "Unit Tests";
        };

        String className = testClass.getSimpleName().replace('$', '.');
        String methodName = context.getRequiredTestMethod().getName();
        return new TestMetadata(packageName, module, layer, className, methodName);
    }

    private static String buildFailureDiagnostics(ExtensionContext context, Throwable error) {
        TestMetadata metadata = buildMetadata(context);
        StringBuilder builder = new StringBuilder();
        builder.append("Display name: ").append(context.getDisplayName()).append('\n');
        builder.append("Module: ").append(metadata.module()).append('\n');
        builder.append("Layer: ").append(metadata.layer()).append('\n');
        builder.append("Package: ").append(metadata.packageName()).append('\n');
        builder.append("Class: ").append(metadata.className()).append('\n');
        builder.append("Method: ").append(metadata.methodName()).append('\n');
        builder.append("Tags: ").append(context.getTags().isEmpty() ? "<none>" : String.join(", ", context.getTags()))
                .append('\n');
        builder.append("Exception type: ").append(error.getClass().getName()).append('\n');
        builder.append("Message: ").append(error.getMessage() == null ? "<none>" : error.getMessage()).append('\n');
        return builder.toString();
    }

    private static Path resolveResultsDirectory() {
        String configured = System.getProperty("allure.results.directory", "target/allure-results");
        Path path = Paths.get(configured);
        if (path.isAbsolute()) {
            return path;
        }
        return Paths.get(System.getProperty("user.dir")).resolve(path).normalize();
    }

    private static void writeEnvironmentProperties(Path resultsDirectory) throws IOException {
        String generatedAt = OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        StringBuilder builder = new StringBuilder();
        builder.append("project.name=SkillVerse_BackEnd").append('\n');
        builder.append("test.scope=Backend unit tests").append('\n');
        builder.append("test.framework=JUnit 5 / Spring Boot Test / Mockito").append('\n');
        builder.append("build.tool=Maven").append('\n');
        builder.append("active.profile=").append(System.getProperty("spring.profiles.active", "test")).append('\n');
        builder.append("java.version=").append(System.getProperty("java.version")).append('\n');
        builder.append("java.vendor=").append(System.getProperty("java.vendor")).append('\n');
        builder.append("os.name=").append(System.getProperty("os.name")).append('\n');
        builder.append("os.arch=").append(System.getProperty("os.arch")).append('\n');
        builder.append("results.directory=").append(resultsDirectory.toString()).append('\n');
        builder.append("generated.at.utc=").append(generatedAt).append('\n');
        writeFile(resultsDirectory.resolve("environment.properties"), builder.toString());
    }

    private static void writeExecutorMetadata(Path resultsDirectory) throws IOException {
        String hostname = safeHostname();
        long buildOrder = System.currentTimeMillis();
        String json = """
                {
                  "name": "Maven Surefire",
                  "type": "maven",
                  "reportName": "SkillVerse Backend Unit Tests",
                  "buildName": "SkillVerse_BackEnd unit tests",
                  "buildUrl": "./mvnw test allure:report",
                  "buildOrder": %d,
                  "executorInfo": {
                    "host": "%s",
                    "resultsDirectory": "%s"
                  }
                }
                """
                .formatted(buildOrder, escapeJson(hostname), escapeJson(resultsDirectory.toString()));
        writeFile(resultsDirectory.resolve("executor.json"), json);
    }

    private static void writeCategories(Path resultsDirectory) throws IOException {
        String json = """
                [
                  {
                    "name": "Assertion failures",
                    "matchedStatuses": ["failed"],
                    "traceRegex": ".*(AssertionError|ComparisonFailure|org\\\\.opentest4j\\\\..*).*"
                  },
                  {
                    "name": "Mockito verification and stubbing issues",
                    "matchedStatuses": ["failed", "broken"],
                    "traceRegex": ".*org\\\\.mockito\\\\.exceptions\\\\..*"
                  },
                  {
                    "name": "Access control and authorization failures",
                    "matchedStatuses": ["failed", "broken"],
                    "messageRegex": ".*(Forbidden|Unauthorized|AccessDenied|not authorized|not permitted|ownership).*"
                  },
                  {
                    "name": "Validation and business rule failures",
                    "matchedStatuses": ["failed", "broken"],
                    "messageRegex": ".*(BadRequest|Conflict|validation|invalid|duplicate|not found|must be|cannot).*"
                  },
                  {
                    "name": "Infrastructure and external dependency failures",
                    "matchedStatuses": ["broken"],
                    "traceRegex": ".*(IOException|RestClientException|MailException|SQLException|TimeoutException).*"
                  }
                ]
                """;
        writeFile(resultsDirectory.resolve("categories.json"), json);
    }

    private static void writeFile(Path path, String content) throws IOException {
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private static int findAnchor(String[] parts, String anchor) {
        for (int i = 0; i < parts.length; i++) {
            if (anchor.equals(parts[i])) {
                return i;
            }
        }
        return -1;
    }

    private static String humanizeSegment(String value) {
        String normalized = value.replace('-', '_').replace('.', '_');
        String[] tokens = normalized.split("_");
        StringBuilder builder = new StringBuilder();

        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }

            String[] camelParts = CAMEL_CASE_BOUNDARY.split(token);
            for (String camelPart : camelParts) {
                if (camelPart.isBlank()) {
                    continue;
                }

                if (builder.length() > 0) {
                    builder.append(' ');
                }
                builder.append(Character.toUpperCase(camelPart.charAt(0)));
                if (camelPart.length() > 1) {
                    builder.append(camelPart.substring(1).toLowerCase(Locale.ROOT));
                }
            }
        }

        return builder.isEmpty() ? "Backend" : builder.toString();
    }

    private static String safeHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (IOException ex) {
            return "localhost";
        }
    }

    private static String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private record TestMetadata(
            String packageName,
            String module,
            String layer,
            String className,
            String methodName) {

        private String story() {
            return className;
        }
    }
}
