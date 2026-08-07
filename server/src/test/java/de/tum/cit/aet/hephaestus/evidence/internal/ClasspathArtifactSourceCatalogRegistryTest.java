package de.tum.cit.aet.hephaestus.evidence.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.evidence.SourceContractVersion;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.evidence.SourceUseBasis;
import de.tum.cit.aet.hephaestus.evidence.SourceUseOutcome;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
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
        "c107ae804d0f25e3072e2899bded13174c2892a7ccaedb88a5bf15ac79b45565";

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Test
    void shouldLoadCurrentCatalogAndGovernanceDecisions() {
        var registry = new ClasspathArtifactSourceCatalogRegistry(objectMapper, java.time.Clock.systemUTC());

        assertThat(registry.current().version()).isEqualTo(new SourceContractVersion("1.0.0"));
        assertThat(registry.catalogDigest()).isEqualTo(VERSION_1_CATALOG_SHA256);
        assertThat(registry.current().sources()).hasSize(12);
        assertThat(registry.requireSourcesFor(new SourceContractVersion("1.0.0"), "scm.pull_request")).contains(
            new SourceKind("scm.repository.tree"),
            new SourceKind("scm.pull-request.diff")
        );
        var repositoryTree = registry.requireSource(
            new SourceContractVersion("1.0.0"),
            new SourceKind("scm.repository.tree")
        );
        assertThat(repositoryTree.displayName()).isEqualTo("Repository files");
        assertThat(repositoryTree.completenessPolicy().supportsPartial()).isTrue();
        assertThat(repositoryTree.completenessPolicy().supportsEmpty()).isTrue();
    }

    @Test
    void shouldRejectUnknownVersionKindAndArtifactKind() {
        var registry = new ClasspathArtifactSourceCatalogRegistry(objectMapper, java.time.Clock.systemUTC());

        assertThatIllegalArgumentException().isThrownBy(() ->
            registry.requireSource(new SourceContractVersion("2.0.0"), new SourceKind("scm.repository.tree"))
        );
        assertThatIllegalArgumentException().isThrownBy(() ->
            registry.requireSource(new SourceContractVersion("1.0.0"), new SourceKind("scm.unknown"))
        );
        assertThatIllegalArgumentException().isThrownBy(() ->
            registry.requireSourcesFor(new SourceContractVersion("1.0.0"), "docs.document")
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
    void shouldRequireTraceableCurrentEngineeringApproval() throws IOException {
        var catalog = ClasspathArtifactSourceCatalogRegistry.parse(
            read(ClasspathArtifactSourceCatalogRegistry.CATALOG_RESOURCE)
        );
        var decisions = ClasspathArtifactSourceCatalogRegistry.parseUseDecisions(
            read(ClasspathArtifactSourceCatalogRegistry.USE_DECISIONS_RESOURCE)
        );
        ClasspathArtifactSourceCatalogRegistry.validateUseDecisions(catalog, decisions);
        assertThat(decisions.values()).allSatisfy(decision -> {
            assertThat(decision.basis()).isEqualTo(SourceUseBasis.ENGINEERING_BASELINE);
            assertThat(decision.outcome()).isEqualTo(SourceUseOutcome.ENGINEERING_APPROVED);
            assertThat(decision.reviewer()).isNotBlank();
            assertThat(decision.decidedAt()).isNotNull();
            assertThat(decision.expiresAt()).isNotNull();
            SourceUsePurpose purpose = decision.purpose();
            assertThat(decision.permitsAt(Instant.parse("2026-08-03T12:00:00Z"), purpose)).isTrue();
        });
    }

    @Test
    void shouldKeepEngineeringBaselineFrozenToPreExistingSources() {
        var registry = new ClasspathArtifactSourceCatalogRegistry(objectMapper, java.time.Clock.systemUTC());

        assertThat(registry.current().sources())
            .filteredOn(source ->
                source
                    .useDecisionIds()
                    .stream()
                    .map(id -> registry.requireUseDecision(registry.current().version(), id))
                    .allMatch(decision -> decision.basis().equals(SourceUseBasis.ENGINEERING_BASELINE))
            )
            .extracting(source -> source.kind().value())
            .containsExactlyInAnyOrder(
                "scm.pull-request.core",
                "scm.pull-request.diff",
                "scm.pull-request.comments",
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
    void shouldRejectEngineeringBaselineWithoutApprovalMetadata() throws IOException {
        JsonNode root = read(ClasspathArtifactSourceCatalogRegistry.USE_DECISIONS_RESOURCE).deepCopy();
        ((tools.jackson.databind.node.ObjectNode) root.path("decisions").get(0)).remove("reviewer");

        assertThatIllegalArgumentException()
            .isThrownBy(() -> ClasspathArtifactSourceCatalogRegistry.parseUseDecisions(root))
            .withMessageContaining("requires review metadata");
    }

    @Test
    void shouldRecheckApprovalExpiryWhenASourceIsRequested() {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(Instant.parse("2026-08-03T12:00:00Z"));
        var registry = new ClasspathArtifactSourceCatalogRegistry(objectMapper, clock);
        when(clock.instant()).thenReturn(Instant.parse("2027-08-03T00:00:00Z"));

        assertThat(
            registry.isSourceUsePermitted(
                new SourceContractVersion("1.0.0"),
                new SourceKind("scm.pull-request.diff"),
                SourceUsePurpose.AUTOMATED_PRACTICE_REVIEW
            )
        ).isFalse();
    }

    @Test
    void shouldPermitNonSensitiveSourcesWithoutOperatorConfiguration() {
        var registry = new ClasspathArtifactSourceCatalogRegistry(objectMapper, Clock.systemUTC());
        var version = new SourceContractVersion("1.0.0");

        assertThat(
            registry.isSourceUsePermitted(
                version,
                new SourceKind("scm.pull-request.diff"),
                SourceUsePurpose.AUTOMATED_PRACTICE_REVIEW
            )
        ).isTrue();
        assertThat(
            registry.isSourceUsePermitted(
                version,
                new SourceKind("scm.issue.core"),
                SourceUsePurpose.AUTOMATED_PRACTICE_REVIEW
            )
        ).isTrue();
    }

    @Test
    void shouldEnforceEachApprovedPurpose() throws IOException {
        JsonNode root = read(ClasspathArtifactSourceCatalogRegistry.USE_DECISIONS_RESOURCE).deepCopy();
        var decisions = ClasspathArtifactSourceCatalogRegistry.parseUseDecisions(root);
        var assessment = decisions.get("use-scm-pull-request-core-automated-review");
        var feedback = decisions.get("use-scm-pull-request-core-feedback-delivery");

        assertThat(
            assessment.permitsAt(Instant.parse("2026-08-03T12:00:00Z"), SourceUsePurpose.AUTOMATED_PRACTICE_REVIEW)
        ).isTrue();
        assertThat(
            assessment.permitsAt(Instant.parse("2026-08-03T12:00:00Z"), SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY)
        ).isFalse();
        assertThat(
            feedback.permitsAt(Instant.parse("2026-08-03T12:00:00Z"), SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY)
        ).isTrue();
    }

    @Test
    void shouldKeepExpiredControllerDecisionStructurallyValidButIneligible() throws IOException {
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

        ClasspathArtifactSourceCatalogRegistry.validateUseDecisions(catalog, decisions);
        assertThat(decisions.values()).anySatisfy(expired ->
            assertThat(
                expired.permitsAt(Instant.parse("2027-08-03T00:00:00Z"), SourceUsePurpose.AUTOMATED_PRACTICE_REVIEW)
            ).isFalse()
        );
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
        decisions.remove("use-scm-repository-tree-automated-review");

        assertThatIllegalStateException()
            .isThrownBy(() -> ClasspathArtifactSourceCatalogRegistry.validateUseDecisions(catalog, decisions))
            .withMessageContaining("must match the catalog exactly");
    }

    /**
     * A source whose decisions leave one product purpose unapproved is refused, even though every
     * decision it does name exists and nothing is orphaned.
     *
     * <p>The two checks either side of this one both fail on a mismatched id set, which is the easy
     * mistake. This is the quiet one: the catalog and the decisions agree with each other and disagree
     * with the product, so a source reaches a purpose it was never approved for.
     */
    @Test
    void shouldRejectASourceThatLacksADecisionForOnePurpose() throws IOException {
        JsonNode catalogNode = read(ClasspathArtifactSourceCatalogRegistry.CATALOG_RESOURCE).deepCopy();
        var useDecisionIds = (tools.jackson.databind.node.ArrayNode) catalogNode
            .path("sources")
            .get(0)
            .path("useDecisionIds");
        String dropped = useDecisionIds.get(useDecisionIds.size() - 1).asString();
        useDecisionIds.remove(useDecisionIds.size() - 1);
        var catalog = ClasspathArtifactSourceCatalogRegistry.parse(catalogNode);
        var decisions = new HashMap<>(
            ClasspathArtifactSourceCatalogRegistry.parseUseDecisions(
                read(ClasspathArtifactSourceCatalogRegistry.USE_DECISIONS_RESOURCE)
            )
        );
        decisions.remove(dropped);

        assertThatIllegalStateException()
            .isThrownBy(() -> ClasspathArtifactSourceCatalogRegistry.validateUseDecisions(catalog, decisions))
            .withMessageContaining("do not cover every product purpose");
    }

    @Test
    void shouldRejectOrphanUseDecision() throws IOException {
        var catalog = ClasspathArtifactSourceCatalogRegistry.parse(
            read(ClasspathArtifactSourceCatalogRegistry.CATALOG_RESOURCE)
        );
        var decisions = new HashMap<>(
            ClasspathArtifactSourceCatalogRegistry.parseUseDecisions(
                read(ClasspathArtifactSourceCatalogRegistry.USE_DECISIONS_RESOURCE)
            )
        );
        decisions.put("orphan", decisions.values().iterator().next());

        assertThatIllegalStateException()
            .isThrownBy(() -> ClasspathArtifactSourceCatalogRegistry.validateUseDecisions(catalog, decisions))
            .withMessageContaining("must match the catalog exactly");
    }

    @Test
    void shouldKeepMachineReadableSchemasVersionedAndClosed() throws IOException {
        for (String name : new String[] {
            "artifact-source-catalog.schema.json",
            "artifact-source-manifest.schema.json",
            "practice-automated-review-policy.schema.json",
            "automated-review-readiness-report.schema.json",
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
