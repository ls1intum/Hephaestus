package de.tum.in.www1.hephaestus.gitprovider.project.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHLabel;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHMilestone;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2Field;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2FieldConfiguration;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2ItemFieldDateValue;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2ItemFieldIterationValue;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2ItemFieldLabelValue;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2ItemFieldMilestoneValue;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2ItemFieldNumberValue;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2ItemFieldPullRequestValue;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2ItemFieldRepositoryValue;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2ItemFieldReviewerValue;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2ItemFieldSingleSelectValue;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2ItemFieldTextValue;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2ItemFieldUserValue;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2ItemFieldValue;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2IterationField;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2SingleSelectField;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHPullRequest;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHRepository;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHRequestedReviewer;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHTeam;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHUser;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

/**
 * Domain DTO for GitHub Projects V2 field values.
 * <p>
 * Represents a polymorphic field value that can be one of:
 * - TEXT: textValue (plain text)
 * - NUMBER: numberValue
 * - DATE: dateValue
 * - SINGLE_SELECT: singleSelectOptionId
 * - ITERATION: iterationId
 * - LABELS: textValue (JSON array of label names)
 * - ASSIGNEES: textValue (JSON array of user logins)
 * - REVIEWERS: textValue (JSON array of reviewer logins/team names)
 * - MILESTONE: textValue (milestone title)
 * - REPOSITORY: textValue (repository nameWithOwner)
 * - PULL_REQUESTS: textValue (JSON array of PR numbers)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubProjectFieldValueDTO(
    @JsonProperty("field_id") String fieldId,
    @JsonProperty("field_type") String fieldType,
    @JsonProperty("text_value") String textValue,
    @JsonProperty("number_value") Double numberValue,
    @JsonProperty("date_value") LocalDate dateValue,
    @JsonProperty("single_select_option_id") String singleSelectOptionId,
    @JsonProperty("iteration_id") String iterationId
) {
    private static final Logger log = LoggerFactory.getLogger(GitHubProjectFieldValueDTO.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // ========== STATIC FACTORY METHODS FOR GRAPHQL RESPONSES ==========

    /**
     * Creates a GitHubProjectFieldValueDTO from a GraphQL field value.
     * <p>
     * Handles different field value types by extracting the appropriate value.
     *
     * @param fieldValue the GraphQL field value (may be null)
     * @return GitHubProjectFieldValueDTO or null if fieldValue is null or cannot be extracted
     */
    @Nullable
    public static GitHubProjectFieldValueDTO fromFieldValue(@Nullable GHProjectV2ItemFieldValue fieldValue) {
        if (fieldValue == null) {
            return null;
        }

        if (fieldValue instanceof GHProjectV2ItemFieldTextValue textValue) {
            return fromTextValue(textValue);
        } else if (fieldValue instanceof GHProjectV2ItemFieldNumberValue numberValue) {
            return fromNumberValue(numberValue);
        } else if (fieldValue instanceof GHProjectV2ItemFieldDateValue dateValue) {
            return fromDateValue(dateValue);
        } else if (fieldValue instanceof GHProjectV2ItemFieldSingleSelectValue ssValue) {
            return fromSingleSelectValue(ssValue);
        } else if (fieldValue instanceof GHProjectV2ItemFieldIterationValue iterValue) {
            return fromIterationValue(iterValue);
        } else if (fieldValue instanceof GHProjectV2ItemFieldLabelValue labelValue) {
            return fromLabelValue(labelValue);
        } else if (fieldValue instanceof GHProjectV2ItemFieldUserValue userValue) {
            return fromUserValue(userValue);
        } else if (fieldValue instanceof GHProjectV2ItemFieldReviewerValue reviewerValue) {
            return fromReviewerValue(reviewerValue);
        } else if (fieldValue instanceof GHProjectV2ItemFieldMilestoneValue milestoneValue) {
            return fromMilestoneValue(milestoneValue);
        } else if (fieldValue instanceof GHProjectV2ItemFieldRepositoryValue repositoryValue) {
            return fromRepositoryValue(repositoryValue);
        } else if (fieldValue instanceof GHProjectV2ItemFieldPullRequestValue pullRequestValue) {
            return fromPullRequestValue(pullRequestValue);
        }

        // Unknown or unsupported field value type
        log.debug("Unsupported field value type: {}", fieldValue.getClass().getSimpleName());
        return null;
    }

    private static GitHubProjectFieldValueDTO fromTextValue(GHProjectV2ItemFieldTextValue value) {
        String fieldId = extractFieldId(value.getField());
        if (fieldId == null) {
            return null;
        }
        return new GitHubProjectFieldValueDTO(fieldId, "TEXT", value.getText(), null, null, null, null);
    }

    private static GitHubProjectFieldValueDTO fromNumberValue(GHProjectV2ItemFieldNumberValue value) {
        String fieldId = extractFieldId(value.getField());
        if (fieldId == null) {
            return null;
        }
        return new GitHubProjectFieldValueDTO(fieldId, "NUMBER", null, value.getNumber(), null, null, null);
    }

    private static GitHubProjectFieldValueDTO fromDateValue(GHProjectV2ItemFieldDateValue value) {
        String fieldId = extractFieldId(value.getField());
        if (fieldId == null) {
            return null;
        }
        LocalDate date = null;
        if (value.getDate() != null) {
            try {
                date = LocalDate.parse(value.getDate().toString());
            } catch (Exception e) {
                log.warn("Failed to parse date value: {}", value.getDate(), e);
            }
        }
        return new GitHubProjectFieldValueDTO(fieldId, "DATE", null, null, date, null, null);
    }

    private static GitHubProjectFieldValueDTO fromSingleSelectValue(GHProjectV2ItemFieldSingleSelectValue value) {
        String fieldId = extractFieldId(value.getField());
        if (fieldId == null) {
            return null;
        }
        return new GitHubProjectFieldValueDTO(fieldId, "SINGLE_SELECT", null, null, null, value.getOptionId(), null);
    }

    private static GitHubProjectFieldValueDTO fromIterationValue(GHProjectV2ItemFieldIterationValue value) {
        String fieldId = extractFieldId(value.getField());
        if (fieldId == null) {
            return null;
        }
        return new GitHubProjectFieldValueDTO(fieldId, "ITERATION", null, null, null, null, value.getIterationId());
    }

    @Nullable
    private static GitHubProjectFieldValueDTO fromLabelValue(GHProjectV2ItemFieldLabelValue value) {
        String fieldId = extractFieldId(value.getField());
        if (fieldId == null) {
            return null;
        }
        List<String> labelNames = Collections.emptyList();
        if (value.getLabels() != null && value.getLabels().getNodes() != null) {
            labelNames = value.getLabels().getNodes().stream()
                .filter(Objects::nonNull)
                .map(GHLabel::getName)
                .filter(Objects::nonNull)
                .toList();
        }
        String jsonValue = serializeToJson(labelNames);
        return new GitHubProjectFieldValueDTO(fieldId, "LABELS", jsonValue, null, null, null, null);
    }

    @Nullable
    private static GitHubProjectFieldValueDTO fromUserValue(GHProjectV2ItemFieldUserValue value) {
        String fieldId = extractFieldId(value.getField());
        if (fieldId == null) {
            return null;
        }
        List<String> userLogins = Collections.emptyList();
        if (value.getUsers() != null && value.getUsers().getNodes() != null) {
            userLogins = value.getUsers().getNodes().stream()
                .filter(Objects::nonNull)
                .map(GHUser::getLogin)
                .filter(Objects::nonNull)
                .toList();
        }
        String jsonValue = serializeToJson(userLogins);
        return new GitHubProjectFieldValueDTO(fieldId, "ASSIGNEES", jsonValue, null, null, null, null);
    }

    @Nullable
    private static GitHubProjectFieldValueDTO fromReviewerValue(GHProjectV2ItemFieldReviewerValue value) {
        String fieldId = extractFieldId(value.getField());
        if (fieldId == null) {
            return null;
        }
        List<String> reviewerNames = Collections.emptyList();
        if (value.getReviewers() != null && value.getReviewers().getNodes() != null) {
            reviewerNames = value.getReviewers().getNodes().stream()
                .filter(Objects::nonNull)
                .map(GitHubProjectFieldValueDTO::extractReviewerName)
                .filter(Objects::nonNull)
                .toList();
        }
        String jsonValue = serializeToJson(reviewerNames);
        return new GitHubProjectFieldValueDTO(fieldId, "REVIEWERS", jsonValue, null, null, null, null);
    }

    @Nullable
    private static String extractReviewerName(GHRequestedReviewer reviewer) {
        if (reviewer instanceof GHUser user) {
            return user.getLogin();
        } else if (reviewer instanceof GHTeam team) {
            return team.getName();
        }
        return null;
    }

    @Nullable
    private static GitHubProjectFieldValueDTO fromMilestoneValue(GHProjectV2ItemFieldMilestoneValue value) {
        String fieldId = extractFieldId(value.getField());
        if (fieldId == null) {
            return null;
        }
        String milestoneTitle = null;
        GHMilestone milestone = value.getMilestone();
        if (milestone != null) {
            milestoneTitle = milestone.getTitle();
        }
        return new GitHubProjectFieldValueDTO(fieldId, "MILESTONE", milestoneTitle, null, null, null, null);
    }

    @Nullable
    private static GitHubProjectFieldValueDTO fromRepositoryValue(GHProjectV2ItemFieldRepositoryValue value) {
        String fieldId = extractFieldId(value.getField());
        if (fieldId == null) {
            return null;
        }
        String repoNameWithOwner = null;
        GHRepository repository = value.getRepository();
        if (repository != null) {
            repoNameWithOwner = repository.getNameWithOwner();
        }
        return new GitHubProjectFieldValueDTO(fieldId, "REPOSITORY", repoNameWithOwner, null, null, null, null);
    }

    @Nullable
    private static GitHubProjectFieldValueDTO fromPullRequestValue(GHProjectV2ItemFieldPullRequestValue value) {
        String fieldId = extractFieldId(value.getField());
        if (fieldId == null) {
            return null;
        }
        List<Integer> prNumbers = Collections.emptyList();
        if (value.getPullRequests() != null && value.getPullRequests().getNodes() != null) {
            prNumbers = value.getPullRequests().getNodes().stream()
                .filter(Objects::nonNull)
                .map(GHPullRequest::getNumber)
                .toList();
        }
        String jsonValue = serializeToJson(prNumbers);
        return new GitHubProjectFieldValueDTO(fieldId, "PULL_REQUESTS", jsonValue, null, null, null, null);
    }

    private static String serializeToJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize value to JSON: {}", value, e);
            return "[]";
        }
    }

    @Nullable
    private static String extractFieldId(@Nullable GHProjectV2FieldConfiguration field) {
        if (field == null) {
            return null;
        }
        // GHProjectV2FieldConfiguration is a union type with different implementations
        if (field instanceof GHProjectV2Field f) {
            return f.getId();
        } else if (field instanceof GHProjectV2SingleSelectField f) {
            return f.getId();
        } else if (field instanceof GHProjectV2IterationField f) {
            return f.getId();
        }
        return null;
    }
}
