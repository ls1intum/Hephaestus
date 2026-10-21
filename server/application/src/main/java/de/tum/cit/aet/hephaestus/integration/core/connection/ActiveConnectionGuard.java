package de.tum.cit.aet.hephaestus.integration.core.connection;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationState;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs a write that a disconnect must erase, under the lifecycle lock of the connection it came through. A disconnect
 * takes the same lock before it erases the connection's content, so either the disconnect committed first and the
 * write does not run, or the disconnect waits and then erases what the write stored.
 */
@Component
@RequiredArgsConstructor
public class ActiveConnectionGuard {

    private final ConnectionRepository connections;

    /**
     * Runs {@code work} in one transaction while the workspace's ACTIVE connection of this kind is the only ACTIVE one
     * for {@code instanceKey}.
     *
     * @return what {@code work} returned; empty when the connection ended
     */
    @Transactional
    public <T> Optional<T> whileActive(long workspaceId, IntegrationKind kind, String instanceKey, Supplier<T> work) {
        Optional<Connection> active = connections.findActive(workspaceId, kind);
        if (active.isEmpty()) return Optional.empty();
        connections.acquireLifecycleLock(Objects.requireNonNull(active.get().getId()), workspaceId);
        List<Long> owners = connections.findWorkspaceIdsByKindAndInstanceKeyAndState(
                kind, instanceKey, IntegrationState.ACTIVE, Limit.of(2));
        if (!owners.equals(List.of(workspaceId))) return Optional.empty();
        return Optional.ofNullable(work.get());
    }
}
