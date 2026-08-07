package de.tum.cit.aet.hephaestus.integration.scm.domain.signal;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ArtifactIdentity;
import de.tum.cit.aet.hephaestus.integration.core.spi.ArtifactIdentityResolver;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmArtifactIdentityRepository.ScmArtifactLabel;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Names the two SCM artifact kinds, from the shared domain rather than from either vendor — the same
 * placement, and the same reason, as the descriptors next door: one entity, one label.
 */
@Configuration(proxyBeanMethods = false)
public class ScmArtifactIdentityConfiguration {

    @Bean
    ArtifactIdentityResolver pullRequestIdentityResolver(ScmArtifactIdentityRepository labels) {
        return new ScmIdentityResolver(ScmSignals.PULL_REQUEST, "Merge request", labels::findPullRequestLabels);
    }

    @Bean
    ArtifactIdentityResolver issueIdentityResolver(ScmArtifactIdentityRepository labels) {
        return new ScmIdentityResolver(ScmSignals.ISSUE, "Issue", labels::findIssueLabels);
    }

    /**
     * A deleted artifact still gets a title and loses its link. Dropping it would make work whose
     * review history we hold look like work we never saw — the reading the ledger exists to prevent —
     * and keeping the link would offer a door that no longer opens.
     */
    private record ScmIdentityResolver(
        ArtifactKind kind,
        String fallbackTitle,
        Function<Collection<Long>, List<ScmArtifactLabel>> lookup
    ) implements ArtifactIdentityResolver {
        @Override
        public Map<Long, ArtifactIdentity> resolve(long workspaceId, Collection<Long> artifactIds) {
            if (artifactIds.isEmpty()) {
                return Map.of();
            }
            Map<Long, ArtifactIdentity> resolved = new HashMap<>();
            for (ScmArtifactLabel label : lookup.apply(artifactIds)) {
                String title =
                    label.getTitle() == null || label.getTitle().isBlank() ? fallbackTitle : label.getTitle();
                resolved.put(
                    label.getId(),
                    new ArtifactIdentity(
                        kind,
                        label.getId(),
                        label.getNumber(),
                        title,
                        label.getContainer(),
                        label.getDeletedAt() == null ? label.getUrl() : null
                    )
                );
            }
            return Map.copyOf(resolved);
        }
    }
}
