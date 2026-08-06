package de.tum.cit.aet.hephaestus.integration.core.conformance;

import de.tum.cit.aet.hephaestus.integration.core.spi.ArtifactDescriptor;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationManifest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.IssueArtifactDescriptor;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.PullRequestArtifactDescriptor;
import de.tum.cit.aet.hephaestus.integration.scm.github.manifest.GitHubManifest;
import java.util.List;

/** GitHub through the shared acceptance suite. */
class GitHubManifestContractTest extends IntegrationManifestContractTest {

    @Override
    protected IntegrationManifest manifest() {
        return new GitHubManifest();
    }

    @Override
    protected List<ArtifactDescriptor> descriptors() {
        return List.of(new PullRequestArtifactDescriptor(), new IssueArtifactDescriptor());
    }
}
