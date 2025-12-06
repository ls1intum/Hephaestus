package de.tum.in.www1.hephaestus.workspace.context;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcRegistrations;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Registers a custom {@link RequestMappingHandlerMapping} that automatically prefixes
 * workspace-scoped controllers with {@code /workspaces/{workspaceSlug}}.
 */
@Configuration
public class WorkspaceScopedWebMvcConfiguration implements WebMvcRegistrations {

    @Override
    public RequestMappingHandlerMapping getRequestMappingHandlerMapping() {
        return new WorkspaceScopedHandlerMapping();
    }

    private static final class WorkspaceScopedHandlerMapping extends RequestMappingHandlerMapping {

        private static final String WORKSPACE_PREFIX = "/workspaces/{workspaceSlug}";

        @Override
        protected void registerHandlerMethod(Object handler, Method method, RequestMappingInfo mapping) {
            if (mapping != null && isWorkspaceScoped(method.getDeclaringClass())) {
                RequestMappingInfo workspacePrefix = RequestMappingInfo.paths(WORKSPACE_PREFIX)
                    .options(getBuilderConfiguration())
                    .build();

                mapping = normalizePaths(workspacePrefix.combine(mapping));
            }
            super.registerHandlerMethod(handler, method, mapping);
        }

        private boolean isWorkspaceScoped(Class<?> handlerType) {
            return AnnotatedElementUtils.hasAnnotation(handlerType, WorkspaceScopedController.class);
        }

        private RequestMappingInfo normalizePaths(RequestMappingInfo mapping) {
            if (mapping == null || mapping.getPathPatternsCondition() == null) {
                return mapping;
            }

            var originalPatterns = mapping.getPathPatternsCondition().getPatternValues();
            LinkedHashSet<String> normalized = new LinkedHashSet<>();
            for (String pattern : originalPatterns) {
                if (pattern == null || pattern.isBlank()) {
                    continue;
                }
                normalized.add(trimTrailingSlash(pattern));
            }

            if (normalized.size() == originalPatterns.size()) {
                return mapping;
            }

            return mapping.mutate().paths(normalized.toArray(String[]::new)).build();
        }

        private String trimTrailingSlash(String pattern) {
            if (pattern.length() > 1 && pattern.endsWith("/")) {
                return pattern.substring(0, pattern.length() - 1);
            }
            return pattern;
        }
    }
}
