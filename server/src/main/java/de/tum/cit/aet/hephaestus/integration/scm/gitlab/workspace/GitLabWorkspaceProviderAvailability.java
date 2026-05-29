package de.tum.cit.aet.hephaestus.integration.scm.gitlab.workspace;

import de.tum.cit.aet.hephaestus.feature.FeatureFlag;
import de.tum.cit.aet.hephaestus.feature.FeatureFlagService;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.WorkspaceProviderAvailability;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabProperties;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Exposes the GitLab default server URL to the workspace creation wizard.
 *
 * <p>Gated by the {@link FeatureFlag#GITLAB_WORKSPACE_CREATION} flag. {@code GitLabProperties}
 * is always-on (the {@code hephaestus.integration.gitlab.enabled=false} branch still binds the record
 * via Spring's default-value semantics), so the availability check is purely flag-driven.
 */
@Component
public class GitLabWorkspaceProviderAvailability implements WorkspaceProviderAvailability {

    private final GitLabProperties gitLabProperties;
    private final FeatureFlagService featureFlagService;

    public GitLabWorkspaceProviderAvailability(
        GitLabProperties gitLabProperties,
        FeatureFlagService featureFlagService
    ) {
        this.gitLabProperties = gitLabProperties;
        this.featureFlagService = featureFlagService;
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.GITLAB;
    }

    @Override
    public Optional<String> hintUrl() {
        if (!featureFlagService.isEnabled(FeatureFlag.GITLAB_WORKSPACE_CREATION)) {
            return Optional.empty();
        }
        return Optional.of(gitLabProperties.defaultServerUrl());
    }
}
