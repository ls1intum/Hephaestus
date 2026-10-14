package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import de.tum.cit.aet.hephaestus.evidence.SourceCatalog;
import de.tum.cit.aet.hephaestus.evidence.SourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.evidence.SourceContractVersion;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.AboutRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemDescriptor;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventDefinition;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventName;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ReviewCapabilities;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewedByKind;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PracticeDefinitionValidatorTest extends BaseUnitTest {
    private PracticeDefinitionValidator validator;

    @BeforeEach
    void setUp() {
        var sources = mock(SourceCatalogRegistry.class);
        var catalog = mock(SourceCatalog.class);
        var options = mock(PracticeItemOptions.class);
        var descriptor = mock(ItemDescriptor.class);
        var event = mock(ItemEventDefinition.class);
        lenient().when(sources.current()).thenReturn(catalog);
        lenient().when(catalog.version()).thenReturn(new SourceContractVersion("2.0.0"));
        lenient()
                .when(catalog.sourcesFor("scm.pull_request"))
                .thenReturn(Set.of(new SourceKind("scm.pull-request.core")));
        lenient().when(options.require(new ItemKind("scm.pull_request"))).thenReturn(descriptor);
        lenient().when(descriptor.roles()).thenReturn(Set.of(AboutRole.AUTHOR));
        lenient().when(descriptor.events()).thenReturn(List.of(event));
        lenient().when(event.name()).thenReturn(new ItemEventName("scm.pull_request.opened"));
        lenient().when(event.bindableForAuthoring()).thenReturn(true);
        lenient()
                .when(descriptor.reviewCapabilities())
                .thenReturn(new ReviewCapabilities(List.of(), List.of(), Set.of(), Set.of(), Set.of()));
        validator = new PracticeDefinitionValidator(sources, options);
    }

    @Test
    void shouldAcceptExplicitNobodyWithoutChoosingAnAuthor() {
        var original = DefinitionTestFixtures.definition();
        var nobody = new PracticeDefinition(
                original.name(),
                original.reviewOn(),
                null,
                null,
                original.reads(),
                original.reviewedBy(),
                original.criteria(),
                null,
                null,
                true,
                original.guidance());
        validator.validate(new ItemKind("scm.pull_request"), nobody);
        assertThat(nobody.about()).isNull();
    }

    @Test
    void shouldRefuseAnUnsupportedRoleBeforeAReviewCanRun() {
        var original = DefinitionTestFixtures.definition();
        var unsupported = new PracticeDefinition(
                original.name(),
                original.reviewOn(),
                null,
                AboutRole.MERGER,
                original.reads(),
                original.reviewedBy(),
                original.criteria(),
                null,
                null,
                true,
                original.guidance());
        assertThatThrownBy(() -> validator.validate(new ItemKind("scm.pull_request"), unsupported))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRefuseWhitespaceOnlyCriteria() {
        var invalid = DefinitionTestFixtures.definition("Valid name", " \n");
        assertThatThrownBy(() -> validator.validate(new ItemKind("scm.pull_request"), invalid))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRefuseAScriptWhenPeopleReviewThePractice() {
        var original = DefinitionTestFixtures.definition();
        var people = new PracticeDefinition(
                original.name(),
                original.reviewOn(),
                null,
                original.about(),
                original.reads(),
                new ReviewedBy(
                        ReviewedByKind.PEOPLE,
                        new ReviewLimit("HUMAN_JUDGMENT", "People need to judge this work."),
                        List.of()),
                original.criteria(),
                "export default {};",
                null,
                false,
                original.guidance());
        assertThatThrownBy(() -> validator.validate(new ItemKind("scm.pull_request"), people))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
