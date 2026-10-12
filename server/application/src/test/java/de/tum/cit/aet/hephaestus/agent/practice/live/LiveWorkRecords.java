package de.tum.cit.aet.hephaestus.agent.practice.live;

import de.tum.cit.aet.hephaestus.agent.context.providers.IssueContentSource;
import de.tum.cit.aet.hephaestus.agent.context.providers.PullRequestContentSource;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The work records that the live harnesses stage as {@code context/metadata.json}, built by the content sources'
 * own {@code workRecord}, so that a harness can never stage a shape the server does not write.
 * {@code LiveWorkRecordsTest} pins them to the files that {@code lib/contract.test.ts} parses with the runner's schema.
 */
final class LiveWorkRecords {

    static final String REPOSITORY = "test/fixture";
    static final long REPOSITORY_ID = 1L;
    /** A fixed instant, so that a record does not change between two runs. */
    static final Instant AT = Instant.parse("2026-10-01T09:00:00Z");

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private LiveWorkRecords() {}

    /** The metadata a pull request review is admitted with, as the job carries it. */
    static ObjectNode pullRequestJob(
            long pullRequestId,
            long repositoryId,
            int number,
            String head,
            String title,
            @Nullable String body,
            String source) {
        return MAPPER.createObjectNode()
                .put("pull_request_id", pullRequestId)
                .put("repository_id", repositoryId)
                .put("repository_full_name", REPOSITORY)
                .put("pr_number", number)
                .put("pr_url", "https://github.com/" + REPOSITORY + "/pull/" + number)
                .put("commit_sha", head)
                .put("source_branch", source)
                .put("target_branch", "main")
                .put("title", title)
                .put("body", body);
    }

    /** An open pull request of the fixture repository, by its author, at {@code head}. */
    static ObjectNode pullRequest(String head, String title, @Nullable String body, int additions) {
        PullRequest pullRequest = new PullRequest();
        pullRequest.setNumber(1);
        pullRequest.setTitle(title);
        pullRequest.setBody(body);
        pullRequest.setState(Issue.State.OPEN);
        pullRequest.setHtmlUrl("https://github.com/" + REPOSITORY + "/pull/1");
        pullRequest.setRepository(repository());
        pullRequest.setAuthor(author());
        pullRequest.setCreatedAt(AT);
        pullRequest.setLastSyncAt(AT);
        pullRequest.setAdditions(additions);
        pullRequest.setChangedFiles(1);
        pullRequest.setHeadRefOid(head);
        return PullRequestContentSource.workRecord(
                pullRequest, pullRequestJob(1L, REPOSITORY_ID, 1, head, title, body, "feat/login"), AT);
    }

    /** An open issue of the fixture repository, by its author. */
    static ObjectNode issue(String title, String body) {
        Issue issue = new Issue();
        issue.setNumber(1);
        issue.setTitle(title);
        issue.setBody(body);
        issue.setState(Issue.State.OPEN);
        issue.setHtmlUrl("https://github.com/" + REPOSITORY + "/issues/1");
        issue.setRepository(repository());
        issue.setAuthor(author());
        issue.setCreatedAt(AT);
        JsonNode job = MAPPER.createObjectNode()
                .put("issue_id", 1L)
                .put("repository_id", REPOSITORY_ID)
                .put("issue_number", 1);
        return IssueContentSource.workRecord(issue, job, null);
    }

    /** The bytes the content source stages: its mapper's default pretty printer. */
    static byte[] staged(JsonNode record) {
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(record);
    }

    private static Repository repository() {
        Repository repository = new Repository();
        repository.setId(REPOSITORY_ID);
        repository.setName("fixture");
        repository.setNameWithOwner(REPOSITORY);
        repository.setHtmlUrl("https://github.com/" + REPOSITORY);
        return repository;
    }

    private static User author() {
        User author = new User();
        author.setNativeId(11L);
        author.setLogin("fixture-author");
        author.setType(User.Type.USER);
        return author;
    }
}
