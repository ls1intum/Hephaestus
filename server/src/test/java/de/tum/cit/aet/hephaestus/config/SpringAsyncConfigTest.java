package de.tum.cit.aet.hephaestus.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.Ordered;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;

@Tag("unit")
class SpringAsyncConfigTest {

    @Test
    void asyncMethodsRunOnTheBoundedApplicationPool() {
        // setActiveProfiles("default") clears surefire's "test" profile so @Profile("!test") applies.
        new ApplicationContextRunner()
            .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("default"))
            .withUserConfiguration(SpringAsyncConfig.class, AsyncProbe.class)
            .run(ctx -> {
                String thread = ctx.getBean(AsyncProbe.class).currentThread().get(5, TimeUnit.SECONDS);
                // async-* = the bounded pool, not the SimpleAsyncTaskExecutor fallback.
                assertThat(thread).startsWith("async-");
            });
    }

    @Test
    void asyncAdviceOrderedBetweenTenancyAspectAndTransaction() {
        // Inside WorkspaceAgnosticAspect (HIGHEST) and outside the transaction advisor (LOWEST). The
        // tie is nondeterministic, so this can't be asserted behaviorally — check the invariant.
        int order = SpringAsyncConfig.class.getAnnotation(EnableAsync.class).order();
        assertThat(order).isGreaterThan(Ordered.HIGHEST_PRECEDENCE).isLessThan(Ordered.LOWEST_PRECEDENCE);
    }

    static class AsyncProbe {

        @Async
        public CompletableFuture<String> currentThread() {
            return CompletableFuture.completedFuture(Thread.currentThread().getName());
        }
    }
}
