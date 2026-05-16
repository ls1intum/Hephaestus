package de.tum.in.www1.hephaestus.agent.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.agent.handler.spi.JobPreparationException;
import de.tum.in.www1.hephaestus.agent.job.AgentJob;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;

@DisplayName("WorkspaceContextBuilder")
class WorkspaceContextBuilderTest extends BaseUnitTest {

    private static AgentJob anyJob() {
        var job = new AgentJob();
        job.setId(UUID.randomUUID());
        return job;
    }

    private static ContextRequest.PracticeReviewRequest reviewRequest() {
        return new ContextRequest.PracticeReviewRequest(anyJob());
    }

    private static WorkspaceContextBuilder builderOf(ContentProvider... providers) {
        return new WorkspaceContextBuilder(List.of(providers), new SimpleMeterRegistry());
    }

    private static SimpleMeterRegistry sharedRegistry;

    private static WorkspaceContextBuilder builderWithSharedRegistry(ContentProvider... providers) {
        sharedRegistry = new SimpleMeterRegistry();
        return new WorkspaceContextBuilder(List.of(providers), sharedRegistry);
    }

    /** Helper to construct a stub provider inline. */
    private static ContentProvider stubProvider(
        boolean required,
        String pathSuffix,
        byte[] payload,
        boolean throwError
    ) {
        return new ContentProvider() {
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
    @DisplayName("metrics")
    class Metrics {

        @Test
        @DisplayName("records build duration timer keyed by request kind")
        void recordsBuildDurationTimer() {
            var p = stubProvider(true, "a.txt", "A".getBytes(StandardCharsets.UTF_8), false);
            builderWithSharedRegistry(p).build(reviewRequest());
            assertThat(
                sharedRegistry.timer("agent.context.build.duration", "kind", "PracticeReviewRequest").count()
            ).isEqualTo(1L);
        }

        @Test
        @DisplayName("emits required-failure counter before throwing")
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
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName("invokes all matching providers")
        void invokesAllMatching() {
            var a = stubProvider(true, "a.txt", "A".getBytes(StandardCharsets.UTF_8), false);
            var b = stubProvider(false, "b.txt", "B".getBytes(StandardCharsets.UTF_8), false);

            Map<String, byte[]> files = builderOf(a, b).build(reviewRequest());

            assertThat(files).hasSize(2);
            assertThat(files.get("context/target/a.txt")).asString(StandardCharsets.UTF_8).isEqualTo("A");
            assertThat(files.get("context/target/b.txt")).asString(StandardCharsets.UTF_8).isEqualTo("B");
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
            var skips = new ContentProvider() {
                @Override
                public boolean supports(ContextRequest request) {
                    return false;
                }

                @Override
                public void contribute(ContextRequest request, Map<String, byte[]> files) {
                    files.put("context/target/should-not-appear.txt", new byte[0]);
                }
            };
            Map<String, byte[]> files = builderOf(skips, supports).build(reviewRequest());
            assertThat(files).containsOnlyKeys("context/target/a.txt");
        }
    }

    @Nested
    @DisplayName("failure policy")
    class FailurePolicy {

        @Test
        @DisplayName("REQUIRED provider failure translates to JobPreparationException")
        void requiredFailureThrows() {
            var bad = stubProvider(true, "x.txt", new byte[0], true);
            assertThatThrownBy(() -> builderOf(bad).build(reviewRequest()))
                .isInstanceOf(JobPreparationException.class)
                .hasMessageContaining("Required content provider failed");
        }

        @Test
        @DisplayName("OPTIONAL provider failure is logged and skipped")
        void optionalFailureSkipped() {
            var bad = stubProvider(false, "x.txt", new byte[0], true);
            var good = stubProvider(true, "y.txt", "Y".getBytes(StandardCharsets.UTF_8), false);
            Map<String, byte[]> files = builderOf(bad, good).build(reviewRequest());
            assertThat(files).containsOnlyKeys("context/target/y.txt");
        }

        @Test
        @DisplayName("re-raises JobPreparationException without re-wrapping")
        void jpePassThrough() {
            var bad = new ContentProvider() {
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
    @DisplayName("output-key dedup")
    class OutputKeyDedup {

        @Test
        @DisplayName("two providers writing the same path is a wiring bug")
        void detectsConflictingKey() {
            // Distinct concrete classes so dedup distinguishes ownership.
            ContentProvider first = new ProviderA();
            ContentProvider second = new ProviderB();
            assertThatThrownBy(() -> builderOf(first, second).build(reviewRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate workspace key");
        }

        private final class ProviderA implements ContentProvider {

            @Override
            public boolean supports(ContextRequest request) {
                return true;
            }

            @Override
            public void contribute(ContextRequest request, Map<String, byte[]> files) {
                files.put(OUTPUT_PREFIX + "shared.txt", "FIRST".getBytes(StandardCharsets.UTF_8));
            }
        }

        private final class ProviderB implements ContentProvider {

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
    @DisplayName("path-prefix enforcement")
    class PrefixEnforcement {

        @Test
        @DisplayName("rejects providers that write outside context/target/")
        void rejectsBadPrefix() {
            var wrong = new ContentProvider() {
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
    @DisplayName("provider ordering")
    class ProviderOrdering {

        @Test
        @DisplayName("Ordered providers run in ascending precedence (lower order first)")
        void respectsOrderedInterface() {
            var first = new OrderedStubProvider(1, "first.txt");
            var second = new OrderedStubProvider(2, "second.txt");
            // Inject in reverse — sort should pick {first, second}.
            Map<String, byte[]> files = builderOf(second, first).build(reviewRequest());
            var iter = files.keySet().iterator();
            assertThat(iter.next()).isEqualTo("context/target/first.txt");
            assertThat(iter.next()).isEqualTo("context/target/second.txt");
        }
    }

    @Nested
    @DisplayName("stripe-key fan-out")
    class StripeKey {

        @Test
        @DisplayName("striping for mentor requests is not degenerate (>= 32/64 stripes used over 100 pairs)")
        void stripingForMentorRequestsIsNotDegenerate() {
            // Pre-#1081 bug: repoKey returned null for MentorChatRequest, collapsing every concurrent
            // mentor session onto stripe 0 (Math.floorMod(0, 64) == 0). Inputs are drawn from a
            // seeded Random so the test is deterministic AND uncorrelated — DB-generated user/workspace
            // IDs in production are independent sequences, so randomly-paired ids mirror the real
            // workload better than an arithmetic progression (which can collide on low bits with the
            // simple `h(c)*31 ^ h(w)` mix). Threshold: at least half of 64 stripes used.
            int stripes = 64;
            java.util.Random rng = new java.util.Random(0xC0FFEE);
            java.util.Set<Integer> seen = new java.util.HashSet<>();
            for (int i = 0; i < 100; i++) {
                long contributorId = 1L + (rng.nextLong() & 0x0FFFFFFFFFFFFFFFL);
                long workspaceId = 1L + (rng.nextLong() & 0x0FFFFFFFFFFFFFFFL);
                ContextRequest.MentorChatRequest req = new ContextRequest.MentorChatRequest(
                    workspaceId,
                    contributorId,
                    UUID.nameUUIDFromBytes(("t-" + i).getBytes(StandardCharsets.UTF_8))
                );
                int idx = Math.floorMod(WorkspaceContextBuilder.stripeKey(req), stripes);
                seen.add(idx);
            }
            assertThat(seen)
                .as("distinct stripe count over 100 mentor (contributorId, workspaceId) pairs")
                .hasSizeGreaterThanOrEqualTo(32);
        }

        @Test
        @DisplayName("two mentor requests with the same (contributorId, workspaceId) land on the same stripe")
        void mentorStripeKeyStableForSamePair() {
            ContextRequest.MentorChatRequest a = new ContextRequest.MentorChatRequest(42L, 7L, UUID.randomUUID());
            ContextRequest.MentorChatRequest b = new ContextRequest.MentorChatRequest(42L, 7L, UUID.randomUUID());
            assertThat(WorkspaceContextBuilder.stripeKey(a)).isEqualTo(WorkspaceContextBuilder.stripeKey(b));
        }
    }

    @Nested
    @DisplayName("single-flight per repository")
    class SingleFlight {

        @Test
        @DisplayName("a second build against the same repo blocks while the first is in-flight")
        void serialisesOnRepoId() throws Exception {
            java.util.concurrent.CountDownLatch firstInside = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.CountDownLatch firstMayFinish = new java.util.concurrent.CountDownLatch(1);
            ContentProvider gatedFirst = new LatchedProvider(firstInside, firstMayFinish);
            ContentProvider unboundedSecond = new LatchedProvider(null, null);
            // Two distinct concrete provider classes → injection-order semantics, not dedup.
            var builder = new WorkspaceContextBuilder(List.of(gatedFirst, unboundedSecond), new SimpleMeterRegistry());

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
    private static final class LatchedProvider implements ContentProvider {

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

    /** Ordered provider: writes a single file; reports a fixed precedence. */
    private static final class OrderedStubProvider implements ContentProvider, Ordered {

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
