package org.kohsuke.github;

import com.fasterxml.jackson.annotation.JsonProperty;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

/**
 * Local extensions around repository discussions that are missing from github-api 2.0-rc.5.
 */
public final class GHRepositoryDiscussionsSupport {

    private static final String DISCUSSIONS_PREVIEW = "application/vnd.github.echo-preview+json";

    private GHRepositoryDiscussionsSupport() {}

    public static PagedIterable<GHRepositoryDiscussion> listDiscussions(GHRepository repository, Instant since)
        throws IOException {
        Requester requester = repository.root().createRequest()
            .withAccept(DISCUSSIONS_PREVIEW)
            .withUrlPath(repository.getApiTailUrl("discussions"));
        if (since != null) {
            requester.with("since", DateTimeFormatter.ISO_INSTANT.format(since));
        }
        return requester.toIterable(GHRepositoryDiscussion[].class, null);
    }

    public static PagedIterable<GHRepositoryDiscussionComment> listDiscussionComments(
        GHRepository repository,
        long discussionNumber,
        Instant since
    ) throws IOException {
        Requester requester = repository.root().createRequest()
            .withAccept(DISCUSSIONS_PREVIEW)
            .withUrlPath(repository.getApiTailUrl("discussions/" + discussionNumber + "/comments"));
        if (since != null) {
            requester.with("since", DateTimeFormatter.ISO_INSTANT.format(since));
        }
        return requester.toIterable(GHRepositoryDiscussionComment[].class, null);
    }

    public static List<GHRepositoryDiscussion.Category> listDiscussionCategories(GHRepository repository)
        throws IOException {
        DiscussionCategoriesResponse response = repository.root().createRequest()
            .withAccept(DISCUSSIONS_PREVIEW)
            .withUrlPath(repository.getApiTailUrl("discussions/categories"))
            .fetch(DiscussionCategoriesResponse.class);
        if (response == null || response.categories == null || response.categories.length == 0) {
            return List.of();
        }
        return Arrays.asList(response.categories);
    }

    public static GHRepositoryDiscussion createDiscussion(
        GHRepository repository,
        String title,
        String body,
        long categoryId
    ) throws IOException {
        return repository.root().createRequest()
            .withAccept(DISCUSSIONS_PREVIEW)
            .method("POST")
            .with("title", title)
            .with("body", body)
            .with("category_id", categoryId)
            .withUrlPath(repository.getApiTailUrl("discussions"))
            .fetch(GHRepositoryDiscussion.class);
    }

    public static GHRepositoryDiscussionComment createDiscussionComment(
        GHRepository repository,
        long discussionNumber,
        String body
    ) throws IOException {
        return repository.root().createRequest()
            .withAccept(DISCUSSIONS_PREVIEW)
            .method("POST")
            .with("body", body)
            .withUrlPath(repository.getApiTailUrl("discussions/" + discussionNumber + "/comments"))
            .fetch(GHRepositoryDiscussionComment.class);
    }

    public static void enableDiscussions(GHRepository repository) throws IOException {
        repository.root().createRequest()
            .withAccept(DISCUSSIONS_PREVIEW)
            .method("PUT")
            .withUrlPath(repository.getApiTailUrl("discussions"))
            .send();
    }

    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification = "JSON binding")
    private static class DiscussionCategoriesResponse {

        @JsonProperty("repository_discussion_categories")
        private GHRepositoryDiscussion.Category[] categories;
    }
}
