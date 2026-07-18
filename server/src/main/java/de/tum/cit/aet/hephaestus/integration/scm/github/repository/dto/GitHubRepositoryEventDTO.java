package de.tum.cit.aet.hephaestus.integration.scm.github.repository.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.cit.aet.hephaestus.integration.scm.github.common.GitHubEventAction;
import de.tum.cit.aet.hephaestus.integration.scm.github.common.GitHubWebhookEvent;
import de.tum.cit.aet.hephaestus.integration.scm.github.user.dto.GitHubUserDTO;
import java.util.Map;

/**
 * DTO for GitHub repository webhook events (created, deleted, archived, renamed, etc.).
 * <p>
 * Used to handle repository-level events that affect the repository entity itself,
 * not events within the repository (issues, PRs, etc.).
 *
 * @see <a href="https://docs.github.com/en/webhooks/webhook-events-and-payloads#repository">
 *      GitHub Repository Webhook Events</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubRepositoryEventDTO(
    @JsonProperty("action") String action,
    @JsonProperty("repository") GitHubRepositoryRefDTO repository,
    @JsonProperty("changes") Changes changes,
    @JsonProperty("sender") GitHubUserDTO sender,
    @JsonProperty("installation") InstallationRef installation
) implements GitHubWebhookEvent {
    /**
     * Reference to the GitHub App installation.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InstallationRef(@JsonProperty("id") Long id, @JsonProperty("node_id") String nodeId) {}

    @Override
    public GitHubEventAction.Repository actionType() {
        return GitHubEventAction.Repository.fromString(action);
    }

    @Override
    public GitHubRepositoryRefDTO repository() {
        return repository;
    }

    /**
     * Changes object for rename events.
     * Contains the previous values before the change.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Changes(
        @JsonProperty("repository") RepositoryChanges repository,
        @JsonProperty("owner") OwnerChanges owner
    ) {
        /**
         * Repository-level changes (e.g., name change on rename).
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record RepositoryChanges(@JsonProperty("name") NameChange name) {}

        /**
         * Owner-level changes (e.g., login change on transfer).
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record OwnerChanges(@JsonProperty("from") Map<String, Object> from) {}

        /**
         * Name change details.
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record NameChange(@JsonProperty("from") String from) {}
    }

    /**
     * Gets the old repository name if this is a rename event.
     *
     * @return the old name, or null if not a rename event or changes not available
     */
    public String getOldName() {
        if (changes == null || changes.repository() == null || changes.repository().name() == null) {
            return null;
        }
        return changes.repository().name().from();
    }

    /**
     * Constructs the old full name (owner/repo) for rename events.
     *
     * @return the old full name, or null if not a rename event or data not available
     */
    public String getOldFullName() {
        String oldName = getOldName();
        if (oldName == null || repository == null || repository.fullName() == null) {
            return null;
        }
        // Extract owner from current full_name (owner/repo) and combine with old name
        String fullName = repository.fullName();
        int slashIndex = fullName.indexOf('/');
        if (slashIndex > 0) {
            String owner = fullName.substring(0, slashIndex);
            return owner + "/" + oldName;
        }
        return null;
    }

    /**
     * The repository's {@code owner/name} before a rename <em>or</em> a transfer — the single lookup
     * key for locating the already-mirrored row when its stable id is unavailable.
     * <ul>
     *   <li><b>renamed</b>: {@code changes.repository.name.from} under the unchanged owner (delegates
     *       to {@link #getOldFullName()});</li>
     *   <li><b>transferred</b>: the unchanged repository name under
     *       {@code changes.owner.from.{user|organization}.login}.</li>
     * </ul>
     *
     * @return the previous {@code owner/name}, or {@code null} when the payload carries no usable
     *         change record
     */
    public String getPreviousNameWithOwner() {
        String renamed = getOldFullName();
        if (renamed != null) {
            return renamed;
        }
        String previousOwner = getPreviousOwnerLogin();
        if (previousOwner == null || repository == null || repository.name() == null) {
            return null;
        }
        return previousOwner + "/" + repository.name();
    }

    /**
     * Extracts {@code changes.owner.from.{user|organization}.login} from a transfer payload. GitHub
     * nests the previous owner under whichever account type it was, so both keys are probed.
     */
    private String getPreviousOwnerLogin() {
        if (changes == null || changes.owner() == null || changes.owner().from() == null) {
            return null;
        }
        Map<String, Object> from = changes.owner().from();
        for (String accountKey : new String[] { "organization", "user" }) {
            if (from.get(accountKey) instanceof Map<?, ?> account && account.get("login") instanceof String login) {
                return login;
            }
        }
        return null;
    }
}
