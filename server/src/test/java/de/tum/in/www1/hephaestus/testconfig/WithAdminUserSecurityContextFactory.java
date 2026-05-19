package de.tum.in.www1.hephaestus.testconfig;

import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

/**
 * Factory to create a mock security context with admin user authentication for tests.
 */
public class WithAdminUserSecurityContextFactory implements WithSecurityContextFactory<WithAdminUser> {

    @Override
    public SecurityContext createSecurityContext(WithAdminUser annotation) {
        return MockSecurityContextUtils.createSecurityContext(
            annotation.username(),
            annotation.userId(),
            annotation.authorities(),
            "mock-jwt-token-for-admin-user"
        );
    }
}
