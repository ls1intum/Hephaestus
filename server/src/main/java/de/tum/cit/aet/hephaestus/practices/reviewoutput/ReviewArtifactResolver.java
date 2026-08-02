package de.tum.cit.aet.hephaestus.practices.reviewoutput;

import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewArtifactDTO;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRunTargetLookup;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRunTargetLookup.Target;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class ReviewArtifactResolver {

    private final ReviewRunTargetLookup targetLookup;

    Map<ArtifactRef, ReviewArtifactDTO> resolve(Long workspaceId, Collection<ArtifactRef> refs) {
        Map<UUID, Target> targets = targetLookup.findByJobIds(
            workspaceId,
            refs.stream().map(ArtifactRef::jobId).distinct().toList()
        );
        return refs
            .stream()
            .distinct()
            .collect(Collectors.toUnmodifiableMap(Function.identity(), ref -> resolve(ref, targets.get(ref.jobId()))));
    }

    private static ReviewArtifactDTO resolve(ArtifactRef ref, Target target) {
        if (target == null || target.type() != ref.type() || (target.id() != null && !target.id().equals(ref.id()))) {
            return unresolved(ref);
        }
        return new ReviewArtifactDTO(
            ref.type(),
            ref.id(),
            target.provider(),
            target.number(),
            target.title(),
            target.repositoryName(),
            target.channelName(),
            target.url()
        );
    }

    private static ReviewArtifactDTO unresolved(ArtifactRef ref) {
        String title = switch (ref.type()) {
            case PULL_REQUEST -> "Pull request";
            case ISSUE -> "Issue";
            case CONVERSATION_THREAD -> "Conversation";
        };
        return new ReviewArtifactDTO(ref.type(), ref.id(), null, null, title, null, null, null);
    }

    record ArtifactRef(UUID jobId, WorkArtifact type, Long id) {
        ArtifactRef {
            Objects.requireNonNull(jobId);
            Objects.requireNonNull(type);
            Objects.requireNonNull(id);
        }
    }
}
