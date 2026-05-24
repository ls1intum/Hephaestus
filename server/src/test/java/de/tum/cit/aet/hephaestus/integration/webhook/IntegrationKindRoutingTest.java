package de.tum.cit.aet.hephaestus.integration.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("IntegrationKindRouting allow-list")
class IntegrationKindRoutingTest extends BaseUnitTest {

    private final IntegrationKindRouting routing = new IntegrationKindRouting();

    @Test
    void resolvesLowercaseKnownKinds() {
        assertThat(routing.resolve("github")).contains(IntegrationKind.GITHUB);
        assertThat(routing.resolve("gitlab")).contains(IntegrationKind.GITLAB);
        assertThat(routing.resolve("slack")).contains(IntegrationKind.SLACK);
        assertThat(routing.resolve("outline")).contains(IntegrationKind.OUTLINE);
    }

    @Test
    void resolvesIsCaseInsensitive() {
        assertThat(routing.resolve("GITHUB")).contains(IntegrationKind.GITHUB);
        assertThat(routing.resolve("GitLab")).contains(IntegrationKind.GITLAB);
    }

    @Test
    void unknownPathSegmentReturnsEmpty() {
        // Rule: the path is attacker-controlled; never feed it to IntegrationKind.valueOf.
        assertThat(routing.resolve("bitbucket")).isEmpty();
        assertThat(routing.resolve("../../etc/passwd")).isEmpty();
        assertThat(routing.resolve("GITHUB; DROP TABLE")).isEmpty();
        assertThat(routing.resolve("")).isEmpty();
        assertThat(routing.resolve(null)).isEmpty();
    }
}
