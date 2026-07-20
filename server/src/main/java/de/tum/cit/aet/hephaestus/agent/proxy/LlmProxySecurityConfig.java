package de.tum.cit.aet.hephaestus.agent.proxy;

import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.core.runtime.RuntimeRole;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Separate security filter chain for the internal LLM proxy endpoints.
 *
 * <p>This chain uses {@code @Order(1)} and is evaluated before the main JWT chain
 * (which has no explicit {@code @Order} and defaults to lowest precedence).
 * Authenticates requests using proxy-scoped bearer tokens instead of JWTs.
 * The main chain for all other endpoints remains unchanged.
 *
 * <p><b>Gating (#1368 slice 5):</b> the LLM proxy is the ONLY LLM credential path, so it must be
 * present on every job-executing host. Gated on the SAME expression {@code AgentJobExecutor} wires
 * on — job-execution capability, not a standalone flag — so "jobs on, proxy off" cannot be configured.
 */
@Configuration
@ConditionalOnExpression(
    "${" + RuntimeRole.AGENT_NATS_ENABLED_PROPERTY + ":false} and ${" + RuntimeRole.WORKER_PROPERTY + ":true}"
)
class LlmProxySecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain llmProxyFilterChain(
        HttpSecurity http,
        AgentJobRepository agentJobRepository,
        MentorProxyCredentialRegistry mentorRegistry,
        ObjectMapper objectMapper
    ) throws Exception {
        http
            .securityMatcher("/internal/llm/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .addFilterBefore(
                new JobTokenAuthenticationFilter(agentJobRepository, mentorRegistry, objectMapper),
                UsernamePasswordAuthenticationFilter.class
            );

        return http.build();
    }
}
