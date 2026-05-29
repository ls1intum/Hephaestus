package de.tum.cit.aet.hephaestus.integration.scm.gitlab.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class GitlabSubjectKeyDeriverTest extends BaseUnitTest {

    private final GitlabSubjectKeyDeriver deriver = new GitlabSubjectKeyDeriver();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private JsonNode json(String raw) throws Exception {
        return objectMapper.readTree(raw);
    }

    @Test
    void buildsMergeRequestSubjectFromProjectPath() throws Exception {
        JsonNode payload = json(
            "{\"object_kind\":\"merge_request\",\"project\":{\"path_with_namespace\":\"group/web\"}}"
        );
        assertThat(deriver.deriveSubject(payload, Map.of())).isEqualTo("gitlab.group.web.merge_request");
    }

    @Test
    void joinsNestedGroupsWithTilde() throws Exception {
        JsonNode payload = json(
            "{\"object_kind\":\"push\",\"project\":{\"path_with_namespace\":\"group/sub/project\"}}"
        );
        assertThat(deriver.deriveSubject(payload, Map.of())).isEqualTo("gitlab.group~sub.project.push");
    }

    /**
     * Regression guard for the producer/consumer case-folding asymmetry: GitLab paths are
     * case-sensitive and may contain uppercase. The consumer filter
     * (ConsumerSubjectMath#buildSubjectPrefix) is case-preserving, so the deriver must be too —
     * lowercasing the path here silently drops every event for a mixed-case GitLab project.
     */
    @Test
    void preservesPathCase() throws Exception {
        JsonNode payload = json(
            "{\"object_kind\":\"merge_request\",\"project\":{\"path_with_namespace\":\"MyGroup/My-Project\"}}"
        );
        assertThat(deriver.deriveSubject(payload, Map.of())).isEqualTo("gitlab.MyGroup.My-Project.merge_request");
    }

    @Test
    void sanitizesDotsInPathSegments() throws Exception {
        JsonNode payload = json("{\"object_kind\":\"push\",\"project\":{\"path_with_namespace\":\"group/my.repo\"}}");
        assertThat(deriver.deriveSubject(payload, Map.of())).isEqualTo("gitlab.group.my~repo.push");
    }

    @Test
    void lowercasesEventTokenOnly() throws Exception {
        JsonNode payload = json("{\"object_kind\":\"Push\",\"project\":{\"path_with_namespace\":\"Group/Repo\"}}");
        // Event is lowercased (Locale.ROOT) but the path keeps its case.
        assertThat(deriver.deriveSubject(payload, Map.of())).isEqualTo("gitlab.Group.Repo.push");
    }

    @Test
    void fallsBackToEventNameWhenObjectKindMissing() throws Exception {
        JsonNode payload = json("{\"event_name\":\"push\",\"project\":{\"path_with_namespace\":\"group/web\"}}");
        assertThat(deriver.deriveSubject(payload, Map.of())).isEqualTo("gitlab.group.web.push");
    }

    @Test
    void fallsBackToTopLevelPathWhenProjectMissing() throws Exception {
        JsonNode payload = json("{\"object_kind\":\"issue\",\"path_with_namespace\":\"group/web\"}");
        assertThat(deriver.deriveSubject(payload, Map.of())).isEqualTo("gitlab.group.web.issue");
    }

    @Test
    void singleSegmentPathLeavesProjectAsPlaceholder() throws Exception {
        JsonNode payload = json("{\"object_kind\":\"push\",\"project\":{\"path_with_namespace\":\"group\"}}");
        assertThat(deriver.deriveSubject(payload, Map.of())).isEqualTo("gitlab.group.?.push");
    }

    @Test
    void placeholdersWhenNoPathOrEvent() throws Exception {
        assertThat(deriver.deriveSubject(json("{}"), Map.of())).isEqualTo("gitlab.?.?.unknown");
    }

    @Test
    void dedupKeyPrefersIdempotencyHeader() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        String key = deriver.deriveDedupKey(body, Map.of("Idempotency-Key", "abc-123"));
        assertThat(key).isEqualTo("gitlab-abc-123");
    }

    @Test
    void dedupKeyUsesEventUuidWhenNoIdempotencyKey() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        String key = deriver.deriveDedupKey(
            body,
            Map.of("X-Gitlab-Event-UUID", "11111111-2222-3333-4444-555555555555")
        );
        assertThat(key).isEqualTo("gitlab-11111111-2222-3333-4444-555555555555");
    }

    @Test
    void dedupKeyFallsBackToTruncatedSha256() {
        byte[] body = "{\"hello\":\"world\"}".getBytes(StandardCharsets.UTF_8);
        String key = deriver.deriveDedupKey(body, Map.of("X-Gitlab-Event", "Push Hook"));

        assertThat(key).startsWith("gitlab-");
        assertThat(key.substring("gitlab-".length())).hasSize(32).matches("[0-9a-f]+");
    }

    @Test
    void dedupKeyIsDeterministicWhenFallingBack() {
        byte[] body = "{\"hello\":\"world\"}".getBytes(StandardCharsets.UTF_8);
        Map<String, String> headers = Map.of("X-Gitlab-Event", "Push Hook");
        assertThat(deriver.deriveDedupKey(body, headers)).isEqualTo(deriver.deriveDedupKey(body, headers));
    }

    @Test
    void deriverIdentifiesAsGitlabKind() {
        assertThat(deriver.kind()).isEqualTo(IntegrationKind.GITLAB);
    }
}
