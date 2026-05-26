package de.tum.cit.aet.hephaestus.integration.github.feedback;

import static de.tum.cit.aet.hephaestus.integration.github.feedback.GithubPrNodeIdResolver.GRAPHQL_TIMEOUT;

import de.tum.cit.aet.hephaestus.integration.github.common.GitHubGraphQlClientProvider;
import de.tum.cit.aet.hephaestus.integration.github.feedback.GithubFeedbackChannel.PrCoordinates;
import de.tum.cit.aet.hephaestus.integration.spi.FeedbackChannel;
import de.tum.cit.aet.hephaestus.integration.spi.FeedbackDeliveryException;
import de.tum.cit.aet.hephaestus.integration.spi.FindingAnchor;
import de.tum.cit.aet.hephaestus.integration.spi.InlineFindingChannel;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.stereotype.Component;

/**
 * GitHub adapter for {@link InlineFindingChannel}. Posts all inline findings as a
 * single atomic {@code addPullRequestReview} mutation with embedded threads — one
 * notification per review, all-or-nothing semantics for the batch.
 *
 * <p>Non-{@link FindingAnchor.DiffAnchor} anchors are counted as failed and logged;
 * GitHub has no analogue for document/channel/issue anchors on a PR review.
 *
 * <p>The commit SHA the review is anchored to is read from the {@link FeedbackChannel.FeedbackTarget#resourceUrl}
 * field — the agent layer encodes the head commit there so the channel doesn't need
 * to re-resolve PR metadata.
 */
@Component
public class GithubInlineFindingChannel implements InlineFindingChannel {

    private static final Logger log = LoggerFactory.getLogger(GithubInlineFindingChannel.class);

    private final GitHubGraphQlClientProvider gitHubProvider;
    private final GithubPrNodeIdResolver prNodeIdResolver;

    public GithubInlineFindingChannel(
        GitHubGraphQlClientProvider gitHubProvider,
        GithubPrNodeIdResolver prNodeIdResolver
    ) {
        this.gitHubProvider = gitHubProvider;
        this.prNodeIdResolver = prNodeIdResolver;
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.GITHUB;
    }

    @Override
    public InlineResult postInlineFindings(FeedbackChannel.FeedbackTarget target, List<InlineFinding> findings) {
        if (findings == null || findings.isEmpty()) {
            return new InlineResult(0, 0);
        }
        long scopeId = target.ref().workspaceId();
        if (gitHubProvider.isRateLimitCritical(scopeId)) {
            log.warn(
                "GitHub rate limit critical — skipping {} inline findings: workspaceId={}",
                findings.size(),
                scopeId
            );
            return new InlineResult(0, findings.size());
        }

        // Build threads array; track non-DiffAnchor findings as failed.
        List<Map<String, Object>> threads = new ArrayList<>(findings.size());
        int unsupportedAnchorCount = 0;
        for (InlineFinding finding : findings) {
            if (!(finding.anchor() instanceof FindingAnchor.DiffAnchor diff)) {
                log.warn("Skipping non-diff anchor on GitHub inline finding: anchor={}", finding.anchor());
                unsupportedAnchorCount++;
                continue;
            }
            if (finding.body() == null || finding.body().isBlank()) {
                continue;
            }
            threads.add(buildThread(diff, finding.body()));
        }

        if (threads.isEmpty()) {
            log.debug("All inline findings were skipped (unsupported or empty): workspaceId={}", scopeId);
            return new InlineResult(0, unsupportedAnchorCount);
        }

        PrCoordinates pr = GithubFeedbackChannel.parseSubjectExternalId(target.subjectExternalId());
        String prNodeId = prNodeIdResolver.resolve(scopeId, pr.owner(), pr.name(), pr.number());
        String commitOid = target.resourceUrl(); // agent encodes head SHA here

        try {
            ClientGraphQlResponse response = gitHubProvider
                .forScope(scopeId)
                .documentName("AddPullRequestReviewWithThreads")
                .variable("pullRequestId", prNodeId)
                .variable("event", "COMMENT")
                .variable("commitOID", commitOid)
                .variable("threads", threads)
                .execute()
                .block(GRAPHQL_TIMEOUT);

            if (response == null) {
                throw new FeedbackDeliveryException("Null response from AddPullRequestReviewWithThreads");
            }
            gitHubProvider.trackRateLimit(scopeId, response);

            if (response.getErrors() != null && !response.getErrors().isEmpty()) {
                log.warn(
                    "GitHub addPullRequestReview with threads failed: workspaceId={}, errors={}, threadCount={}",
                    scopeId,
                    response.getErrors(),
                    threads.size()
                );
                return new InlineResult(0, threads.size() + unsupportedAnchorCount);
            }

            log.info(
                "Posted {} GitHub inline findings as single review: workspaceId={}, prNodeId={}",
                threads.size(),
                scopeId,
                prNodeId
            );
            return new InlineResult(threads.size(), unsupportedAnchorCount);
        } catch (FeedbackDeliveryException e) {
            throw e;
        } catch (Exception e) {
            log.warn("GitHub inline finding batch failed: workspaceId={}, threadCount={}", scopeId, threads.size(), e);
            return new InlineResult(0, threads.size() + unsupportedAnchorCount);
        }
    }

    /** Builds a GitHub review-thread payload from a {@link FindingAnchor.DiffAnchor}. */
    private static Map<String, Object> buildThread(FindingAnchor.DiffAnchor diff, String body) {
        Map<String, Object> thread = new HashMap<>();
        thread.put("path", diff.filePath());
        thread.put("body", body);

        Integer startLine = diff.startLine();
        boolean isMultiLine = startLine != null && startLine < diff.newLineNumber();
        if (isMultiLine) {
            thread.put("startLine", startLine);
            thread.put("line", diff.newLineNumber());
            thread.put("side", "RIGHT");
            thread.put("startSide", "RIGHT");
        } else {
            thread.put("line", diff.newLineNumber());
            thread.put("side", "RIGHT");
        }
        return thread;
    }
}
