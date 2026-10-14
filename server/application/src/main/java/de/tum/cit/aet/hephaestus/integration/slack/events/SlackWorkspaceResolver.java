package de.tum.cit.aet.hephaestus.integration.slack.events;

import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Resolves the workspace that owns the ACTIVE Slack {@code Connection} for a given team.
 *
 * <p>This is a <em>tenant resolution</em>, not a tenant-scoped read: the caller has only the Slack {@code team_id}
 * and must discover which workspace it maps to.
 */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true")
public class SlackWorkspaceResolver {

    private final ConnectionService connectionRepository;

    public SlackWorkspaceResolver(ConnectionService connectionRepository) {
        this.connectionRepository = connectionRepository;
    }

    /**
     * The workspace id of the ACTIVE Slack connection for {@code teamId}, if exactly one exists. More than one
     * means the one-active-per-team index was bypassed; picking either would route events to an arbitrary tenant.
     */
    public Optional<Long> resolveWorkspaceId(String teamId) {
        return connectionRepository.findUniqueActiveWorkspaceId(IntegrationKind.SLACK, teamId);
    }
}
