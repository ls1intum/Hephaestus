package de.tum.in.www1.hephaestus.architecture;

import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import de.tum.in.www1.hephaestus.SecurityConfig;
import de.tum.in.www1.hephaestus.agent.proxy.LlmProxySecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.security.web.SecurityFilterChain;

class SecurityFilterChainArchitectureTest extends HephaestusArchitectureTest {

    @Test
    void onlySecurityConfigAndLlmProxyConfigDeclareFilterChains() {
        ArchRule rule = ArchRuleDefinition.methods()
            .that()
            .haveRawReturnType(SecurityFilterChain.class)
            .should()
            .beDeclaredIn(SecurityConfig.class)
            .orShould()
            .beDeclaredIn(LlmProxySecurityConfig.class);
        rule.check(classes);
    }
}
