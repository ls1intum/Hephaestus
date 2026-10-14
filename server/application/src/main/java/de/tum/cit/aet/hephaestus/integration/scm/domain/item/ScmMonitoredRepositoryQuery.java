package de.tum.cit.aet.hephaestus.integration.scm.domain.item;

import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonSourceNamespace;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.core.security.ScmOrigin;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.connection.SourceProviderNamespaces;
import de.tum.cit.aet.hephaestus.integration.core.spi.MonitoredRepositoryQuery;
import de.tum.cit.aet.hephaestus.integration.core.spi.MonitoredRepositoryRef;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnServerRole
@RequiredArgsConstructor
public class ScmMonitoredRepositoryQuery implements MonitoredRepositoryQuery {
    private final JdbcTemplate jdbc;
    private final ConnectionService connections;

    @Override
    @Transactional(readOnly = true)
    public Map<Long, MonitoredRepositoryRef> repositories(long workspaceId) {
        var kind = connections.findActiveProviderKind(workspaceId);
        if (kind.isEmpty()) return Map.of();
        var connection = connections.findActive(workspaceId, kind.orElseThrow());
        if (connection.isEmpty()) return Map.of();
        var origin = PersonSourceNamespace.from(
                        kind.orElseThrow().name(),
                        SourceProviderNamespaces.configuredUrl(
                                connection.orElseThrow().getConfig()))
                .flatMap(namespace -> ScmOrigin.of(namespace.serverUrl()));
        if (origin.isEmpty()) return Map.of();
        Map<Long, MonitoredRepositoryRef> found = new LinkedHashMap<>();
        jdbc.query(
                """
                SELECT i.id AS item_id, m.id AS monitor_id, r.name_with_owner, p.server_url
                FROM item i JOIN repository r ON r.id=i.source_id
                JOIN identity_provider p ON p.id=r.provider_id
                JOIN repository_to_monitor m ON m.workspace_id=i.workspace_id
                  AND m.name_with_owner=r.name_with_owner
                  AND (m.native_id IS NULL OR m.native_id=r.native_id)
                WHERE i.workspace_id=? AND i.kind='scm.repository' AND i.parent_id IS NULL
                  AND i.removed_at IS NULL AND p.type=? ORDER BY i.id
                """,
                rs -> {
                    if (!origin.equals(ScmOrigin.of(rs.getString("server_url")))) return;
                    var ref = new MonitoredRepositoryRef(
                            rs.getLong("item_id"),
                            rs.getLong("monitor_id"),
                            Objects.requireNonNull(rs.getString("name_with_owner")));
                    if (found.putIfAbsent(ref.repositoryItemId(), ref) != null)
                        throw new IllegalStateException("Repository item has ambiguous monitor identity");
                },
                workspaceId,
                kind.orElseThrow().name());
        return Map.copyOf(found);
    }
}
