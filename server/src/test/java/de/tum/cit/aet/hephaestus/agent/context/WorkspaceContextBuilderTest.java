package de.tum.cit.aet.hephaestus.agent.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.agent.handler.spi.JobPreparationException;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import tools.jackson.databind.ObjectMapper;

class WorkspaceContextBuilderTest extends BaseUnitTest {

    private static AgentJob anyJob() {
        var job = new AgentJob();
        job.setId(UUID.randomUUID());
        return job;
    }

    private static ContextRequest.PracticeReviewRequest reviewRequest() {
        return new ContextRequest.PracticeReviewRequest(anyJob());
    }

    private static WorkspaceContextBuilder builderOf(ContentSource... providers) {
        return new WorkspaceContextBuilder(List.of(providers), new SimpleMeterRegistry(), null);
    }

    private static SimpleMeterRegistry sharedRegistry;

    private static WorkspaceContextBuilder builderWithSharedRegistry(ContentSource... providers) {
        sharedRegistry = new SimpleMeterRegistry();
        return new WorkspaceContextBuilder(List.of(providers), sharedRegistry, null);
    }

    /** Helper to construct a stub provider inline. */
    private static ContentSource stubProvider(boolean required, String pathSuffix, byte[] payload, boolean throwError) {
        return new ContentSource() {
            @Override
            public String originId() {
                return "test";
            }

            @Override
            public boolean supports(ContextRequest request) {
                return true;
            }

            @Override
            public boolean required() {
                return required;
            }

            @Override
            public void contribute(ContextRequest request, Map<String, byte[]> files) {
                if (throwError) {
                    throw new IllegalStateException("provider boom");
                }
                files.put(OUTPUT_PREFIX + pathSuffix, payload);
            }
        };
    }

    @Nested
    class Metrics {

        @Test
        void recordsBuildDurationTimer() {
            var p = stubProvider(true, "a.txt", "A".getBytes(StandardCharsets.UTF_8), false);
            builderWithSharedRegistry(p).build(reviewRequest());
            assertThat(
                sharedRegistry.timer("agent.context.build.duration", "kind", "PracticeReviewRequest").count()
            ).isEqualTo(1L);
        }

        @Test
        void emitsRequiredFailureCounter() {
            var bad = stubProvider(true, "x.txt", new byte[0], true);
            try {
                builderWithSharedRegistry(bad).build(reviewRequest());
            } catch (JobPreparationException expected) {
                // expected
            }
            // The provider class name is appended as the `provider` tag.
            String providerName = bad.getClass().getSimpleName();
            assertThat(
                sharedRegistry.counter("agent.context.provider.required.failure", "provider", providerName).count()
            ).isEqualTo(1d);
        }
    }

    @Nested
    class HappyPath {

        @Test
        void invokesAllMatching() {
            var a = stubProvider(true, "a.txt", "A".getBytes(StandardCharsets.UTF_8), false);
            var b = stubProvider(false, "b.txt", "B".getBytes(StandardCharsets.UTF_8), false);

            Map<String, byte[]> files = builderOf(a, b).build(reviewRequest());

            assertThat(files).hasSize(2);
            assertThat(files.get("inputs/context/a.txt")).asString(StandardCharsets.UTF_8).isEqualTo("A");
            assertThat(files.get("inputs/context/b.txt")).asString(StandardCharsets.UTF_8).isEqualTo("B");
        }

        @Test
        @DisplayName("empty provider list returns empty file map")
        void emptyProvidersReturnsEmpty() {
            assertThat(builderOf().build(reviewRequest())).isEmpty();
        }

        @Test
        @DisplayName("skips providers that do not support the request")
        void skipsUnsupported() {
            var supports = stubProvider(true, "a.txt", "A".getBytes(StandardCharsets.UTF_8), false);
            var skips = new ContentSource() {
                @Override
                public String originId() {
                    return "test";
                }

                @Override
                public boolean supports(ContextRequest request) {
                    return false;
                }

                @Override
                public void contribute(ContextRequest request, Map<String, byte[]> files) {
                    files.put("inputs/context/should-not-appear.txt", new byte[0]);
                }
            };
            Map<String, byte[]> files = builderOf(skips, supports).build(reviewRequest());
            assertThat(files).containsOnlyKeys("inputs/context/a.txt");
        }
    }

