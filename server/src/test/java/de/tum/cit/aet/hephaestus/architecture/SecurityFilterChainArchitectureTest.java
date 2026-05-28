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
            .haveFullyQualifiedName("de.tum.cit.aet.hephaestus.agent.proxy.LlmProxySecurityConfig");
        rule.check(classes);
    }
}
