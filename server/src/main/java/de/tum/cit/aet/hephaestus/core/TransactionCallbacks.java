package de.tum.cit.aet.hephaestus.core;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Side effects that must not become visible unless the row they describe is durable. */
public final class TransactionCallbacks {

    private TransactionCallbacks() {}

    /**
     * Run {@code action} once the current transaction has actually committed, or immediately when
     * there is no transaction to wait for. A caller for which "no transaction" is an error — one whose
     * side effect would otherwise react to a row that may never land — must check that itself first.
     *
     * <p>{@link TransactionSynchronization} has no abstract method, so this cannot be a lambda.
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
