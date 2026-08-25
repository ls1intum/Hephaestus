package de.tum.cit.aet.hephaestus.integration.core.conformance;

import de.tum.cit.aet.hephaestus.integration.core.spi.ArtifactDescriptor;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationManifest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.IssueArtifactDescriptor;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.PullRequestArtifactDescriptor;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.manifest.GitLabManifest;
import java.util.List;

/**
 * GitLab through the shared acceptance suite.
 *
 * <p>The reason this suite exists in the shape it does: GitLab is opt-in and off by default, so nothing
 * on a normal instance — including CI's Spring contexts — ever validates its declaration. Its manifest is
 * constructed with the stack enabled, which is the only configuration in which the bean exists at all.
 */
class GitLabManifestContractTest extends IntegrationManifestContractTest {

    @Override
    protected IntegrationManifest manifest() {
        return new GitLabManifest(true);
    }

    @Override
    protected List<ArtifactDescriptor> descriptors() {
        return List.of(new PullRequestArtifactDescriptor(), new IssueArtifactDescriptor());
    }
}
