package de.tum.cit.aet.hephaestus.achievement;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.achievement.progress.LinearAchievementProgress;
import de.tum.cit.aet.hephaestus.activity.ActivityEventType;
import de.tum.cit.aet.hephaestus.activity.ActivitySavedEvent;
import de.tum.cit.aet.hephaestus.activity.ActivityTargetType;
import de.tum.cit.aet.hephaestus.integration.core.connection.GitProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.GitProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.GitProviderType;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Proves the per-user advisory lock serializes achievement evaluation against a real Postgres
 * (the unit test mocks the repository and cannot exercise {@code pg_advisory_xact_lock}).
 *
 * <p>The guard is deterministic: {@value #THREADS} transactions hammer the same versioned
 * {@link UserAchievement} row at <em>full</em> concurrency (the Hikari pool is widened below so the
 * threads are not serialized by the connection pool). Without the lock, {@code @Retryable}'s 3
 * attempts cannot absorb that contention — each retry round lets only one writer win, so most
 * threads exhaust their retries and either throw {@link ObjectOptimisticLockingFailureException} or
 * lose their increment. With the lock, the writers serialize cleanly: zero failures, every
 * increment lands. So reverting {@code acquireUserLock} turns this test red.
 */
class AchievementConcurrencyIntegrationTest extends BaseIntegrationTest {

    // High-target linear achievement: 40 increments need many writes and never unlock (target 50).
    private static final String LEGENDARY = "pr.merged.legendary";
    private static final int THREADS = 40;

    @DynamicPropertySource
    static void widenConnectionPool(DynamicPropertyRegistry registry) {
        // Every worker holds a connection while blocked on the advisory lock, so the pool must fit
        // all threads — otherwise workers fail with connection-timeouts unrelated to the behaviour
        // under test. Disable leak detection: serialized lock holders legitimately hold connections.
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> THREADS + 4);
        registry.add("spring.datasource.hikari.connection-timeout", () -> "30000");
        registry.add("spring.datasource.hikari.leak-detection-threshold", () -> "0");
    }

    @Autowired
    private AchievementService achievementService;

    @Autowired
    private UserAchievementRepository userAchievementRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GitProviderRepository gitProviderRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final AtomicLong nativeIdGenerator = new AtomicLong(70_000);

    @BeforeEach
    void resetDatabase() {
        databaseTestUtils.cleanDatabase();
    }

    @Test
    @DisplayName("concurrent same-user events serialize: no optimistic-lock failures, no lost increments")
    void sameUserConcurrentEvents() throws Exception {
        User user = persistUser("concurrency-victim");
        ActivitySavedEvent event = mergedEvent(user, 1L);

        List<Throwable> escaped = runConcurrently(THREADS, i -> achievementService.checkAndUnlock(event));

        assertThat(escaped)
            .as("the advisory lock must serialize same-user evaluation; nothing should escape")
            .isEmpty();
        assertThat(readProgress(user.getId(), LEGENDARY).current())
            .as("all %d serialized increments must land (no lost updates)", THREADS)
            .isEqualTo(THREADS);
        assertThat(readUnlockedAt(user.getId(), LEGENDARY)).as("40 < target 50, so it stays locked").isNull();
    }

    @Test
    @DisplayName("concurrent different-user events each land exactly once (independent per-user state)")
    void differentUsersConcurrent() throws Exception {
        int userCount = THREADS;
        List<User> users = new ArrayList<>();
        for (int i = 0; i < userCount; i++) {
            users.add(persistUser("parallel-user-" + i));
        }

        List<Throwable> escaped = runConcurrently(userCount, i ->
            achievementService.checkAndUnlock(mergedEvent(users.get(i), (long) i))
        );

        assertThat(escaped).as("per-user locks must not turn different users into failures").isEmpty();
        for (User user : users) {
            assertThat(readProgress(user.getId(), LEGENDARY).current()).isEqualTo(1);
        }
    }

    // --- helpers ---

    private ActivitySavedEvent mergedEvent(User user, Long targetId) {
        return new ActivitySavedEvent(
            Optional.of(user),
            ActivityEventType.PULL_REQUEST_MERGED,
            Instant.parse("2024-08-15T10:00:00Z"),
            1L,
            ActivityTargetType.PULL_REQUEST,
            targetId
        );
    }

    /**
     * Run {@code parallelism} tasks released together (via a start latch) to maximise transaction
     * overlap, and collect any throwable each task escaped with.
     */
    private List<Throwable> runConcurrently(int parallelism, IntConsumer task) throws InterruptedException {
        List<Throwable> escaped = Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(parallelism);
        CountDownLatch ready = new CountDownLatch(parallelism);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < parallelism; i++) {
                int index = i;
                futures.add(
                    pool.submit(() -> {
                        ready.countDown();
                        try {
                            start.await();
                            task.accept(index);
                        } catch (Throwable t) {
                            escaped.add(t);
                        }
                    })
                );
            }
            ready.await(); // every worker parked on `start` → release together for maximum overlap
            start.countDown();
            for (Future<?> f : futures) {
                f.get(60, TimeUnit.SECONDS);
            }
        } catch (ExecutionException | TimeoutException e) {
            escaped.add(e);
        } finally {
            pool.shutdownNow();
        }
        return escaped;
    }

    /** Read progress in a fresh transaction (worker transactions have committed; avoid a stale context). */
    private LinearAchievementProgress readProgress(Long userId, String achievementId) {
        return (LinearAchievementProgress) transactionTemplate.execute(status ->
            userAchievementRepository
                .findByUserIdAndAchievementId(userId, achievementId)
                .orElseThrow()
                .getProgressData()
        );
    }

    private Instant readUnlockedAt(Long userId, String achievementId) {
        return transactionTemplate.execute(status ->
            userAchievementRepository.findByUserIdAndAchievementId(userId, achievementId).orElseThrow().getUnlockedAt()
        );
    }

    private User persistUser(String login) {
        GitProvider provider = gitProviderRepository
            .findByTypeAndServerUrl(GitProviderType.GITHUB, "https://github.com")
            .orElseGet(() -> gitProviderRepository.save(new GitProvider(GitProviderType.GITHUB, "https://github.com")));
        User user = new User();
        user.setNativeId(nativeIdGenerator.incrementAndGet());
        user.setProvider(provider);
        user.setLogin(login);
        user.setName("User " + login);
        user.setType(User.Type.USER);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        return userRepository.save(user);
    }
}
