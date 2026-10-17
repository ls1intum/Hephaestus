package de.tum.cit.aet.hephaestus.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class ClasspathSourceCatalogRegistryTest extends BaseUnitTest {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-14T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void shouldLoadCurrentSourcesAndExactGovernanceDecisionsWhenResourcesAreValid() {
        var registry = new ClasspathSourceCatalogRegistry(mapper, clock);
        assertThat(registry.current().version()).isEqualTo(ClasspathSourceCatalogRegistry.CURRENT_VERSION);
        assertThat(registry.catalogDigest()).matches("[0-9a-f]{64}");
        assertThat(registry.requireSourcesFor("scm.file")).contains(new SourceKind("scm.repository.tree"));
        assertThat(registry.requireSource(new SourceKind("scm.repository.tree"))
                        .citation()
                        .commit())
                .isEqualTo(CitationRequirement.OPTIONAL);
        assertThat(registry.isSourceUsePermitted(
                        new SourceKind("scm.pull-request.diff"), SourceUsePurpose.AUTOMATED_PRACTICE_REVIEW))
                .isTrue();
    }

    @Test
    void shouldRejectUnknownKindsWhenNoSourceContractApplies() {
        var registry = new ClasspathSourceCatalogRegistry(mapper, clock);
        assertThatThrownBy(() -> registry.requireSource(new SourceKind("scm.unknown")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registry.requireSourcesFor("scm.unknown"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectUnknownFieldsWhenAResourceAddsUnreviewedSemantics() throws IOException {
        var catalog = read(ClasspathSourceCatalogRegistry.CATALOG_RESOURCE);
        catalog.put("futureMeaning", true);
        assertThatThrownBy(() -> registry(catalog, read(ClasspathSourceCatalogRegistry.USE_DECISIONS_RESOURCE)))
                .hasMessageContaining("futureMeaning");
        catalog.remove("futureMeaning");
        ((ObjectNode) catalog.path("sources").get(0).path("citation")).put("futureMeaning", true);
        assertThatThrownBy(() -> registry(catalog, read(ClasspathSourceCatalogRegistry.USE_DECISIONS_RESOURCE)))
                .hasMessageContaining("futureMeaning");
    }

    @Test
    void shouldRejectMissingAndWrongPrimitiveTypesWhenCatalogIsMalformed() throws IOException {
        var catalog = read(ClasspathSourceCatalogRegistry.CATALOG_RESOURCE);
        var source = (ObjectNode) catalog.path("sources").get(0);
        source.remove("defaultRequirement");
        assertThatThrownBy(() -> registry(catalog, read(ClasspathSourceCatalogRegistry.USE_DECISIONS_RESOURCE)))
                .hasMessageContaining("defaultRequirement");
        source.put("defaultRequirement", "true");
        assertThatThrownBy(() -> registry(catalog, read(ClasspathSourceCatalogRegistry.USE_DECISIONS_RESOURCE)))
                .hasMessageContaining("defaultRequirement");
    }

    @Test
    void shouldRejectAnotherVersionWhenResourcesDoNotMatchTheShippedContract() throws IOException {
        var catalog = read(ClasspathSourceCatalogRegistry.CATALOG_RESOURCE).put("version", "1.0.0");
        assertThatThrownBy(() -> registry(catalog, read(ClasspathSourceCatalogRegistry.USE_DECISIONS_RESOURCE)))
                .hasMessageContaining("unexpected version");
        var decisions =
                read(ClasspathSourceCatalogRegistry.USE_DECISIONS_RESOURCE).put("contractVersion", "1.0.0");
        assertThatThrownBy(() -> registry(read(ClasspathSourceCatalogRegistry.CATALOG_RESOURCE), decisions))
                .hasMessageContaining("unexpected contract version");
    }

    @Test
    void shouldRejectDuplicateSourcesAndDecisionsWhenIdentityIsRepeated() throws IOException {
        var catalog = read(ClasspathSourceCatalogRegistry.CATALOG_RESOURCE);
        var sources = (ArrayNode) catalog.path("sources");
        sources.add(sources.get(0).deepCopy());
        assertThatThrownBy(() -> registry(catalog, read(ClasspathSourceCatalogRegistry.USE_DECISIONS_RESOURCE)))
                .hasMessageContaining("Duplicate source kind");
        var document = read(ClasspathSourceCatalogRegistry.USE_DECISIONS_RESOURCE);
        var decisions = (ArrayNode) document.path("decisions");
        decisions.add(decisions.get(0).deepCopy());
        assertThatThrownBy(() -> registry(read(ClasspathSourceCatalogRegistry.CATALOG_RESOURCE), document))
                .hasMessageContaining("Duplicate source-use decision");
    }

    @Test
    void shouldRejectMissingApprovalOrDecisionWhenGovernanceIsIncomplete() throws IOException {
        var catalog = read(ClasspathSourceCatalogRegistry.CATALOG_RESOURCE);
        var document = read(ClasspathSourceCatalogRegistry.USE_DECISIONS_RESOURCE);
        ((ObjectNode) document.path("decisions").get(0)).putNull("reviewer");
        assertThatThrownBy(() -> registry(catalog, document)).hasMessageContaining("requires review metadata");
        var incomplete = read(ClasspathSourceCatalogRegistry.USE_DECISIONS_RESOURCE);
        ((ArrayNode) incomplete.path("decisions")).remove(0);
        assertThatThrownBy(() -> registry(catalog, incomplete)).hasMessageContaining("match the catalog exactly");
    }

    @Test
    void shouldStopPermittingAtExpiryAndRejectAnotherPurposeWhenDecisionDoesNotAuthorizeUse() {
        var registry = new ClasspathSourceCatalogRegistry(mapper, clock);
        var decision = registry.requireUseDecision("use-docs-document-core-automated-review");
        var expiry = Objects.requireNonNull(decision.expiresAt());
        assertThat(decision.permitsAt(expiry.minusNanos(1), SourceUsePurpose.AUTOMATED_PRACTICE_REVIEW))
                .isTrue();
        assertThat(decision.permitsAt(expiry, SourceUsePurpose.AUTOMATED_PRACTICE_REVIEW))
                .isFalse();
        assertThat(decision.permitsAt(clock.instant(), SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY))
                .isFalse();
    }

    private ClasspathSourceCatalogRegistry registry(ObjectNode catalog, ObjectNode decisions) {
        return new ClasspathSourceCatalogRegistry(
                mapper,
                clock,
                new ByteArrayResource(mapper.writeValueAsBytes(catalog)),
                new ByteArrayResource(mapper.writeValueAsBytes(decisions)));
    }

    private ObjectNode read(String resource) throws IOException {
        try (InputStream input = new ClassPathResource(resource).getInputStream()) {
            return (ObjectNode) mapper.readTree(input);
        }
    }
}
