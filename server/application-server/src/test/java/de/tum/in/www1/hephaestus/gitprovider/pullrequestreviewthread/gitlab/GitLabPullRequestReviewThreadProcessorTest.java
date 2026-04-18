package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.gitlab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.gitprovider.common.GitProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewComment;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThread;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThreadRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
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
@DisplayName("GitLabPullRequestReviewThreadProcessor")
class GitLabPullRequestReviewThreadProcessorTest extends BaseUnitTest {

    private static final long PROVIDER_ID = 2L;
    private static final long PR_ID = 100L;
    private static final long SCOPE_ID = 1L;
    private static final String DISCUSSION_GID = "gid://gitlab/Discussion/6a9c1750b37d4e";
    private static final Instant CREATED_AT = Instant.parse("2024-01-15T10:00:00Z");

    @Mock
    private PullRequestReviewThreadRepository threadRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private GitLabPullRequestReviewThreadProcessor processor;
    private GitProvider provider;
    private PullRequest pr;

    @BeforeEach
    void setUp() {
        processor = new GitLabPullRequestReviewThreadProcessor(threadRepository, eventPublisher);

        provider = new GitProvider();
        provider.setId(PROVIDER_ID);
        provider.setType(GitProviderType.GITLAB);

        pr = new PullRequest();
        pr.setId(PR_ID);
        pr.setProvider(provider);
    }

    // ========================================================================
    // deterministicNativeId
    // ========================================================================

    @Nested
    @DisplayName("deterministicNativeId")
    class DeterministicNativeId {

        @Test
        @DisplayName("should be deterministic for identical input")
        void shouldBeDeterministicForIdenticalInput() {
            long a = GitLabPullRequestReviewThreadProcessor.deterministicNativeId(DISCUSSION_GID);
            long b = GitLabPullRequestReviewThreadProcessor.deterministicNativeId(DISCUSSION_GID);
            assertThat(a).isEqualTo(b);
        }

        @Test
        @DisplayName("should always return a positive value")
        void shouldAlwaysReturnPositiveValue() {
            for (int i = 0; i < 50; i++) {
                String gid = "gid://gitlab/Discussion/" + Integer.toHexString(i * 997 + 13);
                assertThat(GitLabPullRequestReviewThreadProcessor.deterministicNativeId(gid)).isNotNegative();
            }
        }

        @Test
        @DisplayName("should produce distinct values for distinct discussion IDs")
        void shouldProduceDistinctValuesForDistinctDiscussionIds() {
            long a = GitLabPullRequestReviewThreadProcessor.deterministicNativeId("gid://gitlab/Discussion/aaaaaa");
            long b = GitLabPullRequestReviewThreadProcessor.deterministicNativeId("gid://gitlab/Discussion/bbbbbb");
            assertThat(a).isNotEqualTo(b);
        }
    }

    // ========================================================================
    // findOrCreateThread — creation populates position metadata
    // ========================================================================

    @Nested
    @DisplayName("findOrCreateThread — creation")
    class CreateThread {

        @Test
        @DisplayName("should populate path, line, side, commitSha, and originalCommitSha when creating a thread")
        void shouldPopulateAllPositionMetadataWhenCreatingThread() {
            when(threadRepository.findByNodeIdAndProviderId(DISCUSSION_GID, PROVIDER_ID)).thenReturn(Optional.empty());
            when(threadRepository.save(any(PullRequestReviewThread.class))).thenAnswer(inv ->
                inv.getArgument(0, PullRequestReviewThread.class)
            );

            var data = new GitLabPullRequestReviewThreadProcessor.ThreadData(
                DISCUSSION_GID,
                false,
                null,
                "src/Foo.ts",
                42,
                null,
                PullRequestReviewComment.Side.RIGHT,
                "head-sha",
                "base-sha",
                CREATED_AT
            );

            PullRequestReviewThread saved = processor.findOrCreateThread(data, pr, provider, SCOPE_ID);

            assertThat(saved).isNotNull();
            assertThat(saved.getPath()).isEqualTo("src/Foo.ts");
            assertThat(saved.getLine()).isEqualTo(42);
            assertThat(saved.getSide()).isEqualTo(PullRequestReviewComment.Side.RIGHT);
            // start_side mirrors side for single-line discussions (GraphQL has no line_range)
            assertThat(saved.getStartSide()).isEqualTo(PullRequestReviewComment.Side.RIGHT);
            assertThat(saved.getCommitSha()).isEqualTo("head-sha");
            assertThat(saved.getOriginalCommitSha()).isEqualTo("base-sha");
            assertThat(saved.getState()).isEqualTo(PullRequestReviewThread.State.UNRESOLVED);
        }

