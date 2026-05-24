package de.tum.cit.aet.hephaestus.integration.consumer;

import de.tum.cit.aet.hephaestus.integration.handler.IntegrationMessageHandler;
import de.tum.cit.aet.hephaestus.integration.handler.IntegrationMessageHandlerRegistry;
import de.tum.cit.aet.hephaestus.integration.spi.EventTypeKey;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.SubjectParser;
import io.nats.client.Message;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Pure-function dispatcher: NATS subject → {@link IntegrationMessageHandler}.
 *
 * <p>Splits the routing problem into three independent steps:
 * <ol>
 *   <li>Map subject prefix → {@link IntegrationKind} via a hard-coded allow-list. This
 *       is the only point that touches the prefix string; we deliberately do NOT call
 *       {@link IntegrationKind#valueOf(String)} on subject input.</li>
 *   <li>Hand the full subject to that kind's registered {@link SubjectParser} to obtain
 *       an {@link EventTypeKey}.</li>
 *   <li>Look the key up in the {@link IntegrationMessageHandlerRegistry}.</li>
 * </ol>
 *
 * <p>Each step short-circuits to {@link Optional#empty()} on miss; the framework
 * decides what to do (ACK and ignore vs. dead-letter) at the consumer layer. The
 * dispatcher itself performs NO I/O beyond the eventual handler invocation, so it
 * remains a fast, unit-testable function suitable for tight loops and benchmarks.
 *
 * <p><b>Construction-time validation.</b> Two {@link SubjectParser} beans for the same
 * {@link IntegrationKind} are a fatal configuration error (we cannot pick a winner
 * deterministically). This matches the registry's duplicate-key policy.
 */
@Component
public class IntegrationMessageDispatcher {

    private static final Logger log = LoggerFactory.getLogger(IntegrationMessageDispatcher.class);

    /**
     * Hard-coded subject-prefix allow-list. Never derived from user input. New kinds must
     * be added here AND in {@link IntegrationKind} at the same time — failure to do so
     * causes silent NACKs at the dispatcher rather than misrouting.
     */
    private static final Map<String, IntegrationKind> PREFIX_TO_KIND = Map.of(
        "github",
        IntegrationKind.GITHUB,
        "gitlab",
        IntegrationKind.GITLAB,
        "slack",
        IntegrationKind.SLACK,
        "outline",
        IntegrationKind.OUTLINE
    );

    private final IntegrationMessageHandlerRegistry registry;
    private final Map<IntegrationKind, SubjectParser> parsersByKind;

    public IntegrationMessageDispatcher(IntegrationMessageHandlerRegistry registry, List<SubjectParser> parsers) {
        this.registry = registry;
        Map<IntegrationKind, SubjectParser> map = new EnumMap<>(IntegrationKind.class);
        for (SubjectParser parser : parsers) {
            IntegrationKind kind = parser.kind();
            if (kind == null) {
                throw new IllegalStateException(
                    parser.getClass().getName() + " returned null from kind() — every SubjectParser must declare a kind"
                );
            }
            SubjectParser previous = map.putIfAbsent(kind, parser);
            if (previous != null) {
                throw new IllegalStateException(
                    "Duplicate SubjectParser for kind " +
                        kind +
                        ": " +
                        previous.getClass().getName() +
                        " conflicts with " +
                        parser.getClass().getName()
                );
            }
        }
        this.parsersByKind = Map.copyOf(map);
        log.info(
            "IntegrationMessageDispatcher configured: {} subject parser(s), {} handler(s)",
            parsersByKind.size(),
            registry.handlerCount()
        );
    }

    /**
     * Look up the handler that owns the given subject. Returns empty if any link in the
     * chain (prefix → kind → key → handler) is missing.
     */
    public Optional<IntegrationMessageHandler> dispatch(String fullSubject) {
        Optional<IntegrationKind> kind = kindFromSubjectPrefix(fullSubject);
        if (kind.isEmpty()) {
            return Optional.empty();
        }
        SubjectParser parser = parsersByKind.get(kind.get());
        if (parser == null) {
            return Optional.empty();
        }
        EventTypeKey key;
        try {
            key = parser.parse(fullSubject);
        } catch (RuntimeException e) {
            // Malformed subject: the parser is the single source of truth for what
            // "well-formed" means per kind. We surface this to the consumer layer at
            // DEBUG since the consumer will already log/handle it via its own error
            // path; double-logging here just creates noise.
            log.debug("SubjectParser rejected subject '{}': {}", fullSubject, e.getMessage());
            return Optional.empty();
        }
        return registry.resolve(key);
    }

    /**
     * Convenience overload: look up the handler AND, if present, invoke it with the
     * NATS message. Unknown subjects are NOT errors — they are logged at DEBUG and the
     * caller (consumer) ACKs the message. This is the intended behaviour during the
     * transition window when many kinds/events have no handler yet.
     *
     * <p>Exceptions from {@link IntegrationMessageHandler#onMessage(Message)} propagate
     * unchanged so the consumer dispatcher can decide between ACK/NACK/dead-letter.
     */
    public void dispatch(String fullSubject, Message msg) {
        Optional<IntegrationMessageHandler> handler = dispatch(fullSubject);
        if (handler.isEmpty()) {
            log.debug("No handler for subject '{}' — ACKing as no-op", fullSubject);
            return;
        }
        handler.get().onMessage(msg);
    }

    /**
     * Explicit allow-list mapping of subject prefix → {@link IntegrationKind}. Returns
     * empty for null, blank, dot-less, or unknown prefixes. Never reflects on the
     * input.
     */
    static Optional<IntegrationKind> kindFromSubjectPrefix(@Nullable String fullSubject) {
        if (fullSubject == null || fullSubject.isBlank()) {
            return Optional.empty();
        }
        int firstDot = fullSubject.indexOf('.');
        if (firstDot <= 0) {
            return Optional.empty();
        }
        String prefix = fullSubject.substring(0, firstDot).toLowerCase(Locale.ROOT);
        return Optional.ofNullable(PREFIX_TO_KIND.get(prefix));
    }
}
