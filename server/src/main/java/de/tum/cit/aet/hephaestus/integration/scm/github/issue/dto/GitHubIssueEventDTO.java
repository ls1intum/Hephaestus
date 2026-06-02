package de.tum.cit.aet.hephaestus.integration.scm.github.issue.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.cit.aet.hephaestus.integration.scm.github.common.GitHubEventAction;
import de.tum.cit.aet.hephaestus.integration.scm.github.common.GitHubWebhookEvent;
import de.tum.cit.aet.hephaestus.integration.scm.github.label.dto.GitHubLabelDTO;
import de.tum.cit.aet.hephaestus.integration.scm.github.repository.dto.GitHubRepositoryRefDTO;
import de.tum.cit.aet.hephaestus.integration.scm.github.user.dto.GitHubUserDTO;
import java.util.Map;

/**
 * DTO for GitHub issue webhook event payloads.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubIssueEventDTO(
    @JsonProperty("action") String action,
    @JsonProperty("issue") GitHubIssueDTO issue,
    @JsonProperty("repository") GitHubRepositoryRefDTO repository,
    @JsonProperty("organization") GitHubOrgRefDTO organization,
    @JsonProperty("sender") GitHubUserDTO sender,
    @JsonProperty("label") GitHubLabelDTO label,
    @JsonProperty("type") GitHubIssueTypeDTO issueType,
    @JsonProperty("changes") Map<String, Object> changes
) implements GitHubWebhookEvent {
    @Override
    public GitHubEventAction.Issue actionType() {
        return GitHubEventAction.Issue.fromString(action);
    }

    /**
     * DTO for organization reference in issue events.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitHubOrgRefDTO(@JsonProperty("id") Long id, @JsonProperty("login") String login) {}
}
