package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.gitlab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.gitprovider.common.GitProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewComment;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewCommentRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThread;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.context.ApplicationEventPublisher;

@Tag("unit")
@DisplayName("GitLabPullRequestReviewCommentProcessor")
class GitLabPullRequestReviewCommentProcessorTest extends BaseUnitTest {

    private static final long PROVIDER_ID = 2L;
    private static final long PR_ID = 100L;
    private static final long THREAD_ID = 200L;
    private static final long SCOPE_ID = 1L;
    private static final long NOTE_NATIVE_ID = 555666L;
    private static final String NOTE_GLOBAL_ID = "gid://gitlab/DiffNote/" + NOTE_NATIVE_ID;

    @Mock
    private PullRequestReviewCommentRepository commentRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private GitLabPullRequestReviewCommentProcessor processor;
    private GitProvider provider;
    private PullRequest pr;
    private PullRequestReviewThread thread;

    @BeforeEach
    void setUp() {
        processor = new GitLabPullRequestReviewCommentProcessor(commentRepository, eventPublisher);

        provider = new GitProvider();
        provider.setId(PROVIDER_ID);
        provider.setType(GitProviderType.GITLAB);

        pr = new PullRequest();
        pr.setId(PR_ID);
        pr.setProvider(provider);

        thread = new PullRequestReviewThread();
        thread.setId(THREAD_ID);
        thread.setProvider(provider);
    }

    // ========================================================================
    // resolvePath (static helper)
    // ========================================================================

    @Nested
    @DisplayName("resolvePath")
    class ResolvePath {

        @Test
        @DisplayName("should prefer newPath when available")
        void shouldPreferNewPathWhenAvailable() {
            var data = buildDiffNoteData("new/path.ts", "old/path.ts", "ignored.ts", 10, null, null, null, null);
            assertThat(GitLabPullRequestReviewCommentProcessor.resolvePath(data)).isEqualTo("new/path.ts");
        }

        @Test
        @DisplayName("should fall back to oldPath when newPath is blank")
        void shouldFallBackToOldPathWhenNewPathIsBlank() {
            var data = buildDiffNoteData(null, "old/path.ts", "ignored.ts", null, 5, null, null, null);
            assertThat(GitLabPullRequestReviewCommentProcessor.resolvePath(data)).isEqualTo("old/path.ts");
        }

        @Test
        @DisplayName("should fall back to filePath when both newPath and oldPath are null")
        void shouldFallBackToFilePathWhenNewAndOldPathAreNull() {
            var data = buildDiffNoteData(null, null, "resolved/path.ts", 10, null, null, null, null);
            assertThat(GitLabPullRequestReviewCommentProcessor.resolvePath(data)).isEqualTo("resolved/path.ts");
        }

        @Test
        @DisplayName("should return empty string when no path is available")
        void shouldReturnEmptyStringWhenNoPathIsAvailable() {
            var data = buildDiffNoteData(null, null, null, 10, null, null, null, null);
            assertThat(GitLabPullRequestReviewCommentProcessor.resolvePath(data)).isEmpty();
        }
    }

    // ========================================================================
    // deriveSide (static helper)
    // ========================================================================

    @Nested
    @DisplayName("deriveSide")
    class DeriveSide {

        @Test
        @DisplayName("should return RIGHT when newLine is present")
        void shouldReturnRightWhenNewLineIsPresent() {
            assertThat(GitLabPullRequestReviewCommentProcessor.deriveSide(42, null)).isEqualTo(
                PullRequestReviewComment.Side.RIGHT
            );
        }

        @Test
        @DisplayName("should return LEFT when only oldLine is present")
        void shouldReturnLeftWhenOnlyOldLineIsPresent() {
            assertThat(GitLabPullRequestReviewCommentProcessor.deriveSide(null, 7)).isEqualTo(
                PullRequestReviewComment.Side.LEFT
            );
        }

        @Test
        @DisplayName("should default to RIGHT when neither line is present")
        void shouldDefaultToRightWhenNeitherLineIsPresent() {
            assertThat(GitLabPullRequestReviewCommentProcessor.deriveSide(null, null)).isEqualTo(
                PullRequestReviewComment.Side.RIGHT
            );
        }
    }

    // ========================================================================
    // buildDiffHunkStub (static helper)
    // ========================================================================

    @Nested
    @DisplayName("buildDiffHunkStub")
    class BuildDiffHunkStub {

        @Test
        @DisplayName("should build single-line stub when newLine is present")
        void shouldBuildSingleLineStubWhenNewLineIsPresent() {
            assertThat(GitLabPullRequestReviewCommentProcessor.buildDiffHunkStub(null, 42)).isEqualTo(
                "@@ -0,1 +42,1 @@"
            );
        }

