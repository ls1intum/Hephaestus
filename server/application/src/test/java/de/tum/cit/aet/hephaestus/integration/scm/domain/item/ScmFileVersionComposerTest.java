package de.tum.cit.aet.hephaestus.integration.scm.domain.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemVersion;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.workdir.RepositoryKey;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ScmFileVersionComposerTest extends BaseUnitTest {
    @Test
    void shouldFreezeOnlyTheDeclaredContextAtThePinnedContainerCommit() {
        var items = mock(ScmItemAccess.class);
        var access = mock(ScmRepositoryAccess.class);
        var repositories = mock(RepositoryRepository.class);
        var tree = mock(ScmGitTree.class);
        var file = new ItemRef(1, 2, ScmItemEvents.FILE, null, 3L, "src/a.ts");
        var parent = new ItemRef(1, 3, ScmItemEvents.REPOSITORY, 4L, null, null);
        var repository = new Repository();
        var key = new RepositoryKey(1, 4);
        var pinned = new ItemVersion("a".repeat(40), null);
        when(items.parent(file, ScmItemEvents.REPOSITORY)).thenReturn(parent);
        when(repositories.findById(4L)).thenReturn(Optional.of(repository));
        when(access.prepare(1, repository)).thenReturn(key);
        var self = new ScmFileVersions.Entry("src/a.ts", "100644", "b".repeat(40));
        var context = new ScmFileVersions.Entry("src/b.ts", "100644", "c".repeat(40));
        var unrelated = new ScmFileVersions.Entry("README.md", "100644", "d".repeat(40));
        when(tree.inventory(key, pinned.value())).thenReturn(List.of(self, context, unrelated));
        var composer =
                new ScmFileVersionComposer(items, access, repositories, tree, new ScmFileVersions(new ObjectMapper()));
        assertThat(composer.kinds()).containsExactly(ScmItemEvents.FILE);
        var original = composer.compose(file, pinned, List.of("src/**"));
        assertThat(original.baseVersion()).isEqualTo(pinned.value());
        when(tree.inventory(key, pinned.value()))
                .thenReturn(List.of(
                        self, context, new ScmFileVersions.Entry(unrelated.path(), unrelated.mode(), "e".repeat(40))));
        assertThat(composer.compose(file, pinned, List.of("src/**"))).isEqualTo(original);
        when(tree.inventory(key, pinned.value()))
                .thenReturn(List.of(
                        self, new ScmFileVersions.Entry(context.path(), context.mode(), "f".repeat(40)), unrelated));
        assertThat(composer.compose(file, pinned, List.of("src/**"))).isNotEqualTo(original);
    }
}