        @Test
        @DisplayName("should mark thread RESOLVED and set resolvedBy when discussion is resolved")
        void shouldMarkThreadResolvedWhenDiscussionIsResolved() {
            when(threadRepository.findByNodeIdAndProviderId(DISCUSSION_GID, PROVIDER_ID)).thenReturn(Optional.empty());
            when(threadRepository.save(any(PullRequestReviewThread.class))).thenAnswer(inv ->
                inv.getArgument(0, PullRequestReviewThread.class)
            );

            User resolver = new User();
            resolver.setLogin("resolver");

            var data = new GitLabPullRequestReviewThreadProcessor.ThreadData(
                DISCUSSION_GID,
                true,
                resolver,
                "src/Foo.ts",
                42,
                null,
                PullRequestReviewComment.Side.RIGHT,
                "head-sha",
                "base-sha",
                CREATED_AT
            );

            PullRequestReviewThread saved = processor.findOrCreateThread(data, pr, provider, SCOPE_ID);

            assertThat(saved).isNotNull();
            assertThat(saved.getState()).isEqualTo(PullRequestReviewThread.State.RESOLVED);
            assertThat(saved.getResolvedBy()).isSameAs(resolver);
        }

        @Test
        @DisplayName("should leave position metadata null when data has no position info (backward-compat 6-arg ctor)")
        void shouldLeavePositionNullWhenDataHasNoPositionInfo() {
            when(threadRepository.findByNodeIdAndProviderId(DISCUSSION_GID, PROVIDER_ID)).thenReturn(Optional.empty());
            when(threadRepository.save(any(PullRequestReviewThread.class))).thenAnswer(inv ->
                inv.getArgument(0, PullRequestReviewThread.class)
            );

            var data = new GitLabPullRequestReviewThreadProcessor.ThreadData(
                DISCUSSION_GID,
                false,
                null,
                null,
                null,
                CREATED_AT
            );

            PullRequestReviewThread saved = processor.findOrCreateThread(data, pr, provider, SCOPE_ID);

            assertThat(saved).isNotNull();
            assertThat(saved.getPath()).isNull();
            assertThat(saved.getLine()).isNull();
            assertThat(saved.getSide()).isNull();
            assertThat(saved.getCommitSha()).isNull();
            assertThat(saved.getOriginalCommitSha()).isNull();
        }
    }

    // ========================================================================
    // findOrCreateThread — update backfill
    // ========================================================================

    @Nested
    @DisplayName("findOrCreateThread — update backfill")
    class UpdateThreadBackfill {

        @Test
        @DisplayName("should backfill path, line, side, and commit shas when existing thread has null values")
        void shouldBackfillAllPositionFieldsWhenExistingHasNull() {
            PullRequestReviewThread existing = new PullRequestReviewThread();
            existing.setId(500L);
            existing.setNodeId(DISCUSSION_GID);
            existing.setProvider(provider);
            existing.setPullRequest(pr);
            existing.setState(PullRequestReviewThread.State.UNRESOLVED);
            // all metadata fields null to simulate legacy row

            when(threadRepository.findByNodeIdAndProviderId(DISCUSSION_GID, PROVIDER_ID)).thenReturn(
                Optional.of(existing)
            );
            when(threadRepository.save(any(PullRequestReviewThread.class))).thenAnswer(inv ->
                inv.getArgument(0, PullRequestReviewThread.class)
            );

            var data = new GitLabPullRequestReviewThreadProcessor.ThreadData(
                DISCUSSION_GID,
                false,
                null,
                "src/Foo.ts",
                42,
                null,
                PullRequestReviewComment.Side.RIGHT,
                "head-sha",
                "base-sha",
                CREATED_AT
            );

            PullRequestReviewThread result = processor.findOrCreateThread(data, pr, provider, SCOPE_ID);

            assertThat(result.getPath()).isEqualTo("src/Foo.ts");
            assertThat(result.getLine()).isEqualTo(42);
            assertThat(result.getSide()).isEqualTo(PullRequestReviewComment.Side.RIGHT);
            assertThat(result.getStartSide()).isEqualTo(PullRequestReviewComment.Side.RIGHT);
            assertThat(result.getCommitSha()).isEqualTo("head-sha");
            assertThat(result.getOriginalCommitSha()).isEqualTo("base-sha");
        }

