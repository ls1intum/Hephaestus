package de.tum.in.www1.hephaestus.architecture;

import de.tum.in.www1.hephaestus.Application;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.Violations;

/**
 * Asserts the Modulith violation set against a checked-in SHA-256 baseline. Each line in
 * {@code modulith-violation-baseline.txt} is the hex digest of one tolerated violation message
 * (Liquibase change-log style: opaque to humans, exact to the test). Any new violation hash
 * fails CI; any stale hash fails CI. The architecture epic shrinks the file as it narrows
 * per-module {@code allowedDependencies}.
 */
@Tag("architecture")
@DisplayName("Spring Modulith 2 module verification")
class ApplicationModulesVerificationTest {

    private static final Path BASELINE = Path.of("src/test/resources/modulith-violation-baseline.txt");

    @Test
    @DisplayName("Modulith violation hashes match the committed baseline exactly")
    void verifyModuleStructure() throws IOException {
        ApplicationModules modules = ApplicationModules.of(Application.class);
        List<String> messages = collectViolations(modules);
        TreeMap<String, String> observed = new TreeMap<>();
        for (String message : messages) {
            observed.put(hash(message), message);
        }
        Set<String> allowed = loadBaseline();

        Set<String> unexpected = new LinkedHashSet<>(observed.keySet());
        unexpected.removeAll(allowed);

        Set<String> stale = new LinkedHashSet<>(allowed);
        stale.removeAll(observed.keySet());

        if (unexpected.isEmpty() && stale.isEmpty()) {
            return;
        }

        StringBuilder report = new StringBuilder("\nModulith baseline drift.\n");
        if (!unexpected.isEmpty()) {
            report.append("\nNEW violations (").append(unexpected.size()).append("):\n");
            unexpected.forEach(h ->
                report.append("  + ").append(h).append("  ").append(preview(observed.get(h))).append('\n')
            );
        }
        if (!stale.isEmpty()) {
            report.append("\nSTALE baseline entries (").append(stale.size()).append("):\n");
            stale.forEach(h -> report.append("  - ").append(h).append('\n'));
        }
        report
            .append("\nUpdate ")
            .append(BASELINE)
            .append(" after intentionally tightening or widening the module graph.\n");
        throw new AssertionError(report.toString());
    }

    private static List<String> collectViolations(ApplicationModules modules) {
        try {
            modules.verify();
            return List.of();
        } catch (Violations v) {
            return v.getMessages();
        }
    }

    private static String hash(String message) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(message.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** First line + truncation to keep CI failure output readable. */
    private static String preview(String message) {
        if (message == null) return "<unknown>";
        int newline = message.indexOf('\n');
        String head = newline >= 0 ? message.substring(0, newline) : message;
        return head.length() > 140 ? head.substring(0, 137) + "..." : head;
    }

    private static Set<String> loadBaseline() throws IOException {
        if (!Files.exists(BASELINE)) {
            return Set.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (String line : Files.readAllLines(BASELINE, StandardCharsets.UTF_8)) {
            String trimmed = line.strip();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                out.add(trimmed);
            }
        }
        return out;
    }
}
