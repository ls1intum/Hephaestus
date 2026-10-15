package de.tum.cit.aet.hephaestus.integration.scm.gitlab.repository;

import static de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabSyncConstants.extractNumericId;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.RepositoryIdentityLookup;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "hephaestus.integration.gitlab.enabled", havingValue = "true", matchIfMissing = false)
public class GitLabRepositoryIdentityLookup implements RepositoryIdentityLookup {
    private final GitLabProjectSyncService projects;

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.GITLAB;
    }

    @Override
    public Optional<RepositoryIdentity> resolve(long workspaceId, String fullPath) {
        return projects.fetchProject(workspaceId, fullPath).flatMap(project -> {
            String reportedPath = project.fullPath();
            if (reportedPath == null) return Optional.empty();
            return Optional.of(new RepositoryIdentity(extractNumericId(project.id()), reportedPath));
        });
    }
}
