package de.tum.cit.aet.hephaestus.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.EvaluationResult;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditSnapshot;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Fixtures for {@link ConfigAuditSnapshotArchTest}'s secret detection.
 *
 * <p>The rule guards an append-only table, so a secret it fails to catch cannot be edited out
 * afterwards. These pin what it must catch — including a secret one level down: a snapshot is
 * serialized whole.
 */
@Tag("architecture")
class ConfigAuditSnapshotSecretDetectionTest {

    record Gateway(String apiKey) {}

    record NestedSecretSnapshot(String name, Gateway gateway) implements ConfigAuditSnapshot {}

    record TopLevelSecretSnapshot(String apiKey) implements ConfigAuditSnapshot {}

    record PresenceFlagSnapshot(String modelName, boolean llmApiKeySet) implements ConfigAuditSnapshot {}

    @Test
    void catchesASecretNestedInsideAnotherRecord() {
        // Neither the component name (`gateway`) nor the record name trips the deny-list, so only the
        // recursion can catch this — deleting it must turn the test red.
        assertThat(violationsFor(NestedSecretSnapshot.class))
            .as("the snapshot is serialized whole, so a nested secret reaches the row just the same")
            .anySatisfy(violation -> assertThat(violation).contains("gateway.apiKey"));
    }

    @Test
    void catchesATopLevelSecret() {
        assertThat(violationsFor(TopLevelSecretSnapshot.class)).isNotEmpty();
    }

    @Test
    void allowsAPresenceFlag() {
        assertThat(violationsFor(PresenceFlagSnapshot.class))
            .as("recording that a key exists is the sanctioned alternative to recording the key")
            .isEmpty();
    }

    private static java.util.List<String> violationsFor(Class<?> snapshot) {
        var imported = new ClassFileImporter().importClasses(snapshot, Gateway.class);
        EvaluationResult result = ConfigAuditSnapshotArchTest.secretLikeComponentRule().evaluate(imported);
        return result.getFailureReport().getDetails();
    }
}