        @Test
        @DisplayName("should build single-line stub when oldLine is present")
        void shouldBuildSingleLineStubWhenOldLineIsPresent() {
            assertThat(GitLabPullRequestReviewCommentProcessor.buildDiffHunkStub(7, null)).isEqualTo("@@ -7,1 +0,1 @@");
        }

        @Test
        @DisplayName("should return null when both lines are null")
        void shouldReturnNullWhenBothLinesAreNull() {
            assertThat(GitLabPullRequestReviewCommentProcessor.buildDiffHunkStub(null, null)).isNull();
        }

        @Test
        @DisplayName("should return null when both lines are zero")
        void shouldReturnNullWhenBothLinesAreZero() {
            assertThat(GitLabPullRequestReviewCommentProcessor.buildDiffHunkStub(0, 0)).isNull();
        }
    }

    // ========================================================================
    // findOrCreateComment — new comment creation
    // ========================================================================

    @Nested
    @DisplayName("findOrCreateComment — creation")
    class CreateComment {

        @Test
        @DisplayName("should populate path, side, commitId, originalCommitId, and diffHunk when creating a comment")
        void shouldPopulateAllPositionMetadataWhenCreatingComment() {
            when(commentRepository.findByNativeIdAndProviderId(NOTE_NATIVE_ID, PROVIDER_ID)).thenReturn(
                Optional.empty()
            );
            when(commentRepository.save(any(PullRequestReviewComment.class))).thenAnswer(inv ->
                inv.getArgument(0, PullRequestReviewComment.class)
            );

            var data = buildDiffNoteData(
                "src/Foo.ts",
                "src/Foo.ts",
                "src/Foo.ts",
                42,
                null,
                "head-sha",
                "base-sha",
                "start-sha"
            );
            var context = new GitLabPullRequestReviewCommentProcessor.CommentContext(
                thread,
                pr,
                null,
                provider,
                null,
                null,
                SCOPE_ID
            );

            PullRequestReviewComment saved = processor.findOrCreateComment(data, context);

            assertThat(saved).isNotNull();
            assertThat(saved.getPath()).isEqualTo("src/Foo.ts");
            assertThat(saved.getLine()).isEqualTo(42);
            assertThat(saved.getOriginalLine()).isZero();
            assertThat(saved.getSide()).isEqualTo(PullRequestReviewComment.Side.RIGHT);
            assertThat(saved.getStartSide()).isEqualTo(PullRequestReviewComment.Side.RIGHT);
            assertThat(saved.getCommitId()).isEqualTo("head-sha");
            assertThat(saved.getOriginalCommitId()).isEqualTo("base-sha");
            assertThat(saved.getDiffHunk()).isEqualTo("@@ -0,1 +42,1 @@");
        }

        @Test
        @DisplayName("should fall back to startSha for originalCommitId when baseSha is null")
        void shouldFallBackToStartShaWhenBaseShaIsNull() {
            when(commentRepository.findByNativeIdAndProviderId(NOTE_NATIVE_ID, PROVIDER_ID)).thenReturn(
                Optional.empty()
            );
            when(commentRepository.save(any(PullRequestReviewComment.class))).thenAnswer(inv ->
                inv.getArgument(0, PullRequestReviewComment.class)
            );

            var data = buildDiffNoteData("src/Foo.ts", "src/Foo.ts", null, 42, null, "head-sha", null, "start-sha");
            var context = new GitLabPullRequestReviewCommentProcessor.CommentContext(
                thread,
                pr,
                null,
                provider,
                null,
                null,
                SCOPE_ID
            );

            PullRequestReviewComment saved = processor.findOrCreateComment(data, context);

            assertThat(saved).isNotNull();
            assertThat(saved.getOriginalCommitId()).isEqualTo("start-sha");
        }

        @Test
        @DisplayName("should attach inReplyTo when reply context is provided")
        void shouldAttachInReplyToWhenReplyContextIsProvided() {
            when(commentRepository.findByNativeIdAndProviderId(NOTE_NATIVE_ID, PROVIDER_ID)).thenReturn(
                Optional.empty()
            );
            when(commentRepository.save(any(PullRequestReviewComment.class))).thenAnswer(inv ->
                inv.getArgument(0, PullRequestReviewComment.class)
            );

            PullRequestReviewComment parent = new PullRequestReviewComment();
            parent.setId(999L);

            var data = buildDiffNoteData("src/Foo.ts", null, null, 42, null, "head-sha", "base-sha", null);
            var context = new GitLabPullRequestReviewCommentProcessor.CommentContext(
                thread,
                pr,
                null,
                provider,
                parent,
                null,
                SCOPE_ID
            );

            PullRequestReviewComment saved = processor.findOrCreateComment(data, context);

            assertThat(saved).isNotNull();
            assertThat(saved.getInReplyTo()).isSameAs(parent);
        }

