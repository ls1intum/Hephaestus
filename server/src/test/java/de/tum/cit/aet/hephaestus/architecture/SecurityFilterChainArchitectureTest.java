package de.tum.cit.aet.hephaestus.architecture;

import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.security.web.SecurityFilterChain;

class SecurityFilterChainArchitectureTest extends HephaestusArchitectureTest {

    @Test
    void onlySecurityConfigAndLlmProxyConfigDeclareFilterChains() {
        ArchRule rule = ArchRuleDefinition.methods()
            .that()
            .haveRawReturnType(SecurityFilterChain.class)
            .should()
            .beDeclaredInClassesThat()
            .haveFullyQualifiedName("de.tum.cit.aet.hephaestus.SecurityConfig")
            .orShould()
            .beDeclaredInClassesThat()
            .haveFullyQualifiedName("de.tum.cit.aet.hephaestus.agent.proxy.LlmProxySecurityConfig")
            // core.auth (ADR 0017) owns the oauth2Login filter chain — the user-facing login
            // flow is its own architectural concern, deliberately separate from the
            // resource-server chain in SecurityConfig.
            .orShould()
            .beDeclaredInClassesThat()
            .haveFullyQualifiedName("de.tum.cit.aet.hephaestus.core.auth.config.AuthSecurityConfig");
        rule.check(classes);
    }
}
