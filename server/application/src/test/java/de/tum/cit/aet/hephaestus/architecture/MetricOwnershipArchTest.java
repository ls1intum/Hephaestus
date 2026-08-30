package de.tum.cit.aet.hephaestus.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("architecture")
class MetricOwnershipArchTest {

    private static final Path PRODUCTION_SOURCES = Path.of("src/main/java/de/tum/cit/aet/hephaestus");
    private static final Pattern PACKAGE = Pattern.compile("package ([A-Za-z0-9_.]+);");
    private static final Pattern LITERAL_BUILDER =
            Pattern.compile("(?:Timer|Counter|Gauge|DistributionSummary)\\.builder\\(\\s*\\\"");
    private static final Pattern METRICS_IMPORT =
            Pattern.compile("import (de\\.tum\\.cit\\.aet\\.hephaestus\\.[A-Za-z0-9_.]+\\."
                    + "(?:ActivityMetrics|AgentMetrics|CoreMetrics|EvidenceMetrics|GithubMetrics|"
                    + "GitlabMetrics|IntegrationCoreMetrics));");

    @Test
    void meterNamesBelongToTheEmittingModule() throws IOException {
        var modules = ModulithVerificationTest.applicationModules();
        List<String> violations = new ArrayList<>();
        try (var paths = Files.walk(PRODUCTION_SOURCES)) {
            for (Path path : paths.filter(candidate -> candidate.toString().endsWith(".java"))
                    .toList()) {
                String source = Files.readString(path);
                if (LITERAL_BUILDER.matcher(source).find()) {
                    violations.add(path + " passes a string literal to a meter builder");
                }

                String sourcePackage = requiredMatch(PACKAGE, source, path);
                Matcher imports = METRICS_IMPORT.matcher(source);
                while (imports.find()) {
                    String metricsType = imports.group(1);
                    String metricsPackage = metricsType.substring(0, metricsType.lastIndexOf('.'));
                    if (!modules.getModuleForPackage(sourcePackage)
                            .equals(modules.getModuleForPackage(metricsPackage))) {
                        violations.add(path + " imports meter names from another application module: " + metricsType);
                    }
                }
            }
        }

        assertThat(violations).as("meter name ownership violations").isEmpty();
    }

    private static String requiredMatch(Pattern pattern, String source, Path path) {
        Matcher matcher = pattern.matcher(source);
        assertThat(matcher.find()).as("package declaration in %s", path).isTrue();
        return matcher.group(1);
    }
}
