package de.tum.cit.aet.hephaestus.integration.github.lifecycle;

import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationLifecycleListener;
import org.springframework.stereotype.Component;

/**
 * GitHub marker. {@code WorkspaceInstallationService} still owns the canonical
 * install/uninstall write path; this listener exists so the framework's per-kind
 * dispatch resolves. All methods inherit the interface's default no-op.
 */
@Component
public class GithubLifecycleListener implements IntegrationLifecycleListener {

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.GITHUB;
    }
}
