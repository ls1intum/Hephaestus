package de.tum.cit.aet.hephaestus.agent.documentation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the floor for the documentation seam, used when no integration mirrors documentation in
 * this deployment.
 *
 * <p>On a {@code @Bean} factory method rather than a component-scanned {@code @Component} for the same
 * reason {@code SlackSeamDefaultsConfiguration} is: {@code @ConditionalOnMissingBean} is only evaluated
 * deterministically for {@code @Bean} methods. By the time these are parsed, every scanned
 * {@code DocumentProjection} definition is already registered, so a vendor that supplies one wins and
 * this never appears.
 */
@Configuration
class DocumentationSeamDefaultsConfiguration {

    @Bean
    @ConditionalOnMissingBean(DocumentProjection.class)
    DocumentProjection noDocumentationMirror() {
        return new NoDocumentationMirror();
    }
}
