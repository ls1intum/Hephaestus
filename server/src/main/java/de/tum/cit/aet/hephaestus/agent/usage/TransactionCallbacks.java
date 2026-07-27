package de.tum.cit.aet.hephaestus.agent.usage;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Side effects that must not become visible unless the row they describe is durable. */
public final class TransactionCallbacks {

    private TransactionCallbacks() {}

    /**
     * Run {@code action} once the current transaction has actually committed, or immediately when
     * there is no transaction to wait for.
     *
     * <p>Not {@code @TransactionalEventListener(AFTER_COMMIT)}: that needs an event type for what is a
     * same-class continuation, and moves the callback out of the class holding the invariant it
     * protects. Not a lambda either — {@link TransactionSynchronization} has no abstract method, so it
     * is not a functional interface.
     *
     * <p>Callers that require a transaction say so themselves before calling; running immediately is
     * the right default only where there is genuinely nothing to wait for.
     */
    public static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            }
        );
    }
}
