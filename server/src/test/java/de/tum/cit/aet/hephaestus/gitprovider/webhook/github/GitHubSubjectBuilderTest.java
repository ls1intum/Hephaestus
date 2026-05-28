package de.tum.cit.aet.hephaestus.gitprovider.webhook.github;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class GitHubSubjectBuilderTest extends BaseUnitTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode parse(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void buildsRepoSubject() {
        JsonNode payload = parse("{\"repository\":{\"owner\":{\"login\":\"acme\"},\"name\":\"widgets\"}}");
        assertThat(GitHubSubjectBuilder.build(payload, "push")).isEqualTo("github.acme.widgets.push");
    }

    @Test
    void fallsBackToOrganizationLogin() {
        JsonNode payload = parse("{\"organization\":{\"login\":\"acme-org\"}}");
        assertThat(GitHubSubjectBuilder.build(payload, "membership")).isEqualTo("github.acme-org.?.membership");
    }

    @Test
    void placeholdersForMissingOrgAndRepo() {
        assertThat(GitHubSubjectBuilder.build(parse("{}"), "ping")).isEqualTo("github.?.?.ping");
    }

    @Test
    void sanitizesDotsAsTildes() {
        JsonNode payload = parse("{\"repository\":{\"owner\":{\"login\":\"my.org\"},\"name\":\"my.repo\"}}");
        assertThat(GitHubSubjectBuilder.build(payload, "push")).isEqualTo("github.my~org.my~repo.push");
    }

    @Test
    void placeholderForEmptyOrNullEventType() {
        JsonNode payload = parse("{\"repository\":{\"owner\":{\"login\":\"acme\"},\"name\":\"widgets\"}}");
        assertThat(GitHubSubjectBuilder.build(payload, "")).isEqualTo("github.acme.widgets.?");
        assertThat(GitHubSubjectBuilder.build(payload, null)).isEqualTo("github.acme.widgets.?");
    }
}
