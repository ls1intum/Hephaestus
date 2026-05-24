package de.tum.cit.aet.hephaestus.integration.github.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GithubSubjectKeyDeriver four-segment subject + dedup key derivation")
class GithubSubjectKeyDeriverTest extends BaseUnitTest {

    private final GithubSubjectKeyDeriver deriver = new GithubSubjectKeyDeriver();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void buildsRepositorySubject() throws Exception {
        JsonNode payload = objectMapper.readTree(
            "{\"repository\":{\"name\":\"web\",\"owner\":{\"login\":\"acme\"}}}"
        );
        String subject = deriver.deriveSubject(payload, Map.of("X-GitHub-Event", "pull_request"));

        assertThat(subject).isEqualTo("github.acme.web.pull_request");
    }

    @Test
    void buildsOrganizationSubjectWhenNoRepository() throws Exception {
        JsonNode payload = objectMapper.readTree("{\"organization\":{\"login\":\"acme\"}}");
        String subject = deriver.deriveSubject(payload, Map.of("X-GitHub-Event", "member"));

        assertThat(subject).isEqualTo("github.acme.?.member");
    }

    @Test
    void buildsInstallationSubjectWhenNeitherPresent() throws Exception {
        JsonNode payload = objectMapper.readTree("{}");
        String subject = deriver.deriveSubject(payload, Map.of("X-GitHub-Event", "installation"));

        assertThat(subject).isEqualTo("github.?.?.installation");
    }

    @Test
    void sanitizesDotsInTokens() throws Exception {
        JsonNode payload = objectMapper.readTree(
            "{\"repository\":{\"name\":\"my.repo\",\"owner\":{\"login\":\"team.x\"}}}"
        );
        String subject = deriver.deriveSubject(payload, Map.of("X-GitHub-Event", "push"));

        assertThat(subject).isEqualTo("github.team~x.my~repo.push");
    }

    @Test
    void missingEventHeaderBecomesPlaceholder() throws Exception {
        JsonNode payload = objectMapper.readTree(
            "{\"repository\":{\"name\":\"web\",\"owner\":{\"login\":\"acme\"}}}"
        );
        String subject = deriver.deriveSubject(payload, Map.of());

        assertThat(subject).isEqualTo("github.acme.web.?");
    }

    @Test
    void headerLookupIsCaseInsensitive() throws Exception {
        JsonNode payload = objectMapper.readTree(
            "{\"repository\":{\"name\":\"web\",\"owner\":{\"login\":\"acme\"}}}"
        );
        String subject = deriver.deriveSubject(payload, Map.of("x-github-event", "ping"));

        assertThat(subject).isEqualTo("github.acme.web.ping");
    }

    @Test
    void dedupKeyUsesDeliveryHeaderWhenPresent() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        String key = deriver.deriveDedupKey(body, Map.of("X-GitHub-Delivery", "00000000-1111-2222-3333-444444444444"));

        assertThat(key).isEqualTo("github-00000000-1111-2222-3333-444444444444");
    }

    @Test
    void dedupKeyFallsBackToSha256WhenDeliveryMissing() {
        byte[] body = "{\"hello\":\"world\"}".getBytes(StandardCharsets.UTF_8);
        String key = deriver.deriveDedupKey(body, Map.of("X-GitHub-Event", "push"));

        assertThat(key).startsWith("github-");
        // Suffix is SHA-256 truncated to 32 hex characters.
        assertThat(key.substring("github-".length())).hasSize(32).matches("[0-9a-f]+");
    }

    @Test
    void dedupKeyIsDeterministicWhenFallingBack() {
        byte[] body = "{\"hello\":\"world\"}".getBytes(StandardCharsets.UTF_8);
        Map<String, String> headers = Map.of("X-GitHub-Event", "push");

        assertThat(deriver.deriveDedupKey(body, headers)).isEqualTo(deriver.deriveDedupKey(body, headers));
    }

    @Test
    void deriverIdentifiesAsGithubKind() {
        assertThat(deriver.kind()).isEqualTo(IntegrationKind.GITHUB);
    }
}
