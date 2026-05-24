package de.tum.cit.aet.hephaestus.integration.outline.lifecycle;

import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationLifecycleListener;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Outline lifecycle listener — skeleton.
 *
 * <p>Wires up in #1203 alongside the subscription-store + OAuth client. For #1198
 * methods are stubs so {@code IntegrationFrameworkBootstrap} sees lifecycle coverage.
 */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.outline.enabled", havingValue = "true", matchIfMissing = true)
public class OutlineLifecycleListener implements IntegrationLifecycleListener {

    private static final Logger log = LoggerFactory.getLogger(OutlineLifecycleListener.class);

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.OUTLINE;
    }

    @Override
    public void onInstanceInstalled(InstanceProvisioned event) {
        // TODO(#1203): mark Connection ACTIVE + persist team.id/team.name, then register
        // a webhook subscription via POST /api/webhookSubscriptions.create and store the
        // returned signing secret.
        log.info("Outline onInstanceInstalled stub: ref={} account={}", event.ref(), event.account());
    }

    @Override
    public void onInstanceUninstalled(IntegrationRef ref) {
        // TODO(#1203): transition Connection → UNINSTALLED + delete webhook subscriptions.
        log.info("Outline onInstanceUninstalled stub: ref={}", ref);
    }

    @Override
    public void onScopeChanged(IntegrationRef ref, ScopeDelta delta) {
        // TODO(#1203): persist collection additions/removals from collections.create /
        // collections.delete events.
        log.info("Outline onScopeChanged stub: ref={} added={} removed={}",
            ref, delta.added().size(), delta.removedExternalIds().size());
    }

    @Override
    public void onTenantRenamed(IntegrationRef ref, String oldName, String newName) {
        // TODO(#1203): update Connection.displayName on team rename.
        log.info("Outline onTenantRenamed stub: ref={} {} → {}", ref, oldName, newName);
    }
}
