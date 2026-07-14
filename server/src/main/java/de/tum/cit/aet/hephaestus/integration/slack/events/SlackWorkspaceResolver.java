package de.tum.cit.aet.hephaestus.integration.slack.events;

import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Resolves the workspace that owns the ACTIVE Slack {@code Connection} for a given team.
 *
 * <p>This is a <em>tenant resolution</em>, not a tenant-scoped read: the caller has only the Slack {@code team_id}
 * and must discover which workspace it maps to. The {@code connection} table is itself workspace-scoped, so a JPA
 * query keyed on {@code instance_key} alone would be rejected by the tenancy {@code StatementInspector} (there is no
 * {@code workspace_id} to predicate on — that is exactly what we are resolving). A narrow raw {@code JdbcTemplate}
 * read is the deliberate, isolated exception.
 */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true")
public class SlackWorkspaceResolver {

    private final JdbcTemplate jdbc;

    public SlackWorkspaceResolver(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** The workspace id of the ACTIVE Slack connection for {@code teamId}, if any. */
    public Optional<Long> resolveWorkspaceId(String teamId) {
        return jdbc.query(
            "SELECT workspace_id FROM connection WHERE kind = 'SLACK' AND instance_key = ? AND state = 'ACTIVE' LIMIT 1",
            rs -> rs.next() ? Optional.of(rs.getLong(1)) : Optional.<Long>empty(),
            teamId
        );
    }
}