    @Nested
    class FailurePolicy {

        @Test
        void requiredFailureThrows() {
            var bad = stubProvider(true, "x.txt", new byte[0], true);
            assertThatThrownBy(() -> builderOf(bad).build(reviewRequest()))
                .isInstanceOf(JobPreparationException.class)
                .hasMessageContaining("Required content provider failed");
        }

        @Test
        void optionalFailureSkipped() {
            var bad = stubProvider(false, "x.txt", new byte[0], true);
            var good = stubProvider(true, "y.txt", "Y".getBytes(StandardCharsets.UTF_8), false);
            Map<String, byte[]> files = builderOf(bad, good).build(reviewRequest());
            assertThat(files).containsOnlyKeys("inputs/context/y.txt");
        }

        @Test
        @DisplayName("re-raises JobPreparationException without re-wrapping")
        void jpePassThrough() {
            var bad = new ContentSource() {
                @Override
                public String originId() {
                    return "test";
                }

                @Override
                public boolean supports(ContextRequest request) {
                    return true;
                }

                @Override
                public void contribute(ContextRequest request, Map<String, byte[]> files) {
                    throw new JobPreparationException("data error");
                }
            };
            assertThatThrownBy(() -> builderOf(bad).build(reviewRequest()))
                .isInstanceOf(JobPreparationException.class)
                .hasMessage("data error");
        }
    }

    @Nested
    class OutputKeyDedup {

