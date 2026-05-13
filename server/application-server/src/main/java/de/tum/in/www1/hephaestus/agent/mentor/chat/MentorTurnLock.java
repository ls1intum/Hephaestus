package de.tum.in.www1.hephaestus.agent.mentor.chat;

import de.tum.in.www1.hephaestus.core.WorkspaceAgnostic;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Per-{@code (workspaceId, threadId)} mutex with reference-counted cleanup. Non-blocking
 * acquisition; failed {@code tryLock} returns {@link Optional#empty()} so the caller can
 * 409 the request. The DB unique partial index on {@code chat_message(thread_id) WHERE
 * status='in_flight'} is the durable backstop across replicas.
 */
@Component
@WorkspaceAgnostic("In-memory concurrency primitive — keys by tenant but is not a data accessor")
public class MentorTurnLock {

    private final ConcurrentHashMap<ThreadKey, Entry> locks = new ConcurrentHashMap<>();

    /**
     * Run {@code action} under the per-key lock if it is currently free. Returns the action's
     * result wrapped in {@link Optional} on success, or {@link Optional#empty()} when another
     * thread is mid-turn for the same key (caller should respond with 409 Conflict).
     *
     * <p>The supplier may itself return {@code null}; the empty/non-empty distinction therefore
     * tracks lock acquisition, NOT the supplier's return value. Use {@code Boolean} or a
     * sentinel value if the action's null/non-null distinction matters.
     */
    public <T> Optional<T> withLockOr409(ThreadKey key, Supplier<T> action) {
        Entry entry = acquire(key);
        if (entry == null) {
            // tryLock failed → another turn is in flight; do NOT count this as a holder.
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(action.get());
        } finally {
            release(key, entry);
        }
    }

    /**
     * Atomically increment the holder count and {@code tryLock}. {@code computeIfAbsent} gives us
     * an exclusive per-key block where we can both materialise the entry and bump the count;
     * the lock acquisition itself is non-blocking and happens after the entry is published.
     *
     * @return the entry whose {@link Entry#lock} is now held by the caller, or {@code null} if
     *         the lock was already held by someone else (we rolled the count back).
     */
    private Entry acquire(ThreadKey key) {
        Entry entry = locks.compute(key, (k, existing) -> {
            Entry e = existing != null ? existing : new Entry();
            e.holders.incrementAndGet();
            return e;
        });
        boolean acquired = entry.lock.tryLock();
        if (!acquired) {
            // Roll back the holder bump. We still go through compute() so a concurrent cleanup
            // pass observes the decrement before deciding whether to evict.
            decrementOrEvict(key, entry);
            return null;
        }
        return entry;
    }

    private void release(ThreadKey key, Entry entry) {
        entry.lock.unlock();
        decrementOrEvict(key, entry);
    }

    /**
     * Reference-counted teardown: when the last holder leaves, drop the entry. We MUST do this
     * under {@code computeIfPresent} so a concurrent {@link #acquire} cannot publish a hold on
     * the same key between our decrement and our remove.
     */
    private void decrementOrEvict(ThreadKey key, Entry entry) {
        locks.computeIfPresent(key, (k, current) -> {
            if (current != entry) {
                // Different entry was published in-between — leave it alone (its holders manage it).
                return current;
            }
            int after = current.holders.decrementAndGet();
            return after <= 0 ? null : current;
        });
    }

    /** Test/observability accessor. NOT for production code paths. */
    int activeKeys() {
        return locks.size();
    }

    /** Composite key: workspace + thread. Threads are workspace-scoped, but key both for cross-tenant safety. */
    public record ThreadKey(long workspaceId, UUID threadId) {}

    private static final class Entry {

        final ReentrantLock lock = new ReentrantLock();
        final AtomicInteger holders = new AtomicInteger();
    }
}
