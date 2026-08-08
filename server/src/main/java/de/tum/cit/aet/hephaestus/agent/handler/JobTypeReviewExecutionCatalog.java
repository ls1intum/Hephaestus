package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.handler.spi.JobTypeHandler;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobService;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ArtifactDescriptor;
import de.tum.cit.aet.hephaestus.integration.core.spi.ReviewExecutionCatalog;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Answers the review contract's executability question with the only thing that can settle it: the
 * handlers this build actually registered.
 *
 * <p>Derived rather than declared. A hand-written list of executable kinds is one more declaration to keep
 * in step, and the failure it permits is {@link ArtifactDescriptor#reviewable()} returning true for a kind
 * with no way to run. Reading the registered {@link JobTypeHandler} beans means the answer cannot go
 * stale: deleting a handler makes its kind unexecutable in the same commit, and the contract refuses to
 * start.
 *
 * <p>Takes the handler beans directly instead of {@link JobTypeHandlerRegistry} only to keep the
 * dependency one bean shallower; the registry's own constructor already refuses a build where an
 * {@code AgentJobType} has no handler.
 */
@Component
public class JobTypeReviewExecutionCatalog implements ReviewExecutionCatalog {

    private final Set<ArtifactKind> executableKinds;

    public JobTypeReviewExecutionCatalog(List<JobTypeHandler> handlers) {
        this.executableKinds = handlers
            .stream()
            .map(handler -> AgentJobService.artifactKindFor(handler.jobType()))
            .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Set<ArtifactKind> executableKinds() {
        return executableKinds;
    }
}
