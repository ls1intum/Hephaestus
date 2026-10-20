package de.tum.cit.aet.hephaestus.core.auth.audit;

import de.tum.cit.aet.hephaestus.core.auth.metrics.AuthMetrics;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Transactional sink for {@link AuthEvent} rows. Separate bean (not a method on
 * {@link AuthEventLogger}) so the selected transaction boundary is honoured. Event construction
 * stays the same for an independent audit and an audit that commits with its owning operation.
 */
@ConditionalOnServerRole
@Component
public class AuthEventWriter {

    private static final Logger log = LoggerFactory.getLogger(AuthEventWriter.class);

    private final AuthEventRepository repository;
    private final AuthEventSequence sequence;
    private final AuthMetrics metrics;
    private final Clock clock;

    public AuthEventWriter(
            AuthEventRepository repository, AuthEventSequence sequence, AuthMetrics metrics, Clock clock) {
        this.repository = repository;
        this.sequence = sequence;
        this.metrics = metrics;
        this.clock = clock;
    }

    /** @return whether persistence succeeded; the transaction proxy still has to commit before returning to the caller */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean write(AuthEventData data) {
        return persist(data);
    }

    /** Stages the event atomically with its owning operation; the caller owns the commit. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean writeInCurrentTransaction(AuthEventData data) {
        return persist(data);
    }

    private boolean persist(AuthEventData data) {
        try {
            AuthEvent event = AuthEvent.create(data, sequence.next(), clock.instant(), captureIp(), captureUserAgent());
            repository.save(event);
            return true;
        } catch (RuntimeException e) {
            metrics.recordAuditWriteFailed();
            log.warn("auth.audit: failed to persist {} event", data.type(), e);
            return false;
        }
    }

    @Nullable
    private static String captureIp() {
        HttpServletRequest req = currentRequest();
        return req == null ? null : req.getRemoteAddr();
    }

    @Nullable
    private static String captureUserAgent() {
        HttpServletRequest req = currentRequest();
        if (req == null) {
            return null;
        }
        String ua = req.getHeader("User-Agent");
        return (ua != null && ua.length() > 512) ? ua.substring(0, 512) : ua;
    }

    @Nullable
    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }
}
