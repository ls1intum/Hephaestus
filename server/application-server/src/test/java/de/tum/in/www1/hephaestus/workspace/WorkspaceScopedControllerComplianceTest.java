package de.tum.in.www1.hephaestus.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContext;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceScopedController;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.annotation.RequestMapping;

class WorkspaceScopedControllerComplianceTest extends AbstractWorkspaceIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void workspaceScopedHandlersDeclareWorkspaceContextParameter() {
        Map<String, Object> controllers = applicationContext.getBeansWithAnnotation(WorkspaceScopedController.class);
        List<String> violations = new ArrayList<>();

        controllers.values().forEach(bean -> inspectController(bean, violations));

        assertThat(violations)
            .describedAs("Workspace scoped handler methods must include a WorkspaceContext parameter")
            .isEmpty();
    }

    private void inspectController(Object bean, List<String> violations) {
        Class<?> targetClass = AopUtils.getTargetClass(bean);
        for (Method method : ReflectionUtils.getUniqueDeclaredMethods(targetClass)) {
            if (!isRequestMappingMethod(method)) {
                continue;
            }
            if (!hasWorkspaceContextParameter(method)) {
                violations.add(targetClass.getName() + "#" + method.getName());
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
