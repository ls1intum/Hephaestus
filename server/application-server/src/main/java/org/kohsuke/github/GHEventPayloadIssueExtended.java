package org.kohsuke.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Replacement for {@link GHEventPayload.Issue} that wires in {@link GHIssueExtended}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GHEventPayloadIssueExtended extends GHEventPayload {

    private GHIssueChanges changes;
    private GHIssueExtended issue;
    private GHLabel label;

    public GHIssueChanges getChanges() {
        return changes;
    }

    public GHIssueExtended getIssue() {
        return issue;
    }

    public GHLabel getLabel() {
        return label;
    }

    @Override
    void lateBind() {
        super.lateBind();
        GHRepository repository = getRepository();
        if (repository != null && issue != null) {
            issue.wrap(repository);
        }
    }
}
