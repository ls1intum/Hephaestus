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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Proves the per-user advisory lock actually serializes achievement evaluation against a real
 * Postgres (the unit test mocks the repository and cannot exercise {@code pg_advisory_xact_lock}).
 *
 * <p>Pre-fix, concurrent same-user events raced on the {@code @Version} column and threw
 * {@link ObjectOptimisticLockingFailureException} after retries exhausted, losing increments. With
 * the lock, same-user evaluation runs one transaction at a time, so every increment lands.
 */
class AchievementConcurrencyIntegrationTest extends BaseIntegrationTest {

    // A high-target linear achievement so many increments are needed (and none unlock at 40 events).
    private static final String LEGENDARY = "pr.merged.legendary"; // StandardCountEvaluator, target 50
    private static final int THREADS = 40;

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

        List<Throwable> escaped = runConcurrently(THREADS, () -> achievementService.checkAndUnlock(event));

        assertThat(escaped)
            .as("the advisory lock must serialize same-user evaluation; nothing should escape")
            .isEmpty();

        LinearAchievementProgress progress = readProgress(user.getId(), LEGENDARY);
        assertThat(progress.current())
            .as("%d serialized increments must all land (no lost updates)", THREADS)
            .isEqualTo(THREADS);
        // 40 < target 50, so it stays locked.
        assertThat(readUnlockedAt(user.getId(), LEGENDARY)).isNull();
        assertThat(userAchievementRepository.findByUserIdAndAchievementId(user.getId(), LEGENDARY)).isPresent();
    }

    @Test
    @DisplayName("concurrent different-user events run in parallel and each lands exactly once")
    void differentUsersConcurrent() throws Exception {
        int userCount = 20;
        List<User> users = new ArrayList<>();
        for (int i = 0; i < userCount; i++) {
            users.add(persistUser("parallel-user-" + i));
        }

        List<Throwable> escaped = runConcurrentlyEach(users, user ->
            achievementService.checkAndUnlock(mergedEvent(user, (long) user.hashCode()))
        );

        assertThat(escaped).as("per-user locks must not serialize different users into failures").isEmpty();
        for (User user : users) {
            assertThat(readProgress(user.getId(), LEGENDARY).current())
                .as("each user's single event must produce exactly one increment")
                .isEqualTo(1);
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

    /** Run the same task on {@code threads} threads released together, returning any escaped throwables. */
    private List<Throwable> runConcurrently(int threads, Runnable task) throws InterruptedException {
        return runConcurrentlyEach(Collections.nCopies(threads, (Void) null), ignored -> task.run());
    }

    /** Run one task per item concurrently (all released together), returning any escaped throwables. */
    private <T> List<Throwable> runConcurrentlyEach(List<T> items, java.util.function.Consumer<T> task)
        throws InterruptedException {
        List<Throwable> escaped = Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(items.size());
        CountDownLatch ready = new CountDownLatch(items.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (T item : items) {
                futures.add(
                    pool.submit(() -> {
                        ready.countDown();
                        try {
                            start.await();
                            task.accept(item);
                        } catch (Throwable t) {
                            escaped.add(t);
                        }
                    })
                );
            }
            ready.await(); // every worker parked on `start` → maximize overlap
            start.countDown();
            for (Future<?> f : futures) {
                f.get(30, TimeUnit.SECONDS);
            }
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
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
