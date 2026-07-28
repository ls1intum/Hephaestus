package de.tum.cit.aet.hephaestus.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Lints the committed OpenAPI spec: every operation on an {@code /outline} path (which includes the
 * {@code /connections/outline} connection-admin routes) must carry an {@code Outline}-bearing operationId. The
 * generated TypeScript client derives its function names from operationIds in one flat namespace, so an unprefixed
 * id (e.g. a bare {@code listCollections}) collides with the first sibling integration that adds the same verb — a
 * client-breaking rename after ship. Sibling of {@link OpenApiSlackOperationIdLintTest}.
 */
@Tag("unit")
class OpenApiOutlineOperationIdLintTest extends BaseUnitTest {

    private static final Path SPEC = Path.of("openapi.yaml");
    private static final Pattern OPERATION_ID = Pattern.compile("operationId:\\s*(\\S+)");

    @Test
    void everyOutlinePathOperationIdMentionsOutline() throws Exception {
        String spec = Files.readString(SPEC, StandardCharsets.UTF_8);
        List<String> offenders = new ArrayList<>();
        String currentPath = null;
        for (String line : spec.lines().toList()) {
            // paths are the only keys at exactly 4-space indent ending with ':' that start with '/'
            if (line.startsWith("    /") && line.stripTrailing().endsWith(":")) {
                currentPath = line.strip();
                continue;
            }
            // "/outline" also matches the "/connections/outline" connection-admin routes
            if (currentPath == null || !currentPath.contains("/outline")) {
                continue;
            }
            Matcher m = OPERATION_ID.matcher(line);
            if (m.find() && !m.group(1).toLowerCase(Locale.ROOT).contains("outline")) {
                offenders.add(currentPath + " → " + m.group(1));
            }
        }
        assertThat(offenders).as("operations on /outline paths without 'Outline' in their operationId").isEmpty();
    }
}
