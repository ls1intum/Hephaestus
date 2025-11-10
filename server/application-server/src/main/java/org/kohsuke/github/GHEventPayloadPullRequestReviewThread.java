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

        private Long id;

        @JsonProperty("node_id")
        private String nodeId;

        private String path;

        private Integer line;

        @JsonProperty("start_line")
        private Integer startLine;

        private String side;

        @JsonProperty("start_side")
        private String startSide;

        @JsonProperty("is_outdated")
        private Boolean outdated;

        @JsonProperty("is_collapsed")
        private Boolean collapsed;

        private Boolean resolved;

        @JsonProperty("resolved_by")
        private GHUser resolvedBy;

        private List<GHPullRequestReviewComment> comments;

        public Long getId() {
            return id;
        }

        public String getNodeId() {
            return nodeId;
        }

        public String getPath() {
            return path;
        }

        public Integer getLine() {
            return line;
        }

        public Integer getStartLine() {
            return startLine;
        }

        public String getSide() {
            return side;
        }

        public String getStartSide() {
            return startSide;
        }

        public Boolean getOutdated() {
            return outdated;
        }

        public Boolean getCollapsed() {
            return collapsed;
        }

        public Boolean getResolved() {
            return resolved;
        }

        public GHUser getResolvedBy() {
            return resolvedBy;
        }

        public List<GHPullRequestReviewComment> getComments() {
            return comments;
        }
    }
}
