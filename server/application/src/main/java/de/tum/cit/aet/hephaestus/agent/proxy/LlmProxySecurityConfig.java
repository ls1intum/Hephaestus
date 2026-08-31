package de.tum.cit.aet.hephaestus.agent.proxy;

import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.core.runtime.RuntimeRole;
import de.tum.cit.aet.hephaestus.core.runtime.hub.auth.WorkerJwtVerifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Separate security filter chain for the internal LLM proxy endpoints, ordered ahead of the main JWT
 * chain so these requests authenticate with proxy-scoped bearer credentials instead of the
 * application user-authentication chain.
 *
 * <p>Gated on the worker/sandbox capability rather than the practice-job feature flag, because
 * disabling practice reviews must not break mentor turns.
 */
@Configuration
@ConditionalOnProperty(name = RuntimeRole.WORKER_PROPERTY, havingValue = "true", matchIfMissing = true)
class LlmProxySecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain llmProxyFilterChain(
            HttpSecurity http,
            AgentJobRepository agentJobRepository,
            WorkerJwtVerifier jwtVerifier,
            MentorProxyCredentialRegistry mentorRegistry,
            ObjectMapper objectMapper)
            throws Exception {
        http.securityMatcher("/internal/llm/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .addFilterBefore(
                        new JobTokenAuthenticationFilter(agentJobRepository, jwtVerifier, mentorRegistry, objectMapper),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
