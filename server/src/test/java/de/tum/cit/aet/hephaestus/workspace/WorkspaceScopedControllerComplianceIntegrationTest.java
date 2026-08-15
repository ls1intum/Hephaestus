package de.tum.cit.aet.hephaestus.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceScopedController;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.annotation.RequestMapping;

class WorkspaceScopedControllerComplianceIntegrationTest extends AbstractWorkspaceIntegrationTest {

    /**
     * Handlers whose answer does not depend on which workspace asked, so the rule has nothing to bind to.
     * They still sit under the workspace path and still authorize through it: WorkspaceContextFilter matches
     * the URL and @RequireAtLeastWorkspaceAdmin reads the resolved context from the holder, neither of which
     * involves the handler's signature. Named one at a time so an exception is a reviewable diff — a
     * controller that instead dropped @WorkspaceScopedController would take this rule's protection off
     * itself silently, and off every route that copied the trick.
     */
    private static final Set<String> WORKSPACE_INDEPENDENT_ANSWERS = Set.of(
        // The instance-wide "may a workspace connect its own AI provider" switch. There is no per-workspace
        // form of it to consult: WorkspaceLlmConnectionService gates on the instance value alone.
        "de.tum.cit.aet.hephaestus.agent.catalog.WorkspaceLlmSettingsController#get"
    );

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void workspaceScopedHandlersDeclareWorkspaceContextParameter() {
        Map<String, Object> controllers = applicationContext.getBeansWithAnnotation(WorkspaceScopedController.class);
        List<String> violations = new ArrayList<>();
        Set<String> unusedExemptions = new LinkedHashSet<>(WORKSPACE_INDEPENDENT_ANSWERS);

        controllers.values().forEach(bean -> inspectController(bean, violations, unusedExemptions));

        assertThat(violations)
            .describedAs("Workspace scoped handler methods must include a WorkspaceContext parameter")
            .isEmpty();
        assertThat(unusedExemptions)
            .describedAs("Stale exemption: the handler now takes a WorkspaceContext, or no longer exists")
            .isEmpty();
    }

    private void inspectController(Object bean, List<String> violations, Set<String> unusedExemptions) {
        Class<?> targetClass = AopUtils.getTargetClass(bean);
        for (Method method : ReflectionUtils.getUniqueDeclaredMethods(targetClass)) {
            if (!isRequestMappingMethod(method) || hasWorkspaceContextParameter(method)) {
                continue;
            }
            String handler = targetClass.getName() + "#" + method.getName();
            if (!unusedExemptions.remove(handler)) {
                violations.add(handler);
            }
        }
    }

    private boolean hasWorkspaceContextParameter(Method method) {
        return Arrays.stream(method.getParameterTypes()).anyMatch(WorkspaceContext.class::isAssignableFrom);
    }

    private boolean isRequestMappingMethod(Method method) {
        return AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class);
    }
}
