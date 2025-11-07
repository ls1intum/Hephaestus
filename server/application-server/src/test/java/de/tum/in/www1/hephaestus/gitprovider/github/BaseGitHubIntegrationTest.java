package de.tum.in.www1.hephaestus.gitprovider.github;

import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base class for GitHub integration tests. Runs with the {@code github-integration}
 * profile. Each test automatically checks that real credentials have been provided
 * and gets skipped otherwise, which keeps the CI pipeline green while enabling
 * developers to run the suite locally.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles(value = "github-integration", inheritProfiles = true)
@Execution(ExecutionMode.SAME_THREAD)
@Tag("github-integration")
public abstract class BaseGitHubIntegrationTest extends BaseIntegrationTest {

    private static final DateTimeFormatter SUFFIX_FORMATTER = DateTimeFormatter.ofPattern(
        "yyyyMMdd-HHmmss-SSS"
    ).withZone(ZoneOffset.UTC);

    @Autowired
    private Environment environment;

    @Autowired
    private org.springframework.core.io.ResourceLoader resourceLoader;

    @BeforeEach
    void requireGitHubCredentials() {
        assumeGitHubCredentialsConfigured();
    }

    protected void assumeGitHubCredentialsConfigured() {
        long appId = environment.getProperty("github.app.id", Long.class, 0L);
        var privateKey = environment.getProperty("github.app.privateKey", "");
        var privateKeyLocation = environment.getProperty("github.app.privateKeyLocation", "");
        var authToken = environment.getProperty("github.meta.auth-token", "");
        var installationId = environment.getProperty("integration-tests.github.installation-id", "");
        boolean missingKeyMaterial = privateKey.isBlank();
        if (missingKeyMaterial && !privateKeyLocation.isBlank()) {
            var resource = resourceLoader.getResource(privateKeyLocation);
            try (var input = resource.exists() ? resource.getInputStream() : null) {
                missingKeyMaterial = input == null || input.read() == -1;
            } catch (Exception ignored) {
                missingKeyMaterial = true;
            }
        }
        boolean missingToken = authToken.isBlank();
        boolean missingInstallation = installationId.isBlank();
        boolean missingAppId = appId <= 0;

        Assumptions.assumeFalse(
            missingAppId || missingKeyMaterial || missingToken || missingInstallation,
            () ->
                "GitHub integration credentials missing. Copy the example file to " +
                "application-github-integration-local.yml (kept out of VCS) or provide " +
                "environment overrides."
        );
    }

    protected String githubOrganization() {
        return environment.getProperty("integration-tests.github.organization", "");
    }

    protected long githubInstallationId() {
        var raw = environment.getProperty("integration-tests.github.installation-id", "");
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("GitHub installation id not configured for integration tests.");
        }
        return Long.parseLong(raw);
    }

    protected String sandboxRepository() {
        return environment.getProperty("integration-tests.github.sandbox-repository", "");
    }

    protected String nextEphemeralSlug(String suffix) {
        var prefix = environment.getProperty("integration-tests.github.ephemeral-prefix", "hephaestus-it");
        var timestamp = SUFFIX_FORMATTER.format(Instant.now());
        return (prefix + "-" + timestamp + (suffix == null || suffix.isBlank() ? "" : "-" + suffix)).toLowerCase();
    }
}
