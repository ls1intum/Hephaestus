package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelResolver;
import de.tum.cit.aet.hephaestus.agent.config.AgentPurpose;
import de.tum.cit.aet.hephaestus.agent.config.ConfigSnapshot;
import de.tum.cit.aet.hephaestus.agent.config.MemberAiRoutingAdapter;
import de.tum.cit.aet.hephaestus.agent.config.WorkspaceAgentBinding;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonProcessingSuppression;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import de.tum.cit.aet.hephaestus.workspace.spi.MemberAiPreferences;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Every account whose work enters an invocation must permit its frozen model route. */
@Service
@ConditionalOnCaptureRole
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewMemberAiPolicy {
    private final MemberAiRoutingAdapter routing;
    private final ObjectMapper objectMapper;
    private final MemberAiPreferences preferences;
    private final PersonProcessingSuppression suppression;
    private final ReviewJobAssignments assignments;

    public Optional<WorkspaceAgentBinding> binding(ReviewJob job) {
        return binding(job, AgentPurpose.PRACTICE_REVIEW);
    }

    public Optional<WorkspaceAgentBinding> practiceScriptBinding(ReviewJob job, AgentPurpose purpose) {
        return binding(job, purpose);
    }

    private Optional<WorkspaceAgentBinding> binding(ReviewJob job, AgentPurpose purpose) {
        if (!permitsReview(job)) return Optional.empty();
        return routing.bindingForPeople(job.getWorkspaceId(), purpose, people(job));
    }

    public boolean isProcessingSuppressed(ReviewJob job) {
        if (suppression.isReviewJobSuppressed(job.getId())) return true;
        return assignments.load(job).stream()
                .anyMatch(assignment ->
                        (assignment.developerId() != null && suppression.isAccountSuppressed(assignment.developerId()))
                                || (assignment.item().sourceId() != null
                                        && suppression.isSourceSuppressed(
                                                job.getWorkspaceId(),
                                                assignment.item().kind().value(),
                                                assignment.item().sourceId())));
    }

    public boolean permitsReview(ReviewJob job) {
        return !isProcessingSuppressed(job)
                && people(job).stream()
                        .allMatch(person -> person == null
                                || preferences
                                        .forDeveloper(job.getWorkspaceId(), person)
                                        .permitsAi());
    }

    public boolean allows(ReviewJob job, LlmModelResolver.ConnectionRef model) {
        return permitsReview(job)
                && people(job).stream().allMatch(person -> routing.allows(job.getWorkspaceId(), person, model));
    }

    public boolean allowsResult(ReviewJob job) {
        return permitsReview(job) && people(job).stream().allMatch(person -> allowsPerson(job, person));
    }

    public boolean allowsPerson(ReviewJob job, @Nullable Long accountId) {
        if (accountId != null && suppression.isAccountSuppressed(accountId)) return false;
        if (accountId != null
                && !preferences.forDeveloper(job.getWorkspaceId(), accountId).permitsAi()) return false;
        try {
            var snapshot = ConfigSnapshot.fromJson(job.getConfigSnapshot(), objectMapper);
            return routing.allows(
                    job.getWorkspaceId(),
                    accountId,
                    new LlmModelResolver.ConnectionRef(
                            snapshot.connectionScope(),
                            snapshot.connectionId(),
                            snapshot.modelId(),
                            snapshot.workspaceId()));
        } catch (IllegalArgumentException | IllegalStateException | JacksonException invalid) {
            return false;
        }
    }

    private List<@Nullable Long> people(ReviewJob job) {
        List<@Nullable Long> people = new ArrayList<>();
        for (var assignment : assignments.load(job)) {
            Long developer = assignment.developerId();
            if (!people.contains(developer)) people.add(developer);
        }
        return people;
    }
}
