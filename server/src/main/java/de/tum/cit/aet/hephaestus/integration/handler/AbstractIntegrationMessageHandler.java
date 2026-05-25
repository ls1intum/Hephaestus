package de.tum.cit.aet.hephaestus.integration.handler;

import static de.tum.cit.aet.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.cit.aet.hephaestus.gitprovider.common.NatsMessageDeserializer;
import de.tum.cit.aet.hephaestus.gitprovider.common.exception.PayloadParsingException;
import de.tum.cit.aet.hephaestus.integration.spi.EventTypeKey;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import io.nats.client.Message;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Reusable scaffolding for {@link IntegrationMessageHandler} implementations.
 *
 * <p>Centralises the three concerns that every handler shared in the legacy per-kind
 * {@code GitHubMessageHandler<T>} / {@code GitLabMessageHandler<T>} bases:
 * <ol>
 *   <li><b>Subject-suffix validation.</b> The dispatcher already routes by
 *       {@link EventTypeKey} so a misrouted message is impossible in production, but
 *       direct callers (tests, replay tools) can still hand us a subject that doesn't
 *       match. We reject those rather than silently deserializing them.</li>
 *   <li><b>Jackson deserialization.</b> Done once here so every handler implements
 *       {@link #handleEvent(Object)} on the typed DTO rather than {@code byte[]}.</li>
 *   <li><b>Transaction boundary.</b> Spring AOP {@code @Transactional} does NOT work
 *       across self-invocation ({@code this.handleEvent(...)} bypasses the proxy), so
 *       we wrap the call in a {@link TransactionTemplate} the framework can see. This
 *       is the SAME reason the legacy bases needed a {@code TransactionTemplate} — we
 *       carry the constraint forward unchanged.</li>
 * </ol>
 *
 * <p>The class is intentionally {@code final}-ising {@link #onMessage(Message)} and
 * {@link #key()} so subclasses cannot subvert the routing contract: handlers express
 * their identity through the constructor arguments alone.
 *
 * <p><b>Subject-validation contract.</b> The {@link EventTypeKey}'s {@code eventType}
 * is the registry index — for GitHub it carries a tier prefix ({@code "repository.issues"})
 * that the {@code GithubSubjectParser} folds in from the {@code github.<org>.<repo>}
 * structure of the subject. The subject itself only carries the raw event token (e.g.
 * {@code "issues"}). The base class derives that raw token from {@link EventTypeKey#eventType()}
 * by stripping anything before the last {@code '.'}, then validates the inbound subject's
 * last segment against it via exact equality — this is the {@code tag_push} vs
 * {@code push} suffix-overlap guard that GitLab's legacy base required.
 *
 * @param <T> DTO type of the deserialized webhook payload.
 */
public abstract class AbstractIntegrationMessageHandler<T> implements IntegrationMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(AbstractIntegrationMessageHandler.class);

    private final EventTypeKey key;
    private final String eventType;
    /**
     * The raw last-segment of the subject this handler accepts (e.g. {@code "issues"}
     * for GitHub's {@code repository.issues} key, {@code "merge_request"} for GitLab).
     * This is what the producer actually puts on the wire — the per-kind
     * {@code SubjectParser} re-encodes it into the prefixed registry key, but the
     * subject itself never carries the tier prefix.
     */
    private final String subjectEventToken;
    private final Class<T> payloadType;
    private final NatsMessageDeserializer deserializer;
    private final TransactionTemplate transactionTemplate;

    /**
     * @param kind                the integration this handler belongs to; folded into
     *                            {@link #key()}.
     * @param eventType           the eventType portion of {@link EventTypeKey}. For
     *                            GitHub this is {@code "<tier>.<event>"} (e.g.
     *                            {@code "repository.issues"}); for GitLab it is the
     *                            flat event token (e.g. {@code "merge_request"}).
     * @param payloadType         {@code Class<T>} for Jackson deserialization.
     * @param deserializer        shared {@link NatsMessageDeserializer} bean.
     * @param transactionTemplate the framework's {@link TransactionTemplate} (the
     *                            handler runs DB writes; we need a real tx boundary).
     */
    protected AbstractIntegrationMessageHandler(
        IntegrationKind kind,
        String eventType,
        Class<T> payloadType,
        NatsMessageDeserializer deserializer,
        TransactionTemplate transactionTemplate
    ) {
        if (kind == null) {
            throw new IllegalArgumentException("kind must not be null");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must not be blank");
        }
        if (payloadType == null) {
            throw new IllegalArgumentException("payloadType must not be null");
        }
        this.key = new EventTypeKey(kind, eventType);
        this.eventType = eventType;
        // Derive the subject-side event token by stripping any tier prefix encoded in
        // the eventType. GitHub eventType is "<tier>.<event>"; the SubjectParser folds
        // the tier in but the raw subject only carries <event>. GitLab eventType has
        // no prefix so this is a passthrough.
        int lastDot = eventType.lastIndexOf('.');
        this.subjectEventToken = lastDot >= 0 ? eventType.substring(lastDot + 1) : eventType;
        if (this.subjectEventToken.isEmpty()) {
            throw new IllegalArgumentException(
                "eventType last segment must not be empty (eventType=" + eventType + ")"
            );
        }
        this.payloadType = payloadType;
        this.deserializer = deserializer;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public final EventTypeKey key() {
        return key;
    }

    @Override
    public final void onMessage(Message msg) {
        String subject = msg.getSubject();
        String safeSubject = sanitizeForLog(subject);
        if (!subjectMatchesExpectedEvent(subject)) {
            log.error(
                "Rejected message: reason=unexpectedSubject, subject={}, expectedEventType={}",
                safeSubject,
                eventType
            );
            return;
        }

        try {
            T eventPayload = deserializer.deserialize(msg, payloadType);
            // CRITICAL: Use TransactionTemplate to wrap handleEvent() in a transaction.
            // Spring AOP @Transactional does NOT work for self-invocation, so the
            // dispatcher's direct call into handleEvent() bypasses any proxy. Without
            // this template every @Modifying JPA query would fail with
            // TransactionRequiredException.
            transactionTemplate.executeWithoutResult(status -> handleEvent(eventPayload));
        } catch (IOException e) {
            log.error("Failed to parse payload: subject={}", safeSubject, e);
            throw new PayloadParsingException("Payload parsing failed for subject: " + safeSubject, e);
        }
        // Other exceptions intentionally propagate so the consumer dispatcher can
        // decide between ACK / NACK / dead-letter. We do not log them here to avoid
        // duplicate logging.
    }

    /**
     * Handles the deserialized payload. Called from inside the transaction boundary set
     * up by {@link #onMessage(Message)}; throwing here rolls back the transaction.
     */
    protected abstract void handleEvent(T eventPayload);

    /**
     * Subject-matching rule. Compares the trailing segment of the subject against the
     * raw {@link #subjectEventToken} via last-dot extraction — exactly matching the
     * GitLab legacy base's anti-{@code endsWith}-overlap guard. {@code "tag_push"}
     * cannot pass as {@code "push"} because the last-segment comparison requires the
     * full token to align.
     */
    private boolean subjectMatchesExpectedEvent(String subject) {
        if (subject == null) {
            return false;
        }
        int lastDot = subject.lastIndexOf('.');
        String lastSegment = lastDot >= 0 ? subject.substring(lastDot + 1) : subject;
        return subjectEventToken.equals(lastSegment);
    }

}
