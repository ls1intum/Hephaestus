package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.core.auth.AccountReferenceService;
import de.tum.cit.aet.hephaestus.core.auth.dto.AccountRefDTO;
import de.tum.cit.aet.hephaestus.core.auth.dto.ProviderIdentityRefDTO;
import de.tum.cit.aet.hephaestus.integration.core.framework.ItemDescriptorRegistry;
import de.tum.cit.aet.hephaestus.integration.core.framework.RegisteredItemLabels;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemLabel;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.practices.review.dto.ItemRefDTO;
import de.tum.cit.aet.hephaestus.practices.review.dto.ReviewDeveloperDTO;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnCaptureRole
@RequiredArgsConstructor
public class ProductReferenceQuery {

    private final ProductItemRepository rows;
    private final @Nullable AccountReferenceService accountReferences;
    private final RegisteredItemLabels labels;
    private final ItemDescriptorRegistry catalog;

    @Transactional(readOnly = true)
    public Map<Long, ItemRefDTO> items(long workspaceId, Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<StoredItem> stored = rows.items(workspaceId, ids).stream()
                .map(row -> new StoredItem(row.ref(workspaceId), row.getRemovedAt()))
                .toList();
        Map<Long, ItemLabel> named =
                labels.labels(workspaceId, stored.stream().map(StoredItem::ref).toList());
        return stored.stream().collect(Collectors.toMap(item -> item.ref().itemId(), item -> {
            ItemRef ref = item.ref();
            ItemLabel label = named.get(ref.itemId());
            String fallback = catalog.descriptorFor(ref.kind())
                    .map(descriptor -> descriptor.displayName())
                    .orElse("Item");
            return new ItemRefDTO(
                    Long.toString(ref.itemId()),
                    ref.kind().value(),
                    ref.parentId() == null ? null : Long.toString(ref.parentId()),
                    label == null ? null : label.integration(),
                    label == null ? fallback : label.title(),
                    label == null ? null : label.title(),
                    label == null ? null : label.url(),
                    label == null ? null : label.container(),
                    ref.path(),
                    item.removedAt());
        }));
    }

    @Transactional(readOnly = true)
    public Map<Long, ReviewDeveloperDTO> developers(long workspaceId, Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        var eligible = rows.memberAccountIds(workspaceId, ids);
        return accounts(workspaceId, eligible).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> {
                    var account = entry.getValue();
                    var login = account.identities().stream()
                            .map(ProviderIdentityRefDTO::login)
                            .filter(Objects::nonNull)
                            .findFirst()
                            .orElse(null);
                    return new ReviewDeveloperDTO(account.id(), account.displayName(), account.avatarUrl(), login);
                }));
    }

    private record StoredItem(ItemRef ref, @Nullable Instant removedAt) {}

    public Map<Long, AccountRefDTO> accounts(long workspaceId, Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return Objects.requireNonNull(accountReferences, "Account references require the server role.")
                .references(workspaceId, ids);
    }
}
