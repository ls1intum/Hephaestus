package org.kohsuke.github;

import com.fasterxml.jackson.annotation.JsonProperty;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Local extensions around repository discussions that are missing from github-api 2.0-rc.5.
 */
public final class GHRepositoryDiscussionsSupport {

    private static final String DISCUSSIONS_PREVIEW = "application/vnd.github.echo-preview+json";
    private static final Method ROOT_METHOD = locateRootMethod();
    private static final Method CREATE_REQUEST_METHOD = locateCreateRequestMethod();
    private static final Class<?> REQUESTER_CLASS = locateRequesterClass();
    private static final Method WITH_ACCEPT_METHOD = locateRequesterMethod("withAccept", String.class);
    private static final Method WITH_URL_PATH_METHOD = locateRequesterMethod(String.class, String[].class);
    private static final Method WITH_FIELD_METHOD = locateRequesterMethod("with", String.class, Object.class);
    private static final Method METHOD_METHOD = locateRequesterMethod("method", String.class);
    private static final Method FETCH_METHOD = locateRequesterMethod("fetch", Class.class);
    private static final Method SEND_METHOD = locateRequesterMethod("send");
    private static final Method TO_ITERABLE_METHOD = locateRequesterMethod("toIterable", Class.class, Consumer.class);
    private static final Method GET_API_TAIL_URL_METHOD = locateApiTailUrlMethod();

    private GHRepositoryDiscussionsSupport() {}

    private static Method locateRootMethod() {
        try {
            Class<?> interactiveClass = Class.forName(
                "org.kohsuke.github.GitHubInteractiveObject",
                false,
                GHRepository.class.getClassLoader()
            );
            Method method = interactiveClass.getDeclaredMethod("root");
            method.setAccessible(true);
            return method;
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            throw new IllegalStateException("github-api no longer exposes GitHubInteractiveObject#root", e);
        }
    }

    private static GitHub resolveRoot(GHRepository repository) {
        try {
            return (GitHub) ROOT_METHOD.invoke(repository);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Unable to access GitHub client from repository", e);
        }
    }

    private static RequesterAdapter createRequester(GitHub gitHub) {
        try {
            Object requester = CREATE_REQUEST_METHOD.invoke(gitHub);
            return new RequesterAdapter(requester);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Unable to create GitHub request", e);
        }
    }

    private static Method locateCreateRequestMethod() {
        try {
            Method method = GitHub.class.getDeclaredMethod("createRequest");
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("github-api no longer exposes GitHub#createRequest", e);
        }
    }

    private static Method locateApiTailUrlMethod() {
        try {
            Method method = GHRepository.class.getDeclaredMethod("getApiTailUrl", String.class);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("github-api no longer exposes GHRepository#getApiTailUrl", e);
        }
    }

    private static Class<?> locateRequesterClass() {
        try {
            return Class.forName("org.kohsuke.github.Requester", false, GHRepository.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("github-api no longer exposes Requester", e);
        }
    }

    private static Method locateRequesterMethod(String name, Class<?>... parameterTypes) {
        try {
            Method method = REQUESTER_CLASS.getMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("github-api no longer exposes Requester#" + name, e);
        }
    }

    private static Method locateRequesterMethod(Class<?>... parameterTypes) {
        return locateRequesterMethod("withUrlPath", parameterTypes);
    }

    public static PagedIterable<GHRepositoryDiscussion> listDiscussions(GHRepository repository, Instant since)
        throws IOException {
        RequesterAdapter requester = createRequester(resolveRoot(repository))
            .withAccept(DISCUSSIONS_PREVIEW)
            .withUrlPath(getApiTailUrl(repository, "discussions"));
        if (since != null) {
            requester = requester.with("since", DateTimeFormatter.ISO_INSTANT.format(since));
        }
        return requester.toIterable(GHRepositoryDiscussion[].class);
    }

    public static PagedIterable<GHRepositoryDiscussionComment> listDiscussionComments(
        GHRepository repository,
        long discussionNumber,
        Instant since
    ) throws IOException {
        RequesterAdapter requester = createRequester(resolveRoot(repository))
            .withAccept(DISCUSSIONS_PREVIEW)
            .withUrlPath(getApiTailUrl(repository, "discussions/" + discussionNumber + "/comments"));
        if (since != null) {
            requester = requester.with("since", DateTimeFormatter.ISO_INSTANT.format(since));
        }
        return requester.toIterable(GHRepositoryDiscussionComment[].class);
    }

    public static List<GHRepositoryDiscussion.Category> listDiscussionCategories(GHRepository repository)
        throws IOException {
        DiscussionCategoriesResponse response = createRequester(resolveRoot(repository))
            .withAccept(DISCUSSIONS_PREVIEW)
            .withUrlPath(getApiTailUrl(repository, "discussions/categories"))
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
        return createRequester(resolveRoot(repository))
            .withAccept(DISCUSSIONS_PREVIEW)
            .method("POST")
            .with("title", title)
            .with("body", body)
            .with("category_id", categoryId)
            .withUrlPath(getApiTailUrl(repository, "discussions"))
            .fetch(GHRepositoryDiscussion.class);
    }

    public static GHRepositoryDiscussionComment createDiscussionComment(
        GHRepository repository,
        long discussionNumber,
        String body
    ) throws IOException {
        return createRequester(resolveRoot(repository))
            .withAccept(DISCUSSIONS_PREVIEW)
            .method("POST")
            .with("body", body)
            .withUrlPath(getApiTailUrl(repository, "discussions/" + discussionNumber + "/comments"))
            .fetch(GHRepositoryDiscussionComment.class);
    }

    public static void enableDiscussions(GHRepository repository) throws IOException {
        createRequester(resolveRoot(repository))
            .withAccept(DISCUSSIONS_PREVIEW)
            .method("PUT")
            .withUrlPath(getApiTailUrl(repository, "discussions"))
            .send();
    }

    private static String getApiTailUrl(GHRepository repository, String path) {
        try {
            return (String) GET_API_TAIL_URL_METHOD.invoke(repository, path);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Unable to resolve repository API tail", e);
        }
    }

    private static final class RequesterAdapter {

        private final Object delegate;

        private RequesterAdapter(Object delegate) {
            this.delegate = delegate;
        }

        private RequesterAdapter withAccept(String accept) {
            invoke(WITH_ACCEPT_METHOD, delegate, accept);
            return this;
        }

        private RequesterAdapter withUrlPath(String path) {
            invoke(WITH_URL_PATH_METHOD, delegate, path, new String[0]);
            return this;
        }

        private RequesterAdapter with(String key, Object value) {
            invoke(WITH_FIELD_METHOD, delegate, key, value);
            return this;
        }

        private RequesterAdapter method(String method) {
            invoke(METHOD_METHOD, delegate, method);
            return this;
        }

        @SuppressWarnings("unchecked")
        private <T> T fetch(Class<T> type) {
            return (T) invoke(FETCH_METHOD, delegate, type);
        }

        private void send() {
            invoke(SEND_METHOD, delegate);
        }

        @SuppressWarnings("unchecked")
        private <T> PagedIterable<T> toIterable(Class<T[]> type) {
            return (PagedIterable<T>) invoke(TO_ITERABLE_METHOD, delegate, type, null);
        }

        private static Object invoke(Method method, Object target, Object... args) {
            try {
                return method.invoke(target, args);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException("Failed to invoke Requester method " + method.getName(), e);
            }
        }
    }

    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification = "JSON binding")
    private static class DiscussionCategoriesResponse {

        @JsonProperty("repository_discussion_categories")
        private GHRepositoryDiscussion.Category[] categories;
    }
}
