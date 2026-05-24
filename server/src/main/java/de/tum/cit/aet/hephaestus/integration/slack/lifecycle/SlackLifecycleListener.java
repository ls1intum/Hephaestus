package de.tum.cit.aet.hephaestus.integration.slack.lifecycle;

import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationLifecycleListener;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Slack lifecycle listener — skeleton.
 *
 * <p>The wiring from {@code app_uninstalled} / {@code tokens_revoked} →
 * {@code IntegrationState.UNINSTALLED} transition lands with #1204. Same for the
 * {@code member_joined_channel} → {@link #onScopeChanged} path. For #1198 we
 * register the bean so {@code IntegrationFrameworkBootstrap} sees the SPI
 * coverage; method bodies log + no-op intentionally.
 */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true", matchIfMissing = true)
public class SlackLifecycleListener implements IntegrationLifecycleListener {

    private static final Logger log = LoggerFactory.getLogger(SlackLifecycleListener.class);

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.SLACK;
    }

    @Override
    public void onInstanceInstalled(InstanceProvisioned event) {
        // TODO(#1204): mark Connection ACTIVE + persist TenantAccount(team_id, team_name).
        log.info("Slack onInstanceInstalled stub: ref={} account={}", event.ref(), event.account());
    }

    @Override
    public void onInstanceUninstalled(IntegrationRef ref) {
        // TODO(#1204): transition Connection → UNINSTALLED on app_uninstalled / tokens_revoked.
        log.info("Slack onInstanceUninstalled stub: ref={}", ref);
    }

    @Override
    public void onScopeChanged(IntegrationRef ref, ScopeDelta delta) {
        // TODO(#1204): persist channel additions/removals from member_joined_channel /
        // member_left_channel + channel_archive / channel_unarchive events.
        log.info("Slack onScopeChanged stub: ref={} added={} removed={}",
            ref, delta.added().size(), delta.removedExternalIds().size());
    }

    @Override
    public void onTenantRenamed(IntegrationRef ref, String oldName, String newName) {
        // TODO(#1204): update Connection.displayName on team_rename event.
        log.info("Slack onTenantRenamed stub: ref={} {} → {}", ref, oldName, newName);
    }
}
