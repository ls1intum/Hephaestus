package de.tum.cit.aet.hephaestus.agent.context.providers.mentor;

import de.tum.cit.aet.hephaestus.core.auth.spi.AccountSummaryQuery;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.evidence.capture.ContentSource;
import de.tum.cit.aet.hephaestus.evidence.capture.MentorContextRequest;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

// Read personal context for each turn; fetch work details on demand rather than mounting every diff.
@Component
@RequiredArgsConstructor
public class RecentAuthoredWorkContentSource implements ContentSource {

    /** Workspace-relative output key. Whitelisted in {@code MentorContextKeys#ALLOWED_OUTPUT_KEYS}. */
    public static final String OUTPUT_KEY = OUTPUT_PREFIX + "recent_authored_work.json";

    private static final int MAX_PULL_REQUESTS = 20;
    private static final int MAX_ISSUES = 20;
    private final AccountSummaryQuery accounts;
    private final MentorContextQueryRepository queryRepository;
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(MentorContextRequest request) {
        return true;
    }

    @Override
    public boolean required() {
        return false;
    }

    /** Tx-on-contribute / not-on-buildPayload AOP convention documented at {@link MentorContextKeys}. */
    @Override
    @Transactional(readOnly = true)
    public void contribute(MentorContextRequest request, Map<String, byte[]> files) {
        MentorContextRequest req = request;
        ObjectNode payload = buildPayload(req.workspaceId(), req.developerId());
        try {
            files.put(OUTPUT_KEY, objectMapper.writeValueAsBytes(payload));
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize recent authored work context", e);
        }
    }

    /** Reads the current bounded context; formatted personal copies are not retained between turns. */
    public ObjectNode buildPayload(Long workspaceId, Long developerId) {
        var account = accounts.findAllByIds(List.of(developerId)).get(developerId);
        if (account == null) throw new EntityNotFoundException("Account", developerId.toString());

        List<PullRequest> prs = queryRepository.findRecentAuthoredPullRequests(
                workspaceId, developerId, PageRequest.of(0, MAX_PULL_REQUESTS));
        List<Issue> issues =
                queryRepository.findRecentAuthoredIssues(workspaceId, developerId, PageRequest.of(0, MAX_ISSUES));

        ObjectNode root = objectMapper.createObjectNode();
        root.putObject("account").put("displayName", account.displayName());

        ArrayNode prArr = root.putArray("pullRequests");
        for (PullRequest pr : prs) {
            ObjectNode node = prArr.addObject();
            node.put("sourceId", pr.getId());
            node.put("resource", MergeReadinessContentSource.resourceOf(pr.getId()));
            node.put(
                    "reviewsResource",
                    ReviewAttemptsContentSource.resourceOf(new ItemKind("scm.pull_request"), pr.getId()));
            node.put("number", pr.getNumber());
            node.put("title", pr.getTitle());
            if (pr.getHtmlUrl() != null) {
                node.put("url", pr.getHtmlUrl());
            }
            if (pr.getState() != null) {
                node.put("state", pr.getState().name());
            }
            node.put("isDraft", pr.isDraft());
            node.put("additions", pr.getAdditions());
            node.put("deletions", pr.getDeletions());
            if (pr.getHeadRefName() != null) {
                node.put("branch", pr.getHeadRefName());
            }
        }

        ArrayNode issueArr = root.putArray("issues");
        for (Issue issue : issues) {
            ObjectNode node = issueArr.addObject();
            node.put("sourceId", issue.getId());
            node.put(
                    "reviewsResource",
                    ReviewAttemptsContentSource.resourceOf(new ItemKind("scm.issue"), issue.getId()));
            node.put("number", issue.getNumber());
            node.put("title", issue.getTitle());
            if (issue.getHtmlUrl() != null) {
                node.put("url", issue.getHtmlUrl());
            }
            if (issue.getState() != null) {
                node.put("state", issue.getState().name());
            }
        }
        return root;
    }
}
