package de.tum.cit.aet.hephaestus.integration.scm.domain.item;

import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureCode;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureException;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemVersion;
import de.tum.cit.aet.hephaestus.integration.core.spi.ScanItemVersions;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnCaptureRole
@RequiredArgsConstructor
public class ScmFileVersionComposer implements ScanItemVersions {
    private final ScmItemAccess items;
    private final ScmRepositoryAccess access;
    private final RepositoryRepository repositories;
    private final ScmGitTree tree;
    private final ScmFileVersions versions;

    @Override
    public Set<ItemKind> kinds() {
        return Set.of(ScmItemEvents.FILE);
    }

    @Transactional(readOnly = true)
    @Override
    public ItemVersion compose(ItemRef item, ItemVersion containerVersion, List<String> context) {
        var pinned = inventory(item, containerVersion.value());
        var entry = pinned.assigned();
        return versions.version(
                entry.path(), entry.objectId(), entry.mode(), context, pinned.entries(), containerVersion.value());
    }

    @Transactional(readOnly = true)
    @Override
    public List<String> contextPaths(ItemRef item, ItemVersion assignedVersion, List<String> context) {
        String commit = assignedVersion.baseVersion();
        if (commit == null
                || !commit.matches("[0-9a-f]{40}|[0-9a-f]{64}")
                || ScmFileVersions.parse(assignedVersion).isEmpty())
            throw new ItemCaptureException(ItemCaptureCode.VERSION_UNAVAILABLE, "Assigned file version is unavailable");
        var pinned = inventory(item, commit);
        var entry = pinned.assigned();
        var actual = versions.version(entry.path(), entry.objectId(), entry.mode(), context, pinned.entries(), commit);
        if (!actual.equals(assignedVersion))
            throw new ItemCaptureException(
                    ItemCaptureCode.VERSION_UNAVAILABLE,
                    "The selected context does not match the assigned file version");
        return ScmFileVersions.contextEntries(entry.path(), context, pinned.entries()).stream()
                .map(ScmFileVersions.Entry::path)
                .toList();
    }

    private record PinnedInventory(ScmFileVersions.Entry assigned, List<ScmFileVersions.Entry> entries) {}

    private PinnedInventory inventory(ItemRef item, String commit) {
        items.require(item);
        if (!kinds().contains(item.kind())) throw new IllegalArgumentException("Unsupported composed item kind");
        var holder = items.parent(item, ScmItemEvents.REPOSITORY);
        var repository =
                repositories.findById(Objects.requireNonNull(holder.sourceId())).orElseThrow();
        var key = access.prepare(item.workspaceId(), repository);
        var entries = tree.inventory(key, commit);
        var assigned = entries.stream()
                .filter(entry -> entry.path().equals(item.path()))
                .findFirst()
                .orElseThrow(() -> new ItemCaptureException(
                        ItemCaptureCode.ITEM_REMOVED, "Assigned file is unavailable at the pinned commit"));
        return new PinnedInventory(assigned, entries);
    }
}
