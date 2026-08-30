package de.tum.cit.aet.hephaestus.core.auth.consent;

import de.tum.cit.aet.hephaestus.core.auth.web.CurrentAccount;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@ConditionalOnServerRole
class ConsentGateInterceptor implements HandlerInterceptor {

    private final ConsentService service;

    ConsentGateInterceptor(ConsentService service) {
        this.service = service;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Long accountId = CurrentAccount.idOrNull();
        if (accountId == null || isAllowedBeforeConsent(request.getMethod(), request.getRequestURI())) {
            return true;
        }
        if (!service.hasCompletedCurrentNotice(accountId)) {
            throw new ResponseStatusException(
                    HttpStatus.PRECONDITION_REQUIRED, "Complete the current transparency notice first");
        }
        return true;
    }

    static boolean isAllowedBeforeConsent(String method, String path) {
        return path.equals("/error")
                || (method.equals("GET") && (path.equals("/user") || path.equals("/user/consent")))
                || (method.equals("PUT") && path.equals("/user/consent"))
                || (method.equals("DELETE") && path.equals("/user"))
                || (method.equals("POST") && (path.equals("/auth/logout") || path.equals("/auth/refresh")));
    }
}
