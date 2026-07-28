package de.tum.cit.aet.hephaestus.integration.core.spi;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Provides NATS subscription information for scopes.
 *
 * <p>A single scope (workspace) may subscribe to more than one JetStream stream — an SCM stream
 * ({@code github}/{@code gitlab}) for repository/organization events and, independently, the
 * {@code outline} stream for documentation change notifications. Each {@link StreamSubscription}
 * carries the stream name and the wildcard subject filters for that stream; the consumer fleet
 * creates one durable consumer per (scope, stream).
 */
public interface NatsSubscriptionProvider {
    /** Get subscription info for a scope. */
    Optional<NatsSubscriptionInfo> getSubscriptionInfo(Long scopeId);

    record NatsSubscriptionInfo(Long scopeId, List<StreamSubscription> streamSubscriptions) {
        public boolean hasSubscriptions() {
            return streamSubscriptions != null && !streamSubscriptions.isEmpty();
        }
    }

    /** One NATS stream a scope subscribes to, with the wildcard subject filters on that stream. */
    record StreamSubscription(String streamName, Set<String> subjects) {}
}
