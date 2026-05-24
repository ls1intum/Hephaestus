package de.tum.cit.aet.hephaestus.integration.spi;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("IntegrationKind ↔ Family mapping")
class IntegrationKindFamilyTest extends BaseUnitTest {

    @Test
    void everyKindBelongsToAFamily() {
        for (IntegrationKind kind : IntegrationKind.values()) {
            assertThat(kind.family()).as("family for %s", kind).isNotNull();
        }
    }

    @Test
    void githubAndGitlabAreScm() {
        assertThat(IntegrationKind.GITHUB.family()).isEqualTo(IntegrationFamily.Family.SCM);
        assertThat(IntegrationKind.GITLAB.family()).isEqualTo(IntegrationFamily.Family.SCM);
    }

    @Test
    void slackIsMessaging() {
        assertThat(IntegrationKind.SLACK.family()).isEqualTo(IntegrationFamily.Family.MESSAGING);
    }

    @Test
    void outlineIsKnowledge() {
        assertThat(IntegrationKind.OUTLINE.family()).isEqualTo(IntegrationFamily.Family.KNOWLEDGE);
    }
}
