package de.tum.cit.aet.hephaestus.integration.scm.domain.item;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemUrlResolver;
import de.tum.cit.aet.hephaestus.integration.scm.domain.workurl.ReviewedWorkUrls;
import de.tum.cit.aet.hephaestus.integration.scm.spi.ScmTokenSource;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnServerRole
@RequiredArgsConstructor
public class ScmItemUrlResolver implements ItemUrlResolver {
    private final ConnectionService connections;
    private final List<ScmTokenSource> sources;
    private final ScmItemQueryRepository items;

    @Override
    @Transactional(readOnly = true)
    public Optional<ItemRef> resolve(long workspaceId, String url) {
        var page = ReviewedWorkUrls.page(url);
        var provider = connections.findActiveProviderKind(workspaceId);
        if (page.isEmpty() || provider.isEmpty()) return Optional.empty();
        var server = sources.stream()
                .filter(source -> source.kind() == provider.get())
                .findFirst()
                .flatMap(source -> source.serverUrl(workspaceId));
        if (server.isEmpty()
                || !ReviewedWorkUrls.configuredOrigin(server.get())
                        .map(page.get().origin()::equals)
                        .orElse(false)) return Optional.empty();
        var address = ReviewedWorkUrls.workAddress(provider.get(), page.get());
        if (address.isEmpty()) return Optional.empty();
        var work = address.get();
        var matches = items.atAddress(
                workspaceId,
                provider.get().name(),
                server.get(),
                work.repositoryName(),
                work.kind().value(),
                work.number());
        if (matches.size() != 1) return Optional.empty();
        var match = matches.getFirst();
        return Optional.of(
                new ItemRef(workspaceId, match.getId(), work.kind(), match.getSourceId(), match.getParentId(), null));
    }
}
