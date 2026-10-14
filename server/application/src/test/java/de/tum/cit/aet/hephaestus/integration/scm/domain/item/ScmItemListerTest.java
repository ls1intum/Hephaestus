package de.tum.cit.aet.hephaestus.integration.scm.domain.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemListRequest;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemVersion;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.workdir.RepositoryKey;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.testconfig.ItemFixtures;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ScmItemListerTest extends BaseUnitTest {
    @Test
    void shouldPageOnlyOnePinnedInventoryAndRejectAChangedVersionCursor() {
        var items = mock(ScmItemService.class);
        var access = mock(ScmRepositoryAccess.class);
        var repositories = mock(RepositoryRepository.class);
        var tree = mock(ScmGitTree.class);
        var holder = new ItemRef(1, 2, ScmItemEvents.REPOSITORY, 3L, null, null);
        var version = new ItemVersion("a".repeat(40), null);
        var repository = new Repository();
        repository.setId(3L);
        repository.setNameWithOwner("owner/repository");
        var key = new RepositoryKey(1L, 3L);
        var integration = new IntegrationRef(IntegrationKind.GITHUB, 1, "test");
        var snapshot = ItemFixtures.snapshot(holder, Map.of())
                .version(version)
                .integration(integration)
                .label(ItemFixtures.label()
                        .itemId(2)
                        .title("repo")
                        .noun("repository")
                        .integration(IntegrationKind.GITHUB)
                        .build())
                .build();
        when(items.pin(holder, version)).thenReturn(snapshot);
        when(repositories.findById(3L)).thenReturn(Optional.of(repository));
        when(access.prepare(1, repository)).thenReturn(key);
        when(tree.inventory(key, version.value()))
                .thenReturn(List.of(
                        new ScmFileVersions.Entry("A.java", "100644", "b".repeat(40)),
                        new ScmFileVersions.Entry("B.java", "100644", "c".repeat(40))));
        var lister = new ScmItemLister(
                items,
                access,
                repositories,
                tree,
                new ScmFileVersions(JsonMapper.builder().build()));
        var first = lister.list(new ItemListRequest(holder, version, ScmItemEvents.FILE, null, 1));
        assertThat(first.complete()).isFalse();
        assertThat(first.nextCursor()).isNotBlank();
        assertThat(first.items()).extracting(i -> i.item().path()).containsExactly("A.java");
        var last = lister.list(new ItemListRequest(holder, version, ScmItemEvents.FILE, first.nextCursor(), 1));
        assertThat(last.complete()).isTrue();
        assertThat(last.nextCursor()).isNull();
        assertThat(last.items()).extracting(i -> i.item().path()).containsExactly("B.java");
        var changed = new ItemVersion("d".repeat(40), null);
        when(items.pin(holder, changed))
                .thenReturn(ItemFixtures.snapshot(holder, Map.of())
                        .version(changed)
                        .integration(integration)
                        .label(snapshot.label())
                        .build());
        when(tree.inventory(key, changed.value())).thenReturn(List.of());
        assertThatThrownBy(() ->
                        lister.list(new ItemListRequest(holder, changed, ScmItemEvents.FILE, first.nextCursor(), 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
