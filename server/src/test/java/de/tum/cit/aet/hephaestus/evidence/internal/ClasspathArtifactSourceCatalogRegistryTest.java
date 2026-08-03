package de.tum.cit.aet.hephaestus.evidence.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import de.tum.cit.aet.hephaestus.evidence.EvidenceProfileId;
import de.tum.cit.aet.hephaestus.evidence.SourceContractVersion;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.evidence.SourceUseBasis;
import de.tum.cit.aet.hephaestus.evidence.SourceUseOutcome;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.HashMap;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Tag("unit")
class ClasspathArtifactSourceCatalogRegistryTest {

    private static final String VERSION_1_CATALOG_SHA256 =
        "484b89a8237376ede7252c5af182e55a7b4161beb4c5e36a0e47e7911b0b3060";

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Test
    void shouldLoadCurrentCatalogAndGovernanceDecisions() {
        var registry = new ClasspathArtifactSourceCatalogRegistry(objectMapper, java.time.Clock.systemUTC());

        assertThat(registry.current().version()).isEqualTo(new SourceContractVersion("1.0.0"));
        assertThat(registry.catalogDigest()).isEqualTo(VERSION_1_CATALOG_SHA256);
        assertThat(registry.current().sources()).hasSize(13);
        assertThat(registry.current().profiles())
            .extracting(profile -> profile.id().value())
            .containsExactlyInAnyOrder("pull-request-review", "issue-review", "conversation-review");
        assertThat(
            registry
                .requireProfile(new SourceContractVersion("1.0.0"), new EvidenceProfileId("pull-request-review"))
                .allowedSources()
        ).contains(new SourceKind("scm.repository.tree"), new SourceKind("scm.pull-request.diff"));
        var repositoryTree = registry.requireSource(
            new SourceContractVersion("1.0.0"),
            new SourceKind("scm.repository.tree")
        );
        assertThat(repositoryTree.completenessPolicy().supportsPartial()).isTrue();
        assertThat(repositoryTree.completenessPolicy().supportsEmpty()).isTrue();
    }

    @Test
    void shouldRejectUnknownVersionKindAndProfile() {
        var registry = new ClasspathArtifactSourceCatalogRegistry(objectMapper, java.time.Clock.systemUTC());

        assertThatIllegalArgumentException().isThrownBy(() ->
            registry.requireSource(new SourceContractVersion("2.0.0"), new SourceKind("scm.repository.tree"))
        );
        assertThatIllegalArgumentException().isThrownBy(() ->
            registry.requireSource(new SourceContractVersion("1.0.0"), new SourceKind("scm.unknown"))
        );
        assertThatIllegalArgumentException().isThrownBy(() ->
            registry.requireProfile(new SourceContractVersion("1.0.0"), new EvidenceProfileId("unknown"))
        );
    }

    @Test
    void shouldRejectUnknownCatalogFields() throws IOException {
        JsonNode catalog = read(ClasspathArtifactSourceCatalogRegistry.CATALOG_RESOURCE).deepCopy();
        ((tools.jackson.databind.node.ObjectNode) catalog).put("futureMeaning", true);

        assertThatIllegalStateException()
            .isThrownBy(() -> ClasspathArtifactSourceCatalogRegistry.parse(catalog))
            .withMessageContaining("Unknown field");
    }

    @Test
    void shouldKeepExistingUseOperationalWhileControllerReviewIsPending() throws IOException {
        var catalog = ClasspathArtifactSourceCatalogRegistry.parse(
            read(ClasspathArtifactSourceCatalogRegistry.CATALOG_RESOURCE)
        );
        var decisions = ClasspathArtifactSourceCatalogRegistry.parseUseDecisions(
            read(ClasspathArtifactSourceCatalogRegistry.USE_DECISIONS_RESOURCE)
        );
        ClasspathArtifactSourceCatalogRegistry.validateUseDecisions(
            catalog,
            decisions,
            Instant.parse("2030-08-03T00:00:00Z")
        );
        assertThat(decisions.values()).allSatisfy(decision -> {
            assertThat(decision.basis()).isEqualTo(SourceUseBasis.ENGINEERING_BASELINE);
            assertThat(decision.outcome()).isEqualTo(SourceUseOutcome.PENDING_CONTROLLER_REVIEW);
            assertThat(decision.reviewer()).isNull();
            assertThat(decision.permitsProductUseAt(Instant.parse("2030-08-03T00:00:00Z"))).isTrue();
        });
    }

