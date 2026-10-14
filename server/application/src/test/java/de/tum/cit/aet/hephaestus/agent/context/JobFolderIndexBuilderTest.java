package de.tum.cit.aet.hephaestus.agent.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.runtime.ProvenanceDigest;
import de.tum.cit.aet.hephaestus.evidence.CitationSide;
import de.tum.cit.aet.hephaestus.evidence.ClasspathSourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.evidence.SourceCatalog;
import de.tum.cit.aet.hephaestus.evidence.SourceCompleteness;
import de.tum.cit.aet.hephaestus.evidence.SourceContractVersion;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.evidence.capture.CaptureContribution;
import de.tum.cit.aet.hephaestus.evidence.capture.CitableText;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class JobFolderIndexBuilderTest extends BaseUnitTest {
    private final SourceKind source = new SourceKind("docs.document.core");

    private JobFolderIndexBuilder builder() {
        var registry = mock(ClasspathSourceCatalogRegistry.class);
        var catalog = mock(SourceCatalog.class);
        when(registry.current()).thenReturn(catalog);
        when(catalog.version()).thenReturn(new SourceContractVersion("2.0.0"));
        when(registry.catalogDigest()).thenReturn("a".repeat(64));
        return new JobFolderIndexBuilder(registry, new JsonMapper(), Clock.systemUTC());
    }

    private CaptureContribution contribution(String registeredSource) {
        byte[] content = "Evidence".getBytes(StandardCharsets.UTF_8);
        var text = new CitableText(
                registeredSource,
                "document/body",
                CitationSide.NEW,
                null,
                "context/body.txt",
                ProvenanceDigest.sha256Hex(content));
        return new CaptureContribution(
                Map.of("context/body.txt", content),
                Map.of(source, SourceCompleteness.COMPLETE),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                null,
                Map.of(),
                List.of(),
                List.of(),
                List.of(text));
    }

    @Test
    void retainsTheLogicalCitationAndItsActualCapturedBytes() {
        var prepared = builder().build(new ItemKind("docs.document"), Map.of(source, contribution(source.value())));
        assertThat(Objects.requireNonNull(prepared.manifest()).citableTexts())
                .singleElement()
                .satisfies(text -> {
                    assertThat(text.path()).isEqualTo("document/body");
                    assertThat(text.stagedPath()).isEqualTo("context/body.txt");
                    assertThat(text.sha256())
                            .isEqualTo(Objects.requireNonNull(prepared.manifest())
                                    .files()
                                    .getFirst()
                                    .file()
                                    .sha256());
                });
    }

    @Test
    void rejectsACitationRegisteredUnderAnotherSource() {
        assertThatThrownBy(() -> builder()
                        .build(new ItemKind("docs.document"), Map.of(source, contribution("docs.document.other"))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
