package de.tum.cit.aet.hephaestus;

import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContextArgumentResolver;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(@NonNull List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new WorkspaceContextArgumentResolver());
    }
}
