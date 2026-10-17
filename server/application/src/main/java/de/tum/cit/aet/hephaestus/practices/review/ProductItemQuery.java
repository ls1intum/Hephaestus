package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemSnapshot;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemSource;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemUrlResolver;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemVersion;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnCaptureRole
@RequiredArgsConstructor
public class ProductItemQuery {
    private final ProductItemRepository rows;
    private final List<ItemSource> sources;
    private final List<ItemUrlResolver> urlResolvers;

    public Optional<ItemRef> find(long workspaceId, long itemId) {
        return rows.activeItem(workspaceId, itemId).map(row -> row.ref(workspaceId));
    }

    public ItemRef require(long workspaceId, long itemId) {
        return find(workspaceId, itemId).orElseThrow(() -> new EntityNotFoundException("Item", Long.toString(itemId)));
    }

    public ItemSnapshot pin(ItemRef item, @Nullable ItemVersion version) {
        return source(item).pin(item, version);
    }

    public Optional<ItemSnapshot> current(ItemRef item) {
        return source(item).current(item);
    }

    private ItemSource source(ItemRef item) {
        var matched = sources.stream().filter(s -> s.supports(item)).toList();
        if (matched.size() != 1) throw new IllegalArgumentException("The item has no unambiguous source.");
        return matched.getFirst();
    }

    @Transactional(readOnly = true)
    public Optional<ItemRef> atUrl(long workspaceId, String url) {
        var matched = urlResolvers.stream()
                .flatMap(resolver -> resolver.resolve(workspaceId, url).stream())
                .limit(2)
                .toList();
        return matched.size() == 1 && current(matched.getFirst()).isPresent()
                ? Optional.of(matched.getFirst())
                : Optional.empty();
    }
}
