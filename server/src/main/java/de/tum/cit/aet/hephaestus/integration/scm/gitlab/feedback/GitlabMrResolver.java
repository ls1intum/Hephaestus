package de.tum.cit.aet.hephaestus.integration.scm.gitlab.feedback;

import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackDeliveryException;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabGraphQlClientProvider;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Resolves a GitLab MR's global ID + diffRefs in a single GraphQL round-trip
 * ({@code GetMergeRequestGlobalId}). Shared by the three GitLab feedback adapters
 * ({@link GitlabFeedbackChannel}, {@link GitlabInlineFindingChannel},
 * {@link GitlabApprovalChannel}) so per-channel mutations don't re-roundtrip the same
 * lookup.
 *
 * <p>Gated on {@code hephaestus.gitlab.enabled=true} to track
 * {@link GitLabGraphQlClientProvider} — the channel beans only load when the GitLab
 * GraphQL provider does.
 */
@Component
@ConditionalOnProperty(prefix = "hephaestus.gitlab", name = "enabled", havingValue = "true")
class GitlabMrResolver {

    static final Duration GRAPHQL_TIMEOUT = Duration.ofSeconds(15);

    private final GitLabGraphQlClientProvider gitLabProvider;

    GitlabMrResolver(GitLabGraphQlClientProvider gitLabProvider) {
        this.gitLabProvider = gitLabProvider;
    }

    MrInfo resolve(long scopeId, String projectPath, int mrIid) {
        ClientGraphQlResponse response = gitLabProvider
            .forScope(scopeId)
            .documentName("GetMergeRequestGlobalId")
            .variable("fullPath", projectPath)
            .variable("iid", String.valueOf(mrIid))
            .execute()
            .block(GRAPHQL_TIMEOUT);

        if (response == null) {
            throw new FeedbackDeliveryException("Null response resolving MR info: " + projectPath + "!" + mrIid);
        }

        String globalId = response.field("project.mergeRequest.id").getValue();
        if (globalId == null) {
            List<?> errors = response.getErrors();
            throw new FeedbackDeliveryException(
                "MR not found via GraphQL: " +
                    projectPath +
                    "!" +
                    mrIid +
                    (errors.isEmpty() ? "" : ", errors=" + errors)
            );
        }

        // diffRefs may be null if the MR has no diffs yet
        String baseSha = response.field("project.mergeRequest.diffRefs.baseSha").getValue();
        String headSha = response.field("project.mergeRequest.diffRefs.headSha").getValue();
        String startSha = response.field("project.mergeRequest.diffRefs.startSha").getValue();

        return new MrInfo(globalId, baseSha, headSha, startSha);
    }

    /**
     * Splits {@code "project/full/path!42"} (GitLab MR external-id convention) into
     * the path + iid components needed by GraphQL.
     */
    static MrCoordinates parseSubjectExternalId(String subjectExternalId) {
        if (subjectExternalId == null || subjectExternalId.isBlank()) {
            throw new FeedbackDeliveryException("subjectExternalId is required for GitLab MR feedback");
        }
        int bangIdx = subjectExternalId.lastIndexOf('!');
        if (bangIdx <= 0 || bangIdx == subjectExternalId.length() - 1) {
            throw new FeedbackDeliveryException(
                "Invalid GitLab MR subjectExternalId (expected project/path!iid): " + subjectExternalId
            );
        }
        String projectPath = subjectExternalId.substring(0, bangIdx);
        int iid;
        try {
            iid = Integer.parseInt(subjectExternalId.substring(bangIdx + 1));
        } catch (NumberFormatException e) {
            throw new FeedbackDeliveryException(
                "Invalid GitLab MR subjectExternalId — iid must be integer: " + subjectExternalId
            );
        }
        return new MrCoordinates(projectPath, iid);
    }

    record MrInfo(String globalId, @Nullable String baseSha, @Nullable String headSha, @Nullable String startSha) {}

    record MrCoordinates(String projectPath, int iid) {}
}
