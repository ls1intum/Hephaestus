package org.kohsuke.github;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a GitHub Issue Type from webhook payloads.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GHIssueType {

    private final long id;
    private final String nodeId;
    private final String name;
    private final String description;
    private final String color;
    private final boolean isEnabled;

    @JsonCreator
    public GHIssueType(
        @JsonProperty("id") long id,
        @JsonProperty("node_id") String nodeId,
        @JsonProperty("name") String name,
        @JsonProperty("description") String description,
        @JsonProperty("color") String color,
        @JsonProperty("is_enabled") boolean isEnabled
    ) {
        this.id = id;
        this.nodeId = nodeId;
        this.name = name;
        this.description = description;
        this.color = color;
        this.isEnabled = isEnabled;
    }

    public long getId() {
        return id;
    }

    public String getNodeId() {
        return nodeId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getColor() {
        return color;
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    @Override
    public String toString() {
        return "GHIssueType{name='" + name + "', nodeId='" + nodeId + "'}";
    }
}