    @Test
    void shouldKeepEngineeringBaselineFrozenToPreExistingSources() {
        var registry = new ClasspathArtifactSourceCatalogRegistry(objectMapper, java.time.Clock.systemUTC());

        assertThat(registry.current().sources())
            .filteredOn(source ->
                registry
                    .requireUseDecision(registry.current().version(), source.useDecisionId())
                    .basis()
                    .equals(SourceUseBasis.ENGINEERING_BASELINE)
            )
            .extracting(source -> source.kind().value())
            .containsExactlyInAnyOrder(
                "scm.pull-request.core",
                "scm.pull-request.diff",
                "scm.pull-request.comments",
                "scm.contributor-history",
                "scm.repository.tree",
                "scm.issue.core",
                "scm.issue.comments",
                "slack.conversation.thread",
                "scm.linked-work-items",
                "scm.review-threads",
                "scm.general-review-comments",
                "workspace.project-inventory",
                "outline.documents"
            );
    }

    @Test
    void shouldRejectApprovalMetadataOnEngineeringBaseline() throws IOException {
        JsonNode root = read(ClasspathArtifactSourceCatalogRegistry.USE_DECISIONS_RESOURCE).deepCopy();
        ((tools.jackson.databind.node.ObjectNode) root.path("decisions").get(0)).put("reviewer", "test-reviewer");

        assertThatIllegalArgumentException()
            .isThrownBy(() -> ClasspathArtifactSourceCatalogRegistry.parseUseDecisions(root))
            .withMessageContaining("must not contain approval metadata");
    }

    @Test
    void shouldRejectExpiredControllerDecision() throws IOException {
        JsonNode root = read(ClasspathArtifactSourceCatalogRegistry.USE_DECISIONS_RESOURCE).deepCopy();
        var decision = (tools.jackson.databind.node.ObjectNode) root.path("decisions").get(0);
        decision.put("basis", "CONTROLLER_DECISION");
        decision.put("outcome", "APPROVED");
        decision.put("reviewer", "test-reviewer");
        decision.put("decidedAt", "2026-08-03T00:00:00Z");
        decision.put("expiresAt", "2027-08-03T00:00:00Z");
        var catalog = ClasspathArtifactSourceCatalogRegistry.parse(
            read(ClasspathArtifactSourceCatalogRegistry.CATALOG_RESOURCE)
        );
        var decisions = ClasspathArtifactSourceCatalogRegistry.parseUseDecisions(root);

        assertThatIllegalStateException()
            .isThrownBy(() ->
                ClasspathArtifactSourceCatalogRegistry.validateUseDecisions(
                    catalog,
                    decisions,
                    Instant.parse("2027-08-03T00:00:00Z")
                )
            )
            .withMessageContaining("not current");
    }

    @Test
    void shouldRejectMissingUseDecision() throws IOException {
        var catalog = ClasspathArtifactSourceCatalogRegistry.parse(
            read(ClasspathArtifactSourceCatalogRegistry.CATALOG_RESOURCE)
        );
        var decisions = new HashMap<>(
            ClasspathArtifactSourceCatalogRegistry.parseUseDecisions(
                read(ClasspathArtifactSourceCatalogRegistry.USE_DECISIONS_RESOURCE)
            )
        );
        decisions.remove("use-scm-repository-tree");

        assertThatIllegalStateException()
            .isThrownBy(() ->
                ClasspathArtifactSourceCatalogRegistry.validateUseDecisions(
                    catalog,
                    decisions,
                    Instant.parse("2026-08-03T12:00:00Z")
                )
            )
            .withMessageContaining("Missing source-use decision");
    }

    @Test
    void shouldKeepMachineReadableSchemasVersionedAndClosed() throws IOException {
        for (String name : new String[] {
            "artifact-source-catalog.schema.json",
            "artifact-source-manifest.schema.json",
            "practice-evidence-declaration.schema.json",
            "practice-readiness-report.schema.json",
            "source-use-decisions.schema.json",
        }) {
            JsonNode schema = read("contracts/artifact-source/1.0.0/" + name);
            assertThat(schema.path("$schema").asString()).isEqualTo("https://json-schema.org/draft/2020-12/schema");
            assertThat(schema.path("$id").asString()).contains("/1.0.0/");
            assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
        }
    }

    @Test
    void shouldAllowCaptureFactsWithoutAWatermarkOrImmutableIdentity() throws IOException {
        JsonNode factsSchema = read("contracts/artifact-source/1.0.0/artifact-source-manifest.schema.json")
            .path("$defs")
            .path("facts");

        assertThat(factsSchema.has("anyOf")).isFalse();
        assertThat(factsSchema.path("required").toString()).doesNotContain(
            "sourceEffectiveAt",
            "observedAt",
            "immutableIdentity"
        );
    }

    private JsonNode read(String resource) throws IOException {
        try (InputStream input = new ClassPathResource(resource).getInputStream()) {
            return objectMapper.readTree(input);
        }
    }
}
