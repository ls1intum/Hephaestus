package de.tum.cit.aet.hephaestus.integration.slack.health;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default, credential-free {@link SlackAuthLivenessClient}: every probe returns {@link Liveness#UNKNOWN}, so the
 * scheduled liveness sweep enumerates connections and exercises the call site without ever suspending one. The real
 * {@code auth.test} round-trip is LIVE-only; a live client replaces this bean (it is {@code @ConditionalOnMissingBean}).
 */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true")
@ConditionalOnMissingBean(SlackAuthLivenessClient.class)
public class NoopSlackAuthLivenessClient implements SlackAuthLivenessClient {

    @Override
    public Liveness authTest(long workspaceId) {
        return Liveness.UNKNOWN;
    }
}