        @Test
        @DisplayName("two providers writing the same path is a wiring bug")
        void detectsConflictingKey() {
            // Distinct concrete classes so dedup distinguishes ownership.
            ContentSource first = new ProviderA();
            ContentSource second = new ProviderB();
            assertThatThrownBy(() -> builderOf(first, second).build(reviewRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate workspace key");
        }

        private final class ProviderA implements ContentSource {

            @Override
            public String originId() {
                return "test";
            }

            @Override
            public boolean supports(ContextRequest request) {
                return true;
            }

            @Override
            public void contribute(ContextRequest request, Map<String, byte[]> files) {
                files.put(OUTPUT_PREFIX + "shared.txt", "FIRST".getBytes(StandardCharsets.UTF_8));
            }
        }

        private final class ProviderB implements ContentSource {

            @Override
            public String originId() {
                return "test";
            }

            @Override
            public boolean supports(ContextRequest request) {
                return true;
            }

            @Override
            public void contribute(ContextRequest request, Map<String, byte[]> files) {
                files.put(OUTPUT_PREFIX + "shared.txt", "SECOND".getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    @Nested
    class PrefixEnforcement {

        @Test
        @DisplayName("rejects providers that write outside inputs/context/")
        void rejectsBadPrefix() {
            var wrong = new ContentSource() {
                @Override
                public String originId() {
                    return "test";
                }

                @Override
                public boolean supports(ContextRequest request) {
                    return true;
                }

                @Override
                public void contribute(ContextRequest request, Map<String, byte[]> files) {
                    files.put("rogue/file.txt", new byte[0]);
                }
            };
            assertThatThrownBy(() -> builderOf(wrong).build(reviewRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rogue/file.txt");
        }
    }

    @Nested
    class ProviderOrdering {

        @Test
        @DisplayName("Ordered providers run in ascending precedence (lower order first)")
        void respectsOrderedInterface() {
            var first = new OrderedStubProvider(1, "first.txt");
            var second = new OrderedStubProvider(2, "second.txt");
            // Inject in reverse — sort should pick {first, second}.
            Map<String, byte[]> files = builderOf(second, first).build(reviewRequest());
            var iter = files.keySet().iterator();
            assertThat(iter.next()).isEqualTo("inputs/context/first.txt");
            assertThat(iter.next()).isEqualTo("inputs/context/second.txt");
        }
    }

    @Nested
    class SingleFlight {

        @Test
        @DisplayName("a second build against the same repo blocks while the first is in-flight")
        void serialisesOnRepoId() throws Exception {
            java.util.concurrent.CountDownLatch firstInside = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.CountDownLatch firstMayFinish = new java.util.concurrent.CountDownLatch(1);
            ContentSource gatedFirst = new LatchedProvider(firstInside, firstMayFinish);
            ContentSource unboundedSecond = new LatchedProvider(null, null);
            // Two distinct concrete provider classes → injection-order semantics, not dedup.
            var builder = new WorkspaceContextBuilder(
                List.of(gatedFirst, unboundedSecond),
                new SimpleMeterRegistry(),
                null
            );

            ObjectMapper mapper = new ObjectMapper();
            AgentJob jobA = new AgentJob();
            jobA.setId(UUID.randomUUID());
            jobA.setMetadata(mapper.createObjectNode().put("repository_id", 7L));
            AgentJob jobB = new AgentJob();
            jobB.setId(UUID.randomUUID());
            jobB.setMetadata(mapper.createObjectNode().put("repository_id", 7L));

            Thread t1 = new Thread(() -> builder.build(new ContextRequest.PracticeReviewRequest(jobA)), "t1");
            Thread t2 = new Thread(() -> builder.build(new ContextRequest.PracticeReviewRequest(jobB)), "t2");
            t1.start();
            assertThat(firstInside.await(2, java.util.concurrent.TimeUnit.SECONDS))
                .as("t1 should enter the critical section quickly")
                .isTrue();
            t2.start();
            // Spin (with timeout) until t2 has parked on the lock. unboundedSecond's `entered`
            // latch is null, so if t2 ran ahead it would already be past the latch — but it
            // can't, because gatedFirst still holds the stripe lock. We assert t2 reaches a
            // wait/block state without a fixed sleep.
            awaitState(
                t2,
                java.util.Set.of(Thread.State.WAITING, Thread.State.TIMED_WAITING, Thread.State.BLOCKED),
                2_000
            );
            firstMayFinish.countDown();
            t1.join(2_000);
            t2.join(2_000);
            assertThat(t1.isAlive()).isFalse();
            assertThat(t2.isAlive()).isFalse();
        }

        @Test
        @DisplayName("null repoKey requests do not serialise globally")
        void nullRepoKeyRequestsCanRunConcurrently() throws Exception {
            java.util.concurrent.CountDownLatch bothInside = new java.util.concurrent.CountDownLatch(2);
            java.util.concurrent.CountDownLatch mayFinish = new java.util.concurrent.CountDownLatch(1);
            AtomicInteger inFlight = new AtomicInteger();
            AtomicInteger maxInFlight = new AtomicInteger();
            ContentSource concurrentProbe = new ConcurrentProbeProvider(bothInside, mayFinish, inFlight, maxInFlight);
            var builder = new WorkspaceContextBuilder(List.of(concurrentProbe), new SimpleMeterRegistry(), null);

            // IssueReviewRequest jobs without repository_id metadata have no git worktree to protect.
            // Serialising all such requests behind stripe 0 would throttle Slack/web mentor context builds.
            AgentJob jobA = new AgentJob();
            jobA.setId(UUID.randomUUID());
            AgentJob jobB = new AgentJob();
            jobB.setId(UUID.randomUUID());

            Thread t1 = new Thread(() -> builder.build(new ContextRequest.IssueReviewRequest(jobA)), "t1-null");
            Thread t2 = new Thread(() -> builder.build(new ContextRequest.IssueReviewRequest(jobB)), "t2-null");
            t1.start();
            t2.start();
            try {
                assertThat(bothInside.await(2, java.util.concurrent.TimeUnit.SECONDS))
                    .as("both null-repo builds should enter the provider concurrently")
                    .isTrue();
            } finally {
                mayFinish.countDown();
            }
            t1.join(2_000);
            t2.join(2_000);
            assertThat(t1.isAlive()).isFalse();
            assertThat(t2.isAlive()).isFalse();
            assertThat(maxInFlight.get()).isEqualTo(2);
        }
    }

    /** Wait until {@code thread} enters one of {@code wanted} or the timeout elapses. */
    private static void awaitState(Thread thread, java.util.Set<Thread.State> wanted, long timeoutMillis)
        throws InterruptedException {
        long deadline = System.nanoTime() + timeoutMillis * 1_000_000L;
        while (System.nanoTime() < deadline) {
            if (wanted.contains(thread.getState())) {
                return;
            }
            Thread.onSpinWait();
            Thread.sleep(1);
        }
        throw new AssertionError(
            "Thread " + thread.getName() + " never reached " + wanted + " (current=" + thread.getState() + ")"
        );
    }

    /**
     * Provider that releases on {@code entered} and waits on {@code mayFinish}. Both latches
     * may be {@code null} for the unbounded variant.
     */
    private static final class LatchedProvider implements ContentSource {

        @Override
        public String originId() {
            return "test";
        }

        private final java.util.concurrent.CountDownLatch entered;
        private final java.util.concurrent.CountDownLatch mayFinish;

        LatchedProvider(java.util.concurrent.CountDownLatch entered, java.util.concurrent.CountDownLatch mayFinish) {
            this.entered = entered;
            this.mayFinish = mayFinish;
        }

        @Override
        public boolean supports(ContextRequest request) {
            return true;
        }

        @Override
        public void contribute(ContextRequest request, Map<String, byte[]> files) {
            if (entered != null) {
                entered.countDown();
            }
            if (mayFinish != null) {
                try {
                    mayFinish.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            files.put(OUTPUT_PREFIX + "marker-" + System.nanoTime() + ".txt", new byte[0]);
        }
    }

    /** Provider that records whether two builds were allowed into the provider body at the same time. */
    private static final class ConcurrentProbeProvider implements ContentSource {

        private final java.util.concurrent.CountDownLatch bothInside;
        private final java.util.concurrent.CountDownLatch mayFinish;
        private final AtomicInteger inFlight;
        private final AtomicInteger maxInFlight;

        ConcurrentProbeProvider(
            java.util.concurrent.CountDownLatch bothInside,
            java.util.concurrent.CountDownLatch mayFinish,
            AtomicInteger inFlight,
            AtomicInteger maxInFlight
        ) {
            this.bothInside = bothInside;
            this.mayFinish = mayFinish;
            this.inFlight = inFlight;
            this.maxInFlight = maxInFlight;
        }

        @Override
        public String originId() {
            return "test";
        }

        @Override
        public boolean supports(ContextRequest request) {
            return true;
        }

        @Override
        public void contribute(ContextRequest request, Map<String, byte[]> files) {
            int active = inFlight.incrementAndGet();
            maxInFlight.accumulateAndGet(active, Math::max);
            bothInside.countDown();
            try {
                mayFinish.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                inFlight.decrementAndGet();
            }
            files.put(OUTPUT_PREFIX + "marker-" + System.nanoTime() + ".txt", new byte[0]);
        }
    }

    /** Ordered provider: writes a single file; reports a fixed precedence. */
    private static final class OrderedStubProvider implements ContentSource, Ordered {

        @Override
        public String originId() {
            return "test";
        }

        private final int order;
        private final String pathSuffix;

        OrderedStubProvider(int order, String pathSuffix) {
            this.order = order;
            this.pathSuffix = pathSuffix;
        }

        @Override
        public int getOrder() {
            return order;
        }

        @Override
        public boolean supports(ContextRequest request) {
            return true;
        }

        @Override
        public void contribute(ContextRequest request, Map<String, byte[]> files) {
            files.put(OUTPUT_PREFIX + pathSuffix, new byte[0]);
        }
    }
}
