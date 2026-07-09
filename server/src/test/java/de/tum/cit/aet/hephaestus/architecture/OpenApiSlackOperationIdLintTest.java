package de.tum.cit.aet.hephaestus.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Lints the committed OpenAPI spec: every operation on a {@code /slack} path must carry a {@code Slack}-prefixed
 * operationId. The generated TypeScript client derives its function names from operationIds in one flat namespace,
 * so an unprefixed id (e.g. a bare {@code sendTestMessage}) collides with the first sibling integration that adds
 * the same verb — a client-breaking rename after ship.
 */
@Tag("unit")
class OpenApiSlackOperationIdLintTest extends BaseUnitTest {

    private static final Path SPEC = Path.of("openapi.yaml");
    private static final Pattern OPERATION_ID = Pattern.compile("operationId:\\s*(\\S+)");

    @Test
    void everySlackPathOperationIdMentionsSlack() throws Exception {
        String spec = Files.readString(SPEC, StandardCharsets.UTF_8);
        List<String> offenders = new ArrayList<>();
        String currentPath = null;
        for (String line : spec.lines().toList()) {
            // paths are the only keys at exactly 4-space indent ending with ':' that start with '/'
            if (line.startsWith("    /") && line.stripTrailing().endsWith(":")) {
                currentPath = line.strip();
                continue;
            }
            if (currentPath == null || !currentPath.contains("/slack")) {
                continue;
            }
            Matcher m = OPERATION_ID.matcher(line);
            if (m.find() && !m.group(1).toLowerCase(java.util.Locale.ROOT).contains("slack")) {
                offenders.add(currentPath + " → " + m.group(1));
            }
        }
        assertThat(offenders).as("operations on /slack paths without 'Slack' in their operationId").isEmpty();
    }
}
