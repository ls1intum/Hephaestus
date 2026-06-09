package de.tum.cit.aet.hephaestus.core.auth.metrics;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

/**
 * Bridges Spring Security's interactive-login events onto {@link AuthMetrics} login counters.
 *
 * <p>WHY a separate listener: the login-success decision lives inside Spring's oauth2Login filter,
 * not in any of our beans, and we must NOT count the resource-server's per-request
 * {@code AuthenticationSuccessEvent} (which fires on every cookie-JWT API call). Listening for
 * {@link InteractiveAuthenticationSuccessEvent} / {@link AbstractAuthenticationFailureEvent} — both
 * published only by the interactive {@code AbstractAuthenticationProcessingFilter}, never by the
 * bearer-token filter — yields exactly the login-only success/failure signal with zero extra wiring
 * on the existing 6-dependency auth beans.
 */
@Component
public class AuthLoginEventMetrics {

    private final AuthMetrics metrics;

    public AuthLoginEventMetrics(AuthMetrics metrics) {
        this.metrics = metrics;
    }

    @EventListener
    public void onSuccess(InteractiveAuthenticationSuccessEvent event) {
        metrics.recordLogin(AuthMetrics.LoginResult.SUCCESS);
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        metrics.recordLogin(AuthMetrics.LoginResult.FAILURE);
    }
}
