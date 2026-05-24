package de.tum.cit.aet.hephaestus.integration.handler;

import de.tum.cit.aet.hephaestus.integration.spi.EventTypeKey;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Cross-vendor message handler registry, indexed by {@link EventTypeKey}.
 *
 * <p>This is the unified successor to the parallel per-provider registries:
 * <ul>
 *   <li>{@code gitprovider/common/github/GitHubMessageHandlerRegistry} — 3 domain maps
 *       (repository / organization / installation), keyed by event name.</li>
 *   <li>{@code gitprovider/common/gitlab/GitLabMessageHandlerRegistry} — flat map.</li>
 * </ul>
 *
 * <p><b>Transition window.</b> Per plan v4 D7+D22, both legacy registries continue to
 * operate while real handlers stay under {@code gitprovider/...}. This unified registry
 * starts EMPTY in #1198 and fills as handlers migrate to {@code integration/<kind>/...}.
 * The migration is per-handler and reversible: each handler that adopts
 * {@link IntegrationMessageHandler} is automatically picked up here via Spring DI and can
 * be deregistered from its legacy registry in the same change.
 *
 * <p><b>Registration model.</b> Constructor-injects every
 * {@link IntegrationMessageHandler} bean and builds an immutable map keyed by
 * {@link IntegrationMessageHandler#key()}. Duplicate keys are a fatal
 * {@link IllegalStateException} naming BOTH offending bean classes — handler routing must
 * be unambiguous, so we fail at boot rather than silently shadowing one handler with
 * another.
 *
 * <p><b>GitHub domain tiers.</b> The legacy GitHub registry split the keyspace across
 * three domain maps; the unified key folds that into the eventType prefix
 * ({@code repository.<event>}, {@code organization.<event>}, {@code installation.<event>})
 * exactly as {@code GithubSubjectParser} emits it. No information is lost; the indirection
 * becomes a string-prefix lookup.
 */
@Component
public class IntegrationMessageHandlerRegistry {

    private static final Logger log = LoggerFactory.getLogger(IntegrationMessageHandlerRegistry.class);

    private final Map<EventTypeKey, IntegrationMessageHandler> handlers;

    public IntegrationMessageHandlerRegistry(List<IntegrationMessageHandler> all) {
        Map<EventTypeKey, IntegrationMessageHandler> map = new HashMap<>(all.size() * 2);
        for (IntegrationMessageHandler handler : all) {
            EventTypeKey key = handler.key();
            if (key == null) {
                throw new IllegalStateException(
                    handler.getClass().getName() + " returned null from key() — every handler must declare a key"
                );
            }
            IntegrationMessageHandler previous = map.putIfAbsent(key, handler);
            if (previous != null) {
                throw new IllegalStateException(
                    "Duplicate IntegrationMessageHandler for key " +
                        key +
                        ": " +
                        previous.getClass().getName() +
                        " conflicts with " +
                        handler.getClass().getName()
                );
            }
        }
        this.handlers = Collections.unmodifiableMap(map);
        log.info("Registered {} IntegrationMessageHandler bean(s) in unified registry", handlers.size());
    }

    /**
     * Resolve the handler bound to the given key.
     */
    public Optional<IntegrationMessageHandler> resolve(EventTypeKey key) {
        if (key == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(handlers.get(key));
    }

    /**
     * Convenience shim for callers that already have the (kind, eventType) pair split out.
     * Returns empty when either argument is null/blank rather than constructing an
     * obviously-invalid key.
     */
    public Optional<IntegrationMessageHandler> resolve(IntegrationKind kind, String eventType) {
        if (kind == null || eventType == null || eventType.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(handlers.get(new EventTypeKey(kind, eventType)));
    }

    /**
     * @return the number of registered handlers; useful for metrics and startup
     *     diagnostics. Stable for the lifetime of the bean.
     */
    public int handlerCount() {
        return handlers.size();
    }
}
