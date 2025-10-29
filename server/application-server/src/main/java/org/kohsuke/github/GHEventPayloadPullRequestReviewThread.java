package org.kohsuke.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GHEventPayloadPullRequestReviewThread extends GHEventPayload {

    @JsonProperty("thread")
    private ThreadPayload thread;

    @JsonProperty("pull_request")
    private GHPullRequest pullRequest;

    @JsonProperty("updated_at")
    private String updatedAt;

    public ThreadPayload getThread() {
        return thread != null ? thread : new ThreadPayload();
    }

    public GHPullRequest getPullRequest() {
        return pullRequest;
    }

    public Instant getUpdatedAt() {
        if (updatedAt == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(updatedAt).toInstant();
        } catch (Exception ex) {
            return null;
        }
    }

    @Override
    void lateBind() {
        if (thread == null) {
            throw new IllegalStateException(
                "Expected pull_request_review_thread payload, but got something else. Maybe we've got another type of event?"
            );
        }
        super.lateBind();
        if (pullRequest != null && getRepository() != null) {
            pullRequest.wrapUp(getRepository());
        }
        getThread()
            .getComments()
            .forEach(comment -> {
                if (pullRequest != null) {
                    comment.wrapUp(pullRequest);
                }
            });
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ThreadPayload {

        @JsonProperty("node_id")
        private String nodeId;

        @JsonProperty("comments")
        private List<GHPullRequestReviewComment> comments;

        public String getNodeId() {
            return nodeId;
        }

        public List<GHPullRequestReviewComment> getComments() {
            return comments != null ? comments : Collections.emptyList();
        }
    }
}
