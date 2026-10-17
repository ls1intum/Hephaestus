package de.tum.cit.aet.hephaestus.agent.practice.live;

import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.scm.domain.capture.IssueContentSource;
import de.tum.cit.aet.hephaestus.integration.scm.domain.capture.PullRequestContentSource;
import de.tum.cit.aet.hephaestus.integration.scm.domain.capture.ScmCapturedWork;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

// Use the content sources' work records so live fixtures cannot invent an input shape the server never writes.
final class LiveWorkRecords {

    static final String REPOSITORY = "test/fixture";
    static final long REPOSITORY_ID = 1L;
    static final Instant AT = Instant.parse("2026-10-01T09:00:00Z");

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private LiveWorkRecords() {}

    static ScmCapturedWork capturedPullRequest(
            long pullRequestId,
            long repositoryId,
            int number,
            String head,
            String title,
            @Nullable String body,
            String source) {
        return new ScmCapturedWork(
                pullRequestId,
                repositoryId,
                number,
                "https://github.com/" + REPOSITORY + "/pull/" + number,
                REPOSITORY,
                title,
                body,
                head,
                null,
                source,
                "main",
                null,
                null);
    }

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
                pullRequest, capturedPullRequest(1L, REPOSITORY_ID, 1, head, title, body, "feat/login"), AT);
    }

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
        return IssueContentSource.workRecord(
                issue,
                new ScmCapturedWork(
                        1L,
                        REPOSITORY_ID,
                        1,
                        issue.getHtmlUrl(),
                        REPOSITORY,
                        title,
                        body,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null),
                null);
    }

    static byte[] staged(JsonNode record) {
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(record);
    }

    private static Repository repository() {
        Repository repository = new Repository();
        repository.setId(REPOSITORY_ID);
        var provider = new IdentityProvider();
        provider.setId(1L);
        repository.setProvider(provider);
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
