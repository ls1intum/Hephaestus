package de.tum.cit.aet.hephaestus.core;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The application {@link Clock}.
 *
 * <p>Deliberately <b>not</b> {@code @ConditionalOnServerRole}, and deliberately not owned by
 * {@code core.auth}. Telling the time is not an auth concern, and every runtime role needs it: the
 * bean previously lived on the server-gated {@code AuthJwtConfig}, so the first ungated consumer to
 * inject a {@code Clock} would fail context refresh on the worker and webhook pods — a crash loop no
 * test tier could see, because none boots with {@code hephaestus.runtime.server.enabled=false}.
 * {@code RuntimeRoleBoundaryTest#clockBeanIsAvailableToEveryRuntimeRole} pins it here.
 *
 * <p>It is a bean rather than a static so that time-dependent behaviour can be tested against a
 * fixed instant.
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
