package de.tum.in.www1.hephaestus.gitprovider.project.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2Field;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2FieldConfiguration;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2IterationField;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2SingleSelectField;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectField;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

/**
 * Domain DTO for GitHub Projects V2 field configuration.
 * <p>
 * Fields define the columns/properties that can be set on project items.
 * GitHub provides various field types: text, number, date, single-select, iteration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubProjectFieldDTO(
    @JsonProperty("id") String id,
    @JsonProperty("name") String name,
    @JsonProperty("data_type") String dataType,
    @JsonProperty("options") List<Option> options
) {
    private static final Logger log = LoggerFactory.getLogger(GitHubProjectFieldDTO.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Option for single-select fields.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Option(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("color") String color,
        @JsonProperty("description") String description
    ) {}

    /**
     * Get the data type as an enum.
     */
    @Nullable
    public ProjectField.DataType getDataTypeEnum() {
        if (dataType == null) {
            return null;
        }
        return switch (dataType.toUpperCase()) {
            case "TEXT" -> ProjectField.DataType.TEXT;
            case "NUMBER" -> ProjectField.DataType.NUMBER;
            case "DATE" -> ProjectField.DataType.DATE;
            case "SINGLE_SELECT", "SINGLESELECT" -> ProjectField.DataType.SINGLE_SELECT;
            case "ITERATION" -> ProjectField.DataType.ITERATION;
            default -> {
                log.warn("Unknown field data type: {}, treating as TEXT", dataType);
                yield ProjectField.DataType.TEXT;
            }
        };
    }

    /**
     * Convert options to JSON string for storage.
     */
    @Nullable
    public String getOptionsJson() {
        if (options == null || options.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(options);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize options to JSON", e);
            return null;
        }
    }

    // ========== STATIC FACTORY METHODS FOR GRAPHQL RESPONSES ==========

    /**
     * Creates a GitHubProjectFieldDTO from a GraphQL field configuration.
     * <p>
     * Handles different field types: ProjectV2Field, ProjectV2SingleSelectField, ProjectV2IterationField.
     *
     * @param field the GraphQL field configuration (may be null)
     * @return GitHubProjectFieldDTO or null if field is null
     */
    @Nullable
    public static GitHubProjectFieldDTO fromFieldConfiguration(@Nullable GHProjectV2FieldConfiguration field) {
        if (field == null) {
            return null;
        }

        // Handle different field types through instanceof checks
        if (field instanceof GHProjectV2SingleSelectField ssField) {
            return fromSingleSelectField(ssField);
        } else if (field instanceof GHProjectV2IterationField iterField) {
            return fromIterationField(iterField);
        } else if (field instanceof GHProjectV2Field basicField) {
            return fromBasicField(basicField);
        }

        log.warn("Unknown field configuration type: {}", field.getClass().getSimpleName());
        return null;
    }

    private static GitHubProjectFieldDTO fromBasicField(GHProjectV2Field field) {
        String dataType = field.getDataType() != null ? field.getDataType().name() : "TEXT";
        return new GitHubProjectFieldDTO(field.getId(), field.getName(), dataType, Collections.emptyList());
    }

    private static GitHubProjectFieldDTO fromSingleSelectField(GHProjectV2SingleSelectField field) {
        List<Option> options = Collections.emptyList();
        if (field.getOptions() != null) {
            options = field
                .getOptions()
                .stream()
                .map(opt ->
                    new Option(
                        opt.getId(),
                        opt.getName(),
                        opt.getColor() != null ? opt.getColor().name() : null,
                        opt.getDescription()
                    )
                )
                .toList();
        }
        return new GitHubProjectFieldDTO(field.getId(), field.getName(), "SINGLE_SELECT", options);
    }

    private static GitHubProjectFieldDTO fromIterationField(GHProjectV2IterationField field) {
        List<Option> options = Collections.emptyList();
        if (field.getConfiguration() != null && field.getConfiguration().getIterations() != null) {
            options = field
                .getConfiguration()
                .getIterations()
                .stream()
                .map(iter -> new Option(iter.getId(), iter.getTitle(), null, null))
                .toList();
        }
        return new GitHubProjectFieldDTO(field.getId(), field.getName(), "ITERATION", options);
    }
}
