package de.tum.in.www1.hephaestus.workspace.context;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import org.springframework.core.annotation.AliasFor;
import org.springframework.web.bind.annotation.RestController;

/**
 * Meta-annotation that marks a controller as workspace-scoped.
 *
 * <p>Controllers annotated with {@code @WorkspaceScopedController} automatically receive the
 * {@code /workspaces/{workspaceSlug}} prefix via {@link WorkspaceScopedWebMvcConfiguration} so each
 * route is tenant-aware without repeating the slug in every {@code @RequestMapping} declaration.</p>
 */
@Target(TYPE)
@Retention(RUNTIME)
@Documented
@RestController
public @interface WorkspaceScopedController {
    /**
     * Optional component name alias inherited from {@link RestController}.
     */
    @AliasFor(annotation = RestController.class, attribute = "value")
    String value() default "";
}
