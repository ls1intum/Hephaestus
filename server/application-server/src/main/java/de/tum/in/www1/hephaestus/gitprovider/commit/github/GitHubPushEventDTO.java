package de.tum.in.www1.hephaestus.gitprovider.commit.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubWebhookEvent;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.dto.GitHubRepositoryRefDTO;
import java.time.Instant;
import java.util.List;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * DTO for GitHub push webhook event payloads.
 *
 * @see <a href="https://docs.github.com/en/webhooks/webhook-events-and-payloads#push">
 *      GitHub Push Event Documentation</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubPushEventDTO(
    @NonNull @JsonProperty("ref") String ref,
    @NonNull @JsonProperty("before") String before,
    @NonNull @JsonProperty("after") String after,
    @JsonProperty("created") boolean created,
    @JsonProperty("deleted") boolean deleted,
    @JsonProperty("forced") boolean forced,
    @JsonProperty("compare") String compareUrl,
    @NonNull @JsonProperty("commits") List<PushCommit> commits,
    @JsonProperty("head_commit") @Nullable PushCommit headCommit,
    @NonNull @JsonProperty("repository") GitHubRepositoryRefDTO repository,
    @NonNull @JsonProperty("pusher") Pusher pusher,
    @JsonProperty("sender") @Nullable Sender sender,
    @JsonProperty("installation") @Nullable InstallationRef installation
) implements GitHubWebhookEvent {
    /**
     * Push events don't have a traditional "action" field.
     */
    @Override
    public String action() {
        return "pushed";
    }

    @Override
    public GitHubEventAction.Push actionType() {
        return GitHubEventAction.Push.PUSHED;
    }

    /**
     * A commit within a push event.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PushCommit(
        @JsonProperty("id") String sha,
        @JsonProperty("tree_id") String treeId,
        @JsonProperty("message") String message,
        @JsonProperty("timestamp") Instant timestamp,
        @JsonProperty("url") String url,
        @JsonProperty("author") CommitUser author,
        @JsonProperty("committer") CommitUser committer,
        @JsonProperty("added") List<String> added,
        @JsonProperty("removed") List<String> removed,
        @JsonProperty("modified") List<String> modified,
        @JsonProperty("distinct") boolean distinct
    ) {}

    /**
     * Author/committer identity within a commit.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CommitUser(
        @JsonProperty("name") String name,
        @JsonProperty("email") String email,
        @JsonProperty("username") @Nullable String username
    ) {}

    /**
     * The user who pushed the commits.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Pusher(@JsonProperty("name") String name, @JsonProperty("email") String email) {}

    /**
     * The GitHub user who triggered the event.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Sender(@JsonProperty("id") Long id, @JsonProperty("login") String login) {}

    /**
     * Reference to the GitHub App installation.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InstallationRef(@JsonProperty("id") Long id, @JsonProperty("node_id") String nodeId) {}
}
