package de.tum.in.www1.hephaestus.architecture;

import de.tum.in.www1.hephaestus.Application;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.Violations;

/**
 * Validates the Spring Modulith 2 module structure across the application.
 *
 * <h2>What this gates</h2>
 * <ul>
 *   <li><b>Cyclic dependencies</b> between {@code @ApplicationModule}-annotated packages.</li>
 *   <li><b>Access into non-API package contents</b> of another module.</li>
 * </ul>
 *
 * <h2>Baseline allowlist</h2>
 * <p>This is the first PR that adds explicit {@code @ApplicationModule} annotations (epic #1096).
 * Pre-existing cross-module references that {@link ApplicationModules#verify()} flags get captured in
 * {@code src/test/resources/modulith-violation-baseline.txt}, one violation message per line. Any
 * violation NOT on the baseline fails CI. The architecture epic is responsible for emptying the
 * baseline by either (a) narrowing module {@code allowedDependencies} or (b) restructuring callsites.
 *
 * <p>The baseline file may not exist (clean slate) — that's interpreted as "zero allowed violations".
 */
@Tag("architecture")
@DisplayName("Spring Modulith 2 module verification")
class ApplicationModulesVerificationTest {

    private static final Path BASELINE_PATH = Path.of("src/test/resources/modulith-violation-baseline.txt");

    @Test
    @DisplayName("ApplicationModules.verify() reports no violations outside the baseline allowlist")
    void verifyModuleStructure() throws IOException {
        ApplicationModules modules = ApplicationModules.of(Application.class);

        Violations violations;
        try {
            modules.verify();
            violations = Violations.NONE;
        } catch (Violations v) {
            violations = v;
        }

        Set<String> allowed = loadBaseline();
        Set<String> observed = new LinkedHashSet<>(violations.getMessages());

        Set<String> unexpected = new LinkedHashSet<>(observed);
        unexpected.removeAll(allowed);

        Set<String> stale = new LinkedHashSet<>(allowed);
        stale.removeAll(observed);

        StringBuilder report = new StringBuilder();
        if (!unexpected.isEmpty()) {
            report.append("\nNew Modulith violations not on baseline (").append(unexpected.size()).append("):\n");
            unexpected.forEach(m -> report.append("  + ").append(m).append('\n'));
            report
                .append("\nIf this is intentional, add the line(s) to ")
                .append(BASELINE_PATH)
                .append(" with a comment naming the follow-up issue.\n");
        }
        if (!stale.isEmpty()) {
            report
                .append("\nStale baseline entries (no longer violated — remove from baseline) (")
                .append(stale.size())
                .append("):\n");
            stale.forEach(m -> report.append("  - ").append(m).append('\n'));
        }
        if (report.length() > 0) {
            throw new AssertionError(report.toString());
        }
    }

    private static Set<String> loadBaseline() throws IOException {
        if (!Files.exists(BASELINE_PATH)) {
            return Set.of();
        }
        List<String> lines = Files.readAllLines(BASELINE_PATH);
        Set<String> out = new LinkedHashSet<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            out.add(trimmed);
        }
        return out;
    }
}
