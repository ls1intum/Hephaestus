package de.tum.cit.aet.hephaestus.integration.scm.domain.item;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
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
@ConditionalOnServerRole
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
        items.require(item);
        if (!kinds().contains(item.kind())) throw new IllegalArgumentException("Unsupported composed item kind");
        var holder = items.parent(item, ScmItemEvents.REPOSITORY);
        var repository =
                repositories.findById(Objects.requireNonNull(holder.sourceId())).orElseThrow();
        var key = access.prepare(item.workspaceId(), repository);
        var inventory = tree.inventory(key, containerVersion.value());
        var entry = inventory.stream()
                .filter(e -> e.path().equals(item.path()))
                .findFirst()
                .orElseThrow(() -> new ItemCaptureException(
                        ItemCaptureCode.ITEM_REMOVED, "Assigned file is unavailable at the pinned" + " commit"));
        return versions.version(
                entry.path(), entry.objectId(), entry.mode(), context, inventory, containerVersion.value());
    }
}
