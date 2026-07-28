package de.tum.cit.aet.hephaestus.agent.mentor.chat;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.lang.ArchRule;
import de.tum.cit.aet.hephaestus.architecture.HephaestusArchitectureTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards the {@link MentorChannel} transport seam.
 *
 * <p>The mentor orchestrator drives a turn through {@link MentorChannel}; only the web adapter
 * package ({@code agent.mentor.chat}) may touch Spring's {@code SseEmitter}. The independent sync
 * status web adapter ({@code integration.core.sync.push}) is also an HTTP transport boundary. A future Slack DM
 * adapter (in {@code integration.slack.mentor}) must render through the port, never reach for the
 * HTTP-specific emitter — this rule fails the build if it tries.
 */
class MentorChannelBoundaryTest extends HephaestusArchitectureTest {

    private static final String WEB_ADAPTER = "..agent.mentor.chat..";
    private static final String SYNC_PUSH_WEB_ADAPTER = "..integration.core.sync.push..";
    private static final String SSE_EMITTER = "org.springframework.web.servlet.mvc.method.annotation.SseEmitter";

    @Test
    @DisplayName("SseEmitter is confined to the web mentor adapter package")
    void sseEmitterConfinedToWebAdapter() {
        ArchRule rule = noClasses()
            .that()
            .resideOutsideOfPackages(WEB_ADAPTER, SYNC_PUSH_WEB_ADAPTER)
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName(SSE_EMITTER)
            .because(
                "the mentor orchestrator drives turns through MentorChannel; SSE transport details " +
                    "must not leak past the web adapter (a Slack adapter would implement the same port)"
            );
        rule.check(classes);
    }
}
