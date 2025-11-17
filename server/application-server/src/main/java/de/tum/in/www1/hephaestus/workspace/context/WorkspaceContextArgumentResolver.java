package de.tum.in.www1.hephaestus.workspace.context;

import org.springframework.core.MethodParameter;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Spring MVC argument resolver that injects WorkspaceContext into controller method parameters.
 * Retrieves context from ThreadLocal populated by WorkspaceContextFilter.
 */
public class WorkspaceContextArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(WorkspaceContext.class);
    }

    @Override
    public Object resolveArgument(
        @NonNull MethodParameter parameter,
        ModelAndViewContainer mavContainer,
        @NonNull NativeWebRequest webRequest,
        WebDataBinderFactory binderFactory
    ) {
        return WorkspaceContextHolder.getContext();
    }
}
