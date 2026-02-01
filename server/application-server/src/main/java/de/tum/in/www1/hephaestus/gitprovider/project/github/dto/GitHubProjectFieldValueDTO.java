package de.tum.in.www1.hephaestus.gitprovider.project.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2Field;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2FieldConfiguration;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2ItemFieldDateValue;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2ItemFieldIterationValue;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2ItemFieldNumberValue;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2ItemFieldSingleSelectValue;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2ItemFieldTextValue;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2ItemFieldValue;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2IterationField;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2SingleSelectField;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

/**
 * Domain DTO for GitHub Projects V2 field values.
 * <p>
 * Represents a polymorphic field value that can be one of:
 * - TEXT: textValue
 * - NUMBER: numberValue
 * - DATE: dateValue
 * - SINGLE_SELECT: singleSelectOptionId
 * - ITERATION: iterationId
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
        }

        // Unknown or unsupported field value type (e.g., Labels, Assignees, Reviewers)
        // These are read-only fields managed by GitHub, not custom project fields
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
