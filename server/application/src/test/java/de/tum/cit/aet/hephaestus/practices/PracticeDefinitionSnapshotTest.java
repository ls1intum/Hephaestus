package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeVersion;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class PracticeDefinitionSnapshotTest extends BaseUnitTest {
    @Test
    void shouldKeepOnlyVersionIdentityAndDigestWhenAnAuditSnapshotIsWritten() {
        var version = mock(PracticeVersion.class);
        when(version.getId()).thenReturn(17L);
        when(version.getReviewDigest()).thenReturn("v6:" + "a".repeat(64));
        var practice = new Practice();
        practice.setSlug("private-script");
        practice.setKind(new ItemKind("scm.pull_request"));
        practice.setCurrentVersion(version);
        var snapshot = PracticeDefinitionSnapshot.of(practice);
        var json = JsonMapper.builder().build().valueToTree(snapshot);
        assertThat(snapshot.versionId()).isEqualTo(17L);
        assertThat(json.propertyNames())
                .containsExactlyInAnyOrder(
                        "slug",
                        "kind",
                        "versionId",
                        "reviewDigest",
                        "groupSlug",
                        "position",
                        "feedbackMode",
                        "catalogSlug",
                        "catalogBaseDigest");
        assertThat(json.path("slug").asString()).isEqualTo("private-script");
        assertThat(json.path("reviewDigest").asString()).isEqualTo(snapshot.reviewDigest());
    }
}
