package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.auth.AccountReferenceService;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountDirectory;
import de.tum.cit.aet.hephaestus.practices.model.PracticeVersion;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class PracticeVersionServiceTest extends BaseUnitTest {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final PracticeVersionService service = new PracticeVersionService(
            mock(PracticeRepository.class),
            mock(PracticeVersionRepository.class),
            mapper,
            mock(PracticeDefinitionValidator.class),
            mock(AccountDirectory.class),
            mock(AccountReferenceService.class));

    @Test
    void shouldPreserveUnknownHistoricalAboutWithoutTreatingItAsNobody() {
        var recorded = mapper.valueToTree(DefinitionTestFixtures.definition());
        var incomplete = (ObjectNode) recorded.deepCopy();
        incomplete.remove("about");
        var version = mock(PracticeVersion.class);
        when(version.getId()).thenReturn(9_007_199_254_740_993L);
        when(version.getNumber()).thenReturn(3);
        when(version.getDefinition()).thenReturn(incomplete);
        when(version.getName()).thenReturn("Describe the change");
        when(version.getReviewDigest()).thenReturn("v6:" + "a".repeat(64));
        when(version.getCreatedAt()).thenReturn(Instant.EPOCH);
        var view = service.view(version, null, null);
        assertThat(view.id()).isEqualTo("9007199254740993");
        assertThat(view.definitionAvailable()).isFalse();
        assertThat(view.definition()).isNull();
        var historical = Objects.requireNonNull(view.historicalDefinition());
        assertThat(historical.knownFields()).doesNotContain(PracticeDefinitionField.ABOUT);
        assertThat(historical.criteria())
                .isEqualTo(DefinitionTestFixtures.definition().criteria());
        assertThatThrownBy(() -> service.definitionOf(version)).isInstanceOf(IllegalArgumentException.class);
        assertThat(incomplete.has("about")).isFalse();
    }

    @Test
    void shouldDistinguishAnExplicitNullAboutFromTheMissingHistoricalKey() {
        var definition = (ObjectNode) mapper.valueToTree(DefinitionTestFixtures.definition());
        definition.putNull("about");
        assertThat(service.available(definition)).isTrue();
        var version = mock(PracticeVersion.class);
        when(version.getDefinition()).thenReturn(definition);
        assertThat(service.definitionOf(version).about()).isNull();
    }

    @Test
    void shouldKeepIncompleteHistoricalSourceRequirementsUnavailableWithoutInventingAContract() {
        var recorded = (ObjectNode) mapper.valueToTree(DefinitionTestFixtures.definition());
        ((ObjectNode) recorded.path("reads")).putNull("contract");
        var version = mock(PracticeVersion.class);
        when(version.getId()).thenReturn(7L);
        when(version.getNumber()).thenReturn(1);
        when(version.getName()).thenReturn("Historical definition");
        when(version.getDefinition()).thenReturn(recorded);
        when(version.getReviewDigest()).thenReturn("v6:" + "a".repeat(64));
        when(version.getCreatedAt()).thenReturn(Instant.EPOCH);
        var view = service.view(version, null, null);
        assertThat(view.definitionAvailable()).isFalse();
        var historical = Objects.requireNonNull(view.historicalDefinition());
        assertThat(historical.knownFields()).doesNotContain(PracticeDefinitionField.READS);
        assertThat(historical.reads()).isNull();
        assertThat(historical.criteria())
                .isEqualTo(DefinitionTestFixtures.definition().criteria());
        assertThat(recorded.path("reads").path("contract").isNull()).isTrue();
        assertThat(recorded.path("reads").path("sources").isArray()).isTrue();
    }
}
