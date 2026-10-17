package de.tum.cit.aet.hephaestus.account.userview;

import de.tum.cit.aet.hephaestus.core.UserViewRead;
import de.tum.cit.aet.hephaestus.core.auth.spi.UserViewAccess;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.core.security.UserViewContextHolder;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Parameter;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.http.HttpStatus;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.method.AuthorizationInterceptorsOrder;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

// The step-up gate must run first so a refused confirmation cannot leave a USER_VIEW success.
@ConditionalOnServerRole
@Configuration(proxyBeanMethods = false)
public class UserViewAuthorizationConfig {

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    static Advisor userViewAuthorizationAdvisor(
            ObjectProvider<ViewedAccountService> viewedUsers, ObjectProvider<UserViewAccess> access) {
        AuthorizationManager<MethodInvocation> manager = (authentication, invocation) -> {
            HttpServletRequest request =
                    ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
            String reason = request.getHeader(UserViewContextHolder.REASON_HEADER);
            if (reason == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "The request does not include the " + UserViewContextHolder.REASON_HEADER + " header.");
            }
            long workspaceId = WorkspaceContextHolder.getContext().id();
            long accountId = viewedAccountId(invocation);
            var user = viewedUsers.getObject().requireAccount(workspaceId, accountId);
            String query = request.getQueryString();
            access.getObject()
                    .record(
                            workspaceId,
                            null,
                            Long.parseLong(user.accountId()),
                            reason,
                            query == null ? request.getRequestURI() : request.getRequestURI() + "?" + query);
            return new AuthorizationDecision(true);
        };
        AuthorizationManagerBeforeMethodInterceptor interceptor = new AuthorizationManagerBeforeMethodInterceptor(
                new AnnotationMatchingPointcut(null, UserViewRead.class, true), manager);
        interceptor.setOrder(AuthorizationInterceptorsOrder.PRE_AUTHORIZE.getOrder() + 2);
        return interceptor;
    }

    // Spring converts the accountId path variable before this advisor runs.
    private static long viewedAccountId(MethodInvocation invocation) {
        Parameter[] parameters = invocation.getMethod().getParameters();
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].getName().equals("accountId")
                    && invocation.getArguments()[i] instanceof String accountId) {
                return Long.parseLong(accountId);
            }
        }
        throw new IllegalStateException(invocation.getMethod() + " carries @UserViewRead but has no accountId");
    }
}