        @Test
        @DisplayName("should link review via addComment when review context is provided")
        void shouldLinkReviewWhenReviewContextIsProvided() {
            when(commentRepository.findByNativeIdAndProviderId(NOTE_NATIVE_ID, PROVIDER_ID)).thenReturn(
                Optional.empty()
            );
            when(commentRepository.save(any(PullRequestReviewComment.class))).thenAnswer(inv ->
                inv.getArgument(0, PullRequestReviewComment.class)
            );

            PullRequestReview review = new PullRequestReview();
            review.setId(777L);
            review.setComments(new HashSet<>());

            var data = buildDiffNoteData("src/Foo.ts", null, null, 42, null, "head-sha", "base-sha", null);
            var context = new GitLabPullRequestReviewCommentProcessor.CommentContext(
                thread,
                pr,
                null,
                provider,
                null,
                review,
                SCOPE_ID
            );

            PullRequestReviewComment saved = processor.findOrCreateComment(data, context);

            assertThat(saved).isNotNull();
            assertThat(saved.getReview()).isSameAs(review);
            assertThat(review.getComments()).contains(saved);
        }

        @Test
        @DisplayName("should publish ReviewCommentCreated event when a new comment is persisted")
        void shouldPublishReviewCommentCreatedEventWhenNewCommentIsPersisted() {
            when(commentRepository.findByNativeIdAndProviderId(NOTE_NATIVE_ID, PROVIDER_ID)).thenReturn(
                Optional.empty()
            );
            when(commentRepository.save(any(PullRequestReviewComment.class))).thenAnswer(inv ->
                inv.getArgument(0, PullRequestReviewComment.class)
            );

            var data = buildDiffNoteData("src/Foo.ts", null, null, 42, null, "head-sha", "base-sha", null);
            var context = new GitLabPullRequestReviewCommentProcessor.CommentContext(
                thread,
                pr,
                null,
                provider,
                null,
                null,
                SCOPE_ID
            );
            processor.findOrCreateComment(data, context);

            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue()).isInstanceOf(DomainEvent.ReviewCommentCreated.class);
        }

        @Test
        @DisplayName("should skip and return null when noteGlobalId cannot be parsed")
        void shouldSkipWhenNoteGlobalIdCannotBeParsed() {
            var data = new GitLabPullRequestReviewCommentProcessor.DiffNoteData(
                "not-a-gid",
                "body",
                "https://example",
                "p",
                1,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
            );
            var context = new GitLabPullRequestReviewCommentProcessor.CommentContext(
                thread,
                pr,
                null,
                provider,
                null,
                null,
                SCOPE_ID
            );

            PullRequestReviewComment saved = processor.findOrCreateComment(data, context);

            assertThat(saved).isNull();
            verify(commentRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    // ========================================================================
    // DiffNoteData backward-compat constructor
    // ========================================================================

    @Nested
    @DisplayName("DiffNoteData backward-compat")
    class DiffNoteDataBackwardCompat {

        @Test
        @DisplayName("should default startSha to null for 12-arg callers")
        void shouldDefaultStartShaToNullFor12ArgCallers() {
            var data = new GitLabPullRequestReviewCommentProcessor.DiffNoteData(
                "gid://gitlab/DiffNote/1",
                "body",
                "https://example",
                "src/Foo.ts",
                10,
                null,
                "src/Foo.ts",
                null,
                "head-sha",
                "base-sha",
                Instant.EPOCH,
                Instant.EPOCH
            );
            assertThat(data.startSha()).isNull();
            assertThat(data.baseSha()).isEqualTo("base-sha");
        }
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private static GitLabPullRequestReviewCommentProcessor.DiffNoteData buildDiffNoteData(
        String newPath,
        String oldPath,
        String filePath,
        Integer newLine,
        Integer oldLine,
        String headSha,
        String baseSha,
        String startSha
    ) {
        return new GitLabPullRequestReviewCommentProcessor.DiffNoteData(
            NOTE_GLOBAL_ID,
            "body",
            "https://example.com/note",
            filePath,
            newLine,
            oldLine,
            newPath,
            oldPath,
            headSha,
            baseSha,
            startSha,
            Instant.parse("2024-01-15T10:00:00Z"),
            Instant.parse("2024-01-15T10:00:00Z")
        );
    }

    @SuppressWarnings("unused")
    private static User dummyUser() {
        User user = new User();
        user.setLogin("nobody");
        return user;
    }

    @SuppressWarnings("unused")
    private static Object ignoreArg() {
        return anyLong();
    }
}
