package de.tum.cit.aet.hephaestus.integration.slack.health;

/**
 * Default, credential-free {@link SlackAuthLivenessClient}: every probe returns {@link Liveness#UNKNOWN}, so the
 * scheduled liveness sweep enumerates connections and exercises the call site without ever suspending one. The real
 * {@code auth.test} round-trip is LIVE-only; a live client replaces this bean by supplying its own
 * {@link SlackAuthLivenessClient} (the default is registered {@code @ConditionalOnMissingBean} in
 * {@link SlackSeamDefaultsConfiguration}). It is NOT a component-scanned bean: {@code @ConditionalOnMissingBean}
 * is only reliable on a {@code @Bean} factory method, not on a scanned {@code @Component}.
 */
public class NoopSlackAuthLivenessClient implements SlackAuthLivenessClient {

    @Override
    public Liveness authTest(long workspaceId) {
        return Liveness.UNKNOWN;
    }
}
