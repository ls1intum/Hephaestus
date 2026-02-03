package de.tum.in.www1.hephaestus.config.jackson;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2Field;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2IterationField;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2SingleSelectField;

/**
 * Jackson mixin for GitHub GraphQL ProjectV2FieldConfiguration union type.
 * <p>
 * Configures polymorphic deserialization for project field configuration types
 * using the __typename field. This union represents the different field types
 * available in GitHub Projects V2:
 * <ul>
 *   <li>{@code ProjectV2Field}: Basic fields (text, number, date)</li>
 *   <li>{@code ProjectV2SingleSelectField}: Single-select dropdown fields</li>
 *   <li>{@code ProjectV2IterationField}: Iteration/sprint fields</li>
 * </ul>
 * <p>
 * Note: Using defaultImpl=GHProjectV2Field.class as fallback for basic field types.
 *
 * @see <a href="https://docs.github.com/en/graphql/reference/unions#projectv2fieldconfiguration">GitHub GraphQL API - ProjectV2FieldConfiguration</a>
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "__typename",
    visible = true,
    defaultImpl = GHProjectV2Field.class
)
@JsonSubTypes(
    {
        @JsonSubTypes.Type(value = GHProjectV2Field.class, name = "ProjectV2Field"),
        @JsonSubTypes.Type(value = GHProjectV2SingleSelectField.class, name = "ProjectV2SingleSelectField"),
        @JsonSubTypes.Type(value = GHProjectV2IterationField.class, name = "ProjectV2IterationField"),
    }
)
public abstract class GitHubProjectV2FieldConfigurationMixin {}
