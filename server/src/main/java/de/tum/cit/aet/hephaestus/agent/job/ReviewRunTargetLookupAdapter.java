package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.practices.spi.ReviewRunTargetLookup;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class ReviewRunTargetLookupAdapter implements ReviewRunTargetLookup {

    private final AgentJobRepository repository;

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Target> findByJobIds(long workspaceId, Collection<UUID> jobIds) {
        if (jobIds.isEmpty()) {
            return Map.of();
        }
        return repository
            .findReviewRunTargets(workspaceId, jobIds)
            .stream()
            .collect(
                Collectors.toUnmodifiableMap(AgentJobRepository.ReviewRunTargetRow::getId, ReviewRunTargetMapper::from)
            );
    }
}
