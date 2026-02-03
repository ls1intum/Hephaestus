package de.tum.in.www1.hephaestus.config.jackson;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
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

/**
 * Jackson mixin for GitHub GraphQL ProjectV2ItemFieldValue union type.
 * <p>
 * Configures polymorphic deserialization for project item field value types
 * using the __typename field. This union represents the different value types
 * that can be stored in project fields:
 * <ul>
 *   <li>{@code ProjectV2ItemFieldTextValue}: Text field values</li>
 *   <li>{@code ProjectV2ItemFieldNumberValue}: Number field values</li>
 *   <li>{@code ProjectV2ItemFieldDateValue}: Date field values</li>
 *   <li>{@code ProjectV2ItemFieldSingleSelectValue}: Single-select option values</li>
 *   <li>{@code ProjectV2ItemFieldIterationValue}: Iteration/sprint values</li>
 *   <li>{@code ProjectV2ItemFieldLabelValue}: Label reference values</li>
 *   <li>{@code ProjectV2ItemFieldMilestoneValue}: Milestone reference values</li>
 *   <li>{@code ProjectV2ItemFieldPullRequestValue}: Pull request reference values</li>
 *   <li>{@code ProjectV2ItemFieldRepositoryValue}: Repository reference values</li>
 *   <li>{@code ProjectV2ItemFieldReviewerValue}: Reviewer reference values</li>
 *   <li>{@code ProjectV2ItemFieldUserValue}: User reference values</li>
 * </ul>
 * <p>
 * Note: Using defaultImpl=GHProjectV2ItemFieldTextValue.class as fallback since
 * text fields are the most common field type.
 *
 * @see <a href="https://docs.github.com/en/graphql/reference/unions#projectv2itemfieldvalue">GitHub GraphQL API - ProjectV2ItemFieldValue</a>
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "__typename",
    visible = true,
    defaultImpl = GHProjectV2ItemFieldTextValue.class
)
@JsonSubTypes(
    {
        @JsonSubTypes.Type(value = GHProjectV2ItemFieldTextValue.class, name = "ProjectV2ItemFieldTextValue"),
        @JsonSubTypes.Type(value = GHProjectV2ItemFieldNumberValue.class, name = "ProjectV2ItemFieldNumberValue"),
        @JsonSubTypes.Type(value = GHProjectV2ItemFieldDateValue.class, name = "ProjectV2ItemFieldDateValue"),
        @JsonSubTypes.Type(
            value = GHProjectV2ItemFieldSingleSelectValue.class,
            name = "ProjectV2ItemFieldSingleSelectValue"
        ),
        @JsonSubTypes.Type(value = GHProjectV2ItemFieldIterationValue.class, name = "ProjectV2ItemFieldIterationValue"),
        @JsonSubTypes.Type(value = GHProjectV2ItemFieldLabelValue.class, name = "ProjectV2ItemFieldLabelValue"),
        @JsonSubTypes.Type(value = GHProjectV2ItemFieldMilestoneValue.class, name = "ProjectV2ItemFieldMilestoneValue"),
        @JsonSubTypes.Type(
            value = GHProjectV2ItemFieldPullRequestValue.class,
            name = "ProjectV2ItemFieldPullRequestValue"
        ),
        @JsonSubTypes.Type(
            value = GHProjectV2ItemFieldRepositoryValue.class,
            name = "ProjectV2ItemFieldRepositoryValue"
        ),
        @JsonSubTypes.Type(value = GHProjectV2ItemFieldReviewerValue.class, name = "ProjectV2ItemFieldReviewerValue"),
        @JsonSubTypes.Type(value = GHProjectV2ItemFieldUserValue.class, name = "ProjectV2ItemFieldUserValue"),
    }
)
public abstract class GitHubProjectV2ItemFieldValueMixin {}
