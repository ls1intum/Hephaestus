package de.tum.in.www1.hephaestus.agent.proxy;

import de.tum.in.www1.hephaestus.agent.job.AgentJobRepository;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Separate security filter chain for the internal LLM proxy endpoints.
 *
 * <p>This chain uses {@code @Order(1)} and is evaluated before the main Keycloak/JWT chain
 * (which has no explicit {@code @Order} and defaults to lowest precedence).
 * Authenticates requests using job tokens instead of JWTs.
 * The main chain for all other endpoints remains unchanged.
 */
@Configuration
@EnableConfigurationProperties(LlmProxyProperties.class)
class LlmProxySecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain llmProxyFilterChain(HttpSecurity http, AgentJobRepository agentJobRepository) throws Exception {
        http
            .securityMatcher("/internal/llm/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .addFilterBefore(
                new JobTokenAuthenticationFilter(agentJobRepository),
                UsernamePasswordAuthenticationFilter.class
            );

        return http.build();
    }
}
