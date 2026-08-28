package de.tum.cit.aet.hephaestus.core.auth.oauth;

import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEvent;
import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEventLogger;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/**
 * Failure handler for {@code oauth2Login}: audits the failed login ({@code LOGIN_FAILED} — a
 * security-relevant signal a bare redirect would drop) and sends the SPA to its error page. The
 * recorded reason is the exception TYPE only, never PII. Extracted from {@code AuthSecurityConfig}
 * (mirroring {@link HephaestusAuthSuccessHandler}) so the chain bean stays under the parameter limit.
 */
@ConditionalOnServerRole
@Component
public class HephaestusAuthFailureHandler implements AuthenticationFailureHandler {

    private final AuthEventLogger authEventLogger;

    /**
     * SPA origin (no trailing slash). Blank in production (SPA + API share an origin → a relative path
     * is correct); set to e.g. {@code http://localhost:4200} in local dev where they differ.
     */
    private final String appBaseUrl;

    public HephaestusAuthFailureHandler(
        AuthEventLogger authEventLogger,
        @Value("${hephaestus.webapp.url:}") String webappBaseUrl
    ) {
        this.authEventLogger = authEventLogger;
        this.appBaseUrl = stripTrailingSlash(webappBaseUrl);
    }

    @Override
    public void onAuthenticationFailure(
        HttpServletRequest request,
        HttpServletResponse response,
        AuthenticationException exception
    ) throws IOException {
        authEventLogger
            .event(AuthEvent.EventType.LOGIN_FAILED, AuthEvent.Result.FAILURE)
            .failureReason(exception.getClass().getSimpleName())
            .record();
        response.sendRedirect(appBaseUrl + "/auth/error?code=oauth_failure");
    }

    private static String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
