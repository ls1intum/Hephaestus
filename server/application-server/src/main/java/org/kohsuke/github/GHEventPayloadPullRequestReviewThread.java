package org.kohsuke.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Lightweight representation of the {@code pull_request_review_thread} webhook payload.
 *
 * <p>
 * GitHub ships the payload with an array of comments that belong to the thread. We reuse
 * {@link GHPullRequestReviewComment} here so the rest of the application can keep relying on the
 * official API model when converting the payload.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GHEventPayloadPullRequestReviewThread extends GHEventPayload {

    private GHPullRequest pullRequest;

    private GHRepository repository;

    private Thread thread;

    public GHPullRequest getPullRequest() {
        return pullRequest;
    }

    public GHRepository getRepository() {
        return repository;
    }

    public Thread getThread() {
        return thread;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Thread {

        @JsonProperty("node_id")
        private String nodeId;

        private List<GHPullRequestReviewComment> comments;

        public String getNodeId() {
            return nodeId;
        }

        public List<GHPullRequestReviewComment> getComments() {
            return comments;
        }
    }
}
