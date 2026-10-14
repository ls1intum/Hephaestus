package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.dto.AccountRefDTO;
import de.tum.cit.aet.hephaestus.core.auth.dto.ProviderIdentityRefDTO;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountIdentityQuery;
import de.tum.cit.aet.hephaestus.core.auth.spi.GitProviderRegistry;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCatalog;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemLabel;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemLabels;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.practices.review.dto.ItemRefDTO;
import de.tum.cit.aet.hephaestus.practices.review.dto.ReviewDeveloperDTO;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnCaptureRole
@RequiredArgsConstructor
public class ProductReferenceQuery {

    private final JdbcClient jdbc;
    private final ItemLabels labels;
    private final ItemCatalog catalog;
    private final AccountIdentityQuery identities;
    private final GitProviderRegistry providers;

    @Transactional(readOnly = true)
    public Map<Long, ItemRefDTO> items(long workspaceId, Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<StoredItem> stored = jdbc.sql("""
            SELECT id, kind, source_id, parent_id, path, removed_at FROM item
            WHERE workspace_id = :workspaceId AND id IN (:ids)
            """)
                .param("workspaceId", workspaceId)
                .param("ids", ids)
                .query((rs, row) -> {
                    @Nullable OffsetDateTime removed = rs.getObject("removed_at", OffsetDateTime.class);
                    return new StoredItem(
                            new ItemRef(
                                    workspaceId,
                                    rs.getLong("id"),
                                    new ItemKind(rs.getString("kind")),
                                    rs.getObject("source_id", Long.class),
                                    rs.getObject("parent_id", Long.class),
                                    rs.getString("path")),
                            removed == null ? null : removed.toInstant());
                })
                .list();
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
        return jdbc
                .sql("""
                    SELECT a.id, a.display_name FROM account a JOIN membership m ON m.account_id = a.id
                    WHERE m.workspace_id = :workspaceId AND a.id IN (:ids)
                    """)
                .param("workspaceId", workspaceId)
                .param("ids", ids)
                .query((rs, row) -> new ReviewDeveloperDTO(
                        Long.toString(rs.getLong("id")), rs.getString("display_name"), null, null))
                .list()
                .stream()
                .map(dto -> {
                    var profiles = identities.activeLinksForAccount(Long.parseLong(dto.accountId())).stream()
                            .filter(identity -> providers.visibleInWorkspace(identity.providerId(), workspaceId))
                            .flatMap(
                                    identity -> providers
                                            .profile(
                                                    identity.providerId(),
                                                    identity.providerUserKey(),
                                                    identity.teamKey())
                                            .stream())
                            .toList();
                    var avatar = profiles.stream()
                            .map(profile -> profile.avatarUrl())
                            .filter(Objects::nonNull)
                            .findFirst()
                            .orElse(null);
                    var login = profiles.stream()
                            .map(profile -> profile.login())
                            .filter(Objects::nonNull)
                            .findFirst()
                            .orElse(null);
                    return new ReviewDeveloperDTO(dto.accountId(), dto.displayName(), avatar, login);
                })
                .collect(Collectors.toMap(dto -> Long.parseLong(dto.accountId()), Function.identity()));
    }

    private record StoredItem(ItemRef ref, @Nullable Instant removedAt) {}

    public Map<Long, AccountRefDTO> accounts(long workspaceId, Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return jdbc
                .sql("SELECT id,status,display_name FROM account WHERE id IN (:ids)")
                .param("ids", ids)
                .query((rs, row) -> {
                    long id = rs.getLong("id");
                    var profiles = identities.activeLinksForAccount(id).stream()
                            .filter(link -> providers.visibleInWorkspace(link.providerId(), workspaceId))
                            .flatMap(link -> providers
                                    .profile(link.providerId(), link.providerUserKey(), link.teamKey())
                                    .map(profile -> new ProviderIdentityRefDTO(
                                            providers.providerTypeName(link.providerId()),
                                            profile.login(),
                                            profile.displayName(),
                                            profile.avatarUrl()))
                                    .stream())
                            .toList();
                    var avatar = profiles.stream()
                            .map(ProviderIdentityRefDTO::avatarUrl)
                            .filter(Objects::nonNull)
                            .findFirst()
                            .orElse(null);
                    return new AccountRefDTO(
                            Long.toString(id),
                            Account.Status.valueOf(rs.getString("status")),
                            rs.getString("display_name"),
                            avatar,
                            profiles);
                })
                .list()
                .stream()
                .collect(Collectors.toMap(a -> Long.parseLong(a.id()), Function.identity()));
    }
}
