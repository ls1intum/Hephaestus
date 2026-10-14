package de.tum.cit.aet.hephaestus.practices.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.EntityTagPrecondition;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntry;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditPort;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.practices.DefinitionTestFixtures;
import de.tum.cit.aet.hephaestus.practices.GroupDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinitionField;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinitionValidator;
import de.tum.cit.aet.hephaestus.practices.PracticeReleaseChoice;
import de.tum.cit.aet.hephaestus.practices.PracticeReviewDigest;
import de.tum.cit.aet.hephaestus.practices.curated.BundledPracticeCatalog;
import de.tum.cit.aet.hephaestus.practices.curated.BundledPracticeCatalogLoader;
import de.tum.cit.aet.hephaestus.practices.curated.CatalogChangeKind;
import de.tum.cit.aet.hephaestus.practices.curated.CatalogEntryState;
import de.tum.cit.aet.hephaestus.practices.dto.CatalogPracticeInputDTO;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class CatalogServiceTest extends BaseUnitTest {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final CatalogEntryRepository repository = mock(CatalogEntryRepository.class);
    private final BundledPracticeCatalogLoader loader = mock(BundledPracticeCatalogLoader.class);
    private final PracticeReviewDigest digest = mock(PracticeReviewDigest.class);
    private final ConfigAuditPort audit = mock(ConfigAuditPort.class);
    private final Map<CatalogEntry.Id, CatalogEntry> rows = new HashMap<>();
    private final CatalogService service = new CatalogService(repository, loader, digest, mapper);
    private final CatalogCustomizationService customization = new CatalogCustomizationService(
            service, repository, mock(PracticeDefinitionValidator.class), mock(CatalogLock.class), mapper, audit);
    private final PracticeDefinition original = DefinitionTestFixtures.definition();
    private final CatalogPracticeDocument document =
            new CatalogPracticeDocument(new ItemKind("scm.pull_request"), "review", original);

    @BeforeEach
    void setUp() {
        lenient()
                .when(repository.findById(any()))
                .thenAnswer(call -> Optional.ofNullable(rows.get(call.getArgument(0))));
        lenient()
                .when(repository.findByIdTypeOrderByIdSlugAsc(any()))
                .thenAnswer(call -> rows.values().stream()
                        .filter(row -> row.getId().type() == call.getArgument(0))
                        .sorted(Comparator.comparing(row -> row.getId().slug()))
                        .toList());
        lenient().when(repository.findAll()).thenAnswer(call -> new ArrayList<>(rows.values()));
        lenient().when(repository.save(any())).thenAnswer(call -> save(call.getArgument(0)));
        lenient().when(repository.saveAndFlush(any())).thenAnswer(call -> save(call.getArgument(0)));
        lenient().when(digest.of(any(PracticeDefinition.class))).thenReturn("same-review");
        ship(document);
    }

    private CatalogEntry save(CatalogEntry row) {
        rows.put(row.getId(), row);
        return row;
    }

    private void ship(CatalogPracticeDocument shipped) {
        when(loader.catalog())
                .thenReturn(new BundledPracticeCatalog(
                        List.of(new BundledPracticeCatalog.BundledEntry<>(
                                "review", new GroupDefinition("Review", null, null, null), 0)),
                        List.of(new BundledPracticeCatalog.BundledEntry<>("description", shipped, 0))));
    }

    private EntityTagPrecondition tag(String value) {
        return EntityTagPrecondition.parse(EntityTagPrecondition.format(value));
    }

    private CatalogOffer<CatalogPracticeDocument> customize(PracticeDefinition definition) {
        return customization.updatePractice(
                "description",
                new CatalogPracticeInputDTO("description", "scm.pull_request", "review", definition, 4),
                tag(service.practice("description").etag()));
    }

    @Test
    void shouldRecordNoInventedUpstreamForALocalPractice() {
        var offered = customization.createPractice(
                new CatalogPracticeInputDTO("local", "scm.pull_request", null, original, null));
        assertThat(offered.state()).isEqualTo(CatalogEntryState.YOURS);
        assertThat(offered.shipped()).isNull();
        assertThat(offered.base()).isNull();
        assertThat(Objects.requireNonNull(rows.get(new CatalogEntry.Id(CatalogEntryType.PRACTICE, "local")))
                        .getBase())
                .isNull();
        verify(audit).record(any());
    }

    @Test
    void shouldDeclineAnUpdateAndKeepResetAvailableWithIndependentControls() {
        var local = DefinitionTestFixtures.rename(original, "My description");
        var first = customize(local);
        customization.setExcluded(CatalogEntryType.PRACTICE, "description", true, tag(first.etag()));
        var offered = DefinitionTestFixtures.rename(original, "New bundled description");
        when(digest.of(local)).thenReturn("same-review");
        when(digest.of(offered)).thenReturn("same-review");
        ship(new CatalogPracticeDocument(document.kind(), document.groupSlug(), offered));
        var pending = service.practice("description");
        assertThat(pending.state()).isEqualTo(CatalogEntryState.UPDATE_WAITING);
        assertThat(pending.changeKind()).isEqualTo(CatalogChangeKind.WORDING);
        customization.acknowledge(CatalogEntryType.PRACTICE, "description", tag(pending.etag()));
        var declined = service.practice("description");
        assertThat(declined.effective().definition()).isEqualTo(local);
        assertThat(Objects.requireNonNull(declined.base()).definition()).isEqualTo(offered);
        assertThat(declined.state()).isEqualTo(CatalogEntryState.EDITED_HERE);
        customization.reset(CatalogEntryType.PRACTICE, "description", tag(declined.etag()));
        var reset = service.practice("description");
        assertThat(reset.effective().definition()).isEqualTo(offered);
        assertThat(reset.position()).isEqualTo(4);
        assertThat(reset.excluded()).isTrue();
        assertThat(reset.state()).isEqualTo(CatalogEntryState.FROM_HEPHAESTUS);
        assertThat(Objects.requireNonNull(rows.get(new CatalogEntry.Id(CatalogEntryType.PRACTICE, "description")))
                        .getKind())
                .isNull();
    }

    @Test
    void shouldRetainRemovedBundledContentAndItsProvenance() {
        customize(DefinitionTestFixtures.rename(original, "Retained definition"));
        when(loader.catalog()).thenReturn(new BundledPracticeCatalog(List.of(), List.of()));
        assertThat(service.practice("description").state()).isEqualTo(CatalogEntryState.NO_LONGER_SHIPPED);
        assertThat(service.practice("description").effective().definition().name())
                .isEqualTo("Retained definition");
    }

    @Test
    void shouldExcludeTheWholeGroupFromTheOfferedCatalogAndAllowInclusion() {
        customization.setExcluded(
                CatalogEntryType.GROUP,
                "review",
                true,
                tag(service.group("review").etag()));
        assertThat(service.practiceView(service.practice("description")).offered())
                .isFalse();
        assertThat(service.effectiveCatalog().installablePractices()).isEmpty();
        customization.setExcluded(
                CatalogEntryType.GROUP,
                "review",
                false,
                tag(service.group("review").etag()));
        assertThat(service.practiceView(service.practice("description")).offered())
                .isTrue();
    }

    @Test
    void shouldRejectStaleAndMissingPreconditionsWithoutWriting() {
        assertThatThrownBy(() -> customization.setExcluded(CatalogEntryType.PRACTICE, "description", true, null))
                .isInstanceOf(CatalogPreconditionRequiredException.class);
        assertThatThrownBy(
                        () -> customization.setExcluded(CatalogEntryType.PRACTICE, "description", true, tag("stale")))
                .isInstanceOf(StaleCatalogEntryException.class)
                .hasMessage("This entry changed after you loaded it. Reload and try again.");
        assertThat(rows).isEmpty();
        verifyNoInteractions(audit);
    }

    @Test
    void shouldKeepKindImmutableEvenWhenTheDefinitionHasNoEvents() {
        assertThatThrownBy(() -> customization.updatePractice(
                        "description",
                        new CatalogPracticeInputDTO("description", "scm.issue", null, original, 0),
                        tag(service.practice("description").etag())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(rows).isEmpty();
    }

    @Test
    void shouldResequenceMovedPracticesAndRestoreTheBundledOrder() {
        customization.createPractice(
                new CatalogPracticeInputDTO("local", "scm.pull_request", "review", original, null));
        customization.move("local", "review", 0, tag(service.effectiveCatalog().etag()));
        assertThat(service.effectiveCatalog().practices())
                .extracting(CatalogOffer::slug)
                .containsExactly("local", "description");
        assertThat(service.practice("local").position()).isZero();
        assertThat(service.practice("description").position()).isEqualTo(1);
        customization.resetOrder(tag(service.effectiveCatalog().etag()));
        assertThat(service.effectiveCatalog().practices())
                .extracting(CatalogOffer::slug)
                .containsExactly("description", "local");
        assertThat(service.practice("local").effective().definition()).isEqualTo(original);
    }

    @Test
    void shouldAuditFinalReleaseContentAndBaselineWithoutScriptText() {
        customize(DefinitionTestFixtures.rename(original, "My description"));
        clearInvocations(audit);
        var offered = DefinitionTestFixtures.rename(original, "New name");
        ship(new CatalogPracticeDocument(document.kind(), document.groupSlug(), offered));
        var result = customization.applyRelease(
                "description",
                Map.of(PracticeDefinitionField.NAME, PracticeReleaseChoice.OFFERED),
                tag(service.practice("description").etag()));
        assertThat(Objects.requireNonNull(result.base()).definition()).isEqualTo(offered);
        assertThat(result.effective().definition()).isEqualTo(offered);
        var entry = ArgumentCaptor.forClass(ConfigAuditEntry.class);
        verify(audit, times(1)).record(entry.capture());
        var json = mapper.valueToTree(entry.getValue());
        for (String field : List.of("before", "after")) {
            assertThat(json.path(field).propertyNames())
                    .containsExactlyInAnyOrder("slug", "definitionDigest", "baseDigest", "position", "excluded");
        }
        assertThat(mapper.writeValueAsString(entry.getValue())).doesNotContain(original.criteria());
    }

    @Test
    void shouldRefuseResetWhenTheOfferedItemKindDiffersFromTheStoredIdentity() {
        customize(DefinitionTestFixtures.rename(original, "Local name"));
        ship(new CatalogPracticeDocument(new ItemKind("scm.issue"), document.groupSlug(), original));
        var current = service.practice("description");
        assertThatThrownBy(() -> customization.reset(CatalogEntryType.PRACTICE, "description", tag(current.etag())))
                .isInstanceOf(CatalogConflictException.class);
        assertThat(service.practice("description").effective().kind()).isEqualTo(document.kind());
        assertThat(service.practice("description").effective().definition().name())
                .isEqualTo("Local name");
    }

    @Test
    void shouldReadEachCatalogTypeOnceWhenRenderingTheCatalog() {
        clearInvocations(repository);
        var view = service.view();
        assertThat(view.practices())
                .extracting(entry -> entry.definition().name())
                .containsExactly(original.name());
        assertThat(view.groups()).extracting(entry -> entry.name()).containsExactly("Review");
        verify(repository).findByIdTypeOrderByIdSlugAsc(CatalogEntryType.PRACTICE);
        verify(repository).findByIdTypeOrderByIdSlugAsc(CatalogEntryType.GROUP);
    }
}
