package de.tum.cit.aet.hephaestus.agent.proxy;

import de.tum.cit.aet.hephaestus.agent.catalog.EgressPolicy;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelResolver;
import de.tum.cit.aet.hephaestus.agent.config.MemberAiRoutingAdapter;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJobRepository;
import de.tum.cit.aet.hephaestus.agent.job.ReviewMemberAiPolicy;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnWorkerRole;
import de.tum.cit.aet.hephaestus.mentor.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Recheck the live choice before each proxy request; queued snapshots are not permission. */
@Service
@ConditionalOnWorkerRole
@RequiredArgsConstructor
class ProxyRequestPolicy {
    private final EgressPolicy egress;
    private final ReviewJobRepository jobs;
    private final ChatMessageRepository messages;
    private final ReviewMemberAiPolicy reviews;
    private final MemberAiRoutingAdapter routing;

    void validateTarget(String baseUrl) {
        egress.validate(baseUrl);
    }

    @Transactional(readOnly = true)
    public boolean allows(ProxyRouting request) {
        Long workspaceId = request.workspaceId();
        var attempt = request.attempt();
        if (workspaceId == null || attempt == null) return false;
        var model = new LlmModelResolver.ConnectionRef(
                request.connectionScope(), request.connectionId(), request.modelId(), workspaceId);
        return switch (attempt.sourceType()) {
            // A model for practice scripts is checked like the review's own model: against the reviewed developer.
            case REVIEW_JOB, PRACTICE_SCRIPT_DECISION, PRACTICE_SCRIPT_EMBEDDING, PRACTICE_SCRIPT_RERANKING ->
                jobs.findByIdAndWorkspaceId(attempt.sourceId(), workspaceId)
                        .filter(job -> reviews.allows(job, model))
                        .isPresent();
            case MENTOR_TURN ->
                messages.findDeveloperIdByIdAndWorkspaceId(attempt.sourceId(), workspaceId)
                        .filter(developer -> routing.allows(workspaceId, developer, model))
                        .isPresent();
        };
    }
}
