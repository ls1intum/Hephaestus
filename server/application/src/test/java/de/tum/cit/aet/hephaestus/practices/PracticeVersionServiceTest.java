package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import de.tum.cit.aet.hephaestus.core.auth.AccountReferenceService;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountDirectory;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.testconfig.TestEntities;
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
        var version = TestEntities.practiceVersion(9_007_199_254_740_993L, 3, incomplete, "v6:" + "a".repeat(64));
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
        var version = TestEntities.practiceVersion(1L, 1, definition, "v6:" + "a".repeat(64));
        assertThat(service.definitionOf(version).about()).isNull();
    }

    @Test
    void shouldKeepIncompleteHistoricalSourceRequirementsUnavailableWithoutInventingAContract() {
        var recorded = (ObjectNode) mapper.valueToTree(DefinitionTestFixtures.definition());
        ((ObjectNode) recorded.path("reads")).putNull("contract");
        var version = TestEntities.practiceVersion(7L, 1, recorded, "v6:" + "a".repeat(64));
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

    @Test
    void shouldKeepHistoricalScansWithoutReadRequirementsUnavailable() {
        var recorded = (ObjectNode) mapper.valueToTree(DefinitionTestFixtures.definition());
        var scan = recorded.putObject("scan");
        scan.put("itemKind", "scm.file");
        scan.putArray("include").add("**/*.ts");
        scan.putArray("exclude");
        scan.putArray("context").add("**/*");
        var version = TestEntities.practiceVersion(8L, 2, recorded, "v6:" + "b".repeat(64));
        var view = service.view(version, null, null);
        assertThat(view.definitionAvailable()).isFalse();
        var historical = Objects.requireNonNull(view.historicalDefinition());
        assertThat(historical.knownFields()).doesNotContain(PracticeDefinitionField.SCAN);
        assertThat(historical.scan()).isNull();
        assertThat(scan.has("reads")).isFalse();
        assertThat(Objects.requireNonNull(scan.path("include").get(0)).asString())
                .isEqualTo("**/*.ts");
    }

    @Test
    void shouldKeepAMissingHistoricalLimitCodeUnknownInsteadOfGeneratingOne() {
        var recorded = (ObjectNode) mapper.valueToTree(DefinitionTestFixtures.definition());
        ((ObjectNode) recorded.path("reviewedBy"))
                .putArray("limits")
                .addObject()
                .put("description", "A previously recorded limitation.");
        var version = TestEntities.practiceVersion(11L, 1, recorded, "v6:" + "c".repeat(64));
        var view = service.view(version, null, null);
        assertThat(view.definitionAvailable()).isFalse();
        var historical = Objects.requireNonNull(view.historicalDefinition());
        assertThat(historical.knownFields()).doesNotContain(PracticeDefinitionField.REVIEWED_BY);
        assertThat(historical.reviewedBy()).isNull();
        assertThat(historical.criteria())
                .isEqualTo(DefinitionTestFixtures.definition().criteria());
        assertThatThrownBy(() -> service.definitionOf(version)).isInstanceOf(IllegalArgumentException.class);
    }
}
