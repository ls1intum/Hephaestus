package de.tum.cit.aet.hephaestus.practices.curated;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.evidence.internal.ClasspathSourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.integration.chat.item.ThreadItemDescriptor;
import de.tum.cit.aet.hephaestus.integration.core.framework.ItemDescriptorRegistry;
import de.tum.cit.aet.hephaestus.integration.docs.item.DocumentItemDescriptor;
import de.tum.cit.aet.hephaestus.integration.scm.domain.item.FileItemDescriptor;
import de.tum.cit.aet.hephaestus.integration.scm.domain.item.IssueItemDescriptor;
import de.tum.cit.aet.hephaestus.integration.scm.domain.item.PullRequestItemDescriptor;
import de.tum.cit.aet.hephaestus.integration.scm.domain.item.PullRequestReviewItemDescriptor;
import de.tum.cit.aet.hephaestus.integration.scm.domain.item.RepositoryItemDescriptor;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinitionValidator;
import de.tum.cit.aet.hephaestus.practices.PracticeItemOptions;
import de.tum.cit.aet.hephaestus.practices.spi.ReadNeed;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * Facts the shipped evidence vocabulary asserts about itself, held as tests so a source added later
 * cannot silently invalidate them. A failure here means deciding whether the new case is a genuine
 * requirement to reintroduce deliberately, or an authoring slip just caught — not relaxing the test.
 */
class EvidencePolicyRedundancyTest extends BaseUnitTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();
    private final ClasspathSourceCatalogRegistry registry =
            new ClasspathSourceCatalogRegistry(objectMapper, Clock.systemUTC());
    private final PracticeItemOptions itemOptions = new PracticeItemOptions(new ItemDescriptorRegistry(List.of(
            new PullRequestItemDescriptor(),
            new PullRequestReviewItemDescriptor(),
            new IssueItemDescriptor(),
            new FileItemDescriptor(),
            new RepositoryItemDescriptor(),
            new ThreadItemDescriptor(),
            new DocumentItemDescriptor())));
    private final BundledPracticeCatalogLoader loader =
            new BundledPracticeCatalogLoader(objectMapper, new PracticeDefinitionValidator(registry, itemOptions));

    /**
     * Every kind a practice can be authored against has at least one source that declares it applies —
     * checked at build time, because the alternative is discovering it at review time, unwatched.
     */
    @Test
    void shouldProvideApplicableSourcesForEveryAuthorableItemKind() {
        for (var descriptor : itemOptions.all()) {
            var kind = descriptor.kind();
            assertThat(registry.current().sourcesFor(kind.value()))
                    .as(
                            "item kind '%s' can be authored against but no source declares it applies; a practice "
                                    + "written on it would refuse every review that selected it",
                            kind)
                    .isNotEmpty();
        }
    }

    @Test
    void shouldRequireCompleteCaptureOnlyFromSourcesThatCanProvideIt() {
        loader.catalog()
                .practices()
                .forEach(practice -> practice.definition().definition().reads().sources().stream()
                        .filter(need -> need.need() == ReadNeed.COMPLETE)
                        .forEach(need -> assertThat(registry.requireSource(
                                                registry.current().version(), need.source())
                                        .completenessPolicy()
                                        .supportsComplete())
                                .as("%s requires complete capture of %s", practice.slug(), need.source())
                                .isTrue()));
    }

    @Test
    void shouldKeepChangedCodeAbsenceClaimsWithinTheDiff() {
        assertThat(loader.catalog().practices())
                .filteredOn(practice -> Set.of(
                                "handles-errors-instead-of-swallowing-them",
                                "validates-and-escapes-untrusted-input",
                                "keeps-views-free-of-networking-and-persistence")
                        .contains(practice.slug()))
                .hasSize(3)
                .allSatisfy(practice -> assertThat(practice.definition().definition().reads().sources().stream()
                                .filter(need -> need.need() == ReadNeed.COMPLETE)
                                .map(need -> need.source()))
                        .containsExactly(new SourceKind("scm.pull-request.diff")));
    }
}