        @Test
        @DisplayName("should not clobber existing values when backfilling")
        void shouldNotClobberExistingValuesWhenBackfilling() {
            PullRequestReviewThread existing = new PullRequestReviewThread();
            existing.setId(500L);
            existing.setNodeId(DISCUSSION_GID);
            existing.setProvider(provider);
            existing.setPullRequest(pr);
            existing.setState(PullRequestReviewThread.State.UNRESOLVED);
            existing.setPath("existing/path.ts");
            existing.setLine(99);
            existing.setSide(PullRequestReviewComment.Side.LEFT);
            existing.setStartSide(PullRequestReviewComment.Side.LEFT);
            existing.setCommitSha("existing-head");
            existing.setOriginalCommitSha("existing-base");

            when(threadRepository.findByNodeIdAndProviderId(DISCUSSION_GID, PROVIDER_ID)).thenReturn(
                Optional.of(existing)
            );

            var data = new GitLabPullRequestReviewThreadProcessor.ThreadData(
                DISCUSSION_GID,
                false,
                null,
                "incoming/path.ts",
                42,
                null,
                PullRequestReviewComment.Side.RIGHT,
                "incoming-head",
                "incoming-base",
                CREATED_AT
            );

            PullRequestReviewThread result = processor.findOrCreateThread(data, pr, provider, SCOPE_ID);

            assertThat(result.getPath()).isEqualTo("existing/path.ts");
            assertThat(result.getLine()).isEqualTo(99);
            assertThat(result.getSide()).isEqualTo(PullRequestReviewComment.Side.LEFT);
            assertThat(result.getStartSide()).isEqualTo(PullRequestReviewComment.Side.LEFT);
            assertThat(result.getCommitSha()).isEqualTo("existing-head");
            assertThat(result.getOriginalCommitSha()).isEqualTo("existing-base");
            // Nothing changed, so no save should happen
            verify(threadRepository, never()).save(any());
        }

        @Test
        @DisplayName("should transition state from UNRESOLVED to RESOLVED and publish ReviewThreadResolved")
        void shouldTransitionStateAndPublishResolvedEventWhenDiscussionResolvesExistingThread() {
            PullRequestReviewThread existing = new PullRequestReviewThread();
            existing.setId(500L);
            existing.setNodeId(DISCUSSION_GID);
            existing.setProvider(provider);
            existing.setPullRequest(pr);
            existing.setState(PullRequestReviewThread.State.UNRESOLVED);

            when(threadRepository.findByNodeIdAndProviderId(DISCUSSION_GID, PROVIDER_ID)).thenReturn(
                Optional.of(existing)
            );
            when(threadRepository.save(any(PullRequestReviewThread.class))).thenAnswer(inv ->
                inv.getArgument(0, PullRequestReviewThread.class)
            );

            User resolver = new User();
            resolver.setLogin("resolver");

            var data = new GitLabPullRequestReviewThreadProcessor.ThreadData(
                DISCUSSION_GID,
                true,
                resolver,
                null,
                null,
                null,
                null,
                null,
                null,
                CREATED_AT
            );

            processor.findOrCreateThread(data, pr, provider, SCOPE_ID);

            assertThat(existing.getState()).isEqualTo(PullRequestReviewThread.State.RESOLVED);
            assertThat(existing.getResolvedBy()).isSameAs(resolver);

            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue().getClass().getSimpleName()).isEqualTo("ReviewThreadResolved");
        }
    }

    // ========================================================================
    // ThreadData backward-compat 6-arg constructor
    // ========================================================================

    @Nested
    @DisplayName("ThreadData backward-compat")
    class ThreadDataBackwardCompat {

        @Test
        @DisplayName("should default side and commit shas to null for 6-arg callers")
        void shouldDefaultOptionalFieldsToNullFor6ArgCallers() {
            var data = new GitLabPullRequestReviewThreadProcessor.ThreadData(
                DISCUSSION_GID,
                false,
                null,
                "src/Foo.ts",
                42,
                CREATED_AT
            );

            assertThat(data.side()).isNull();
            assertThat(data.oldLine()).isNull();
            assertThat(data.commitSha()).isNull();
            assertThat(data.originalCommitSha()).isNull();
            assertThat(data.filePath()).isEqualTo("src/Foo.ts");
            assertThat(data.newLine()).isEqualTo(42);
        }
    }
}
