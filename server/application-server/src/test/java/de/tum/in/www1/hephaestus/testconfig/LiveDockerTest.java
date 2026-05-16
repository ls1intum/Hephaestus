package de.tum.in.www1.hephaestus.testconfig;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/**
 * Live test that requires a working Docker daemon. There is no environment-variable gate —
 * Docker presence is probed at runtime in {@code @BeforeAll} via Testcontainers'
 * {@code DockerClientFactory.isDockerAvailable()}. Combine with {@link LiveLlmTest} when the
 * sandbox payload also needs LLM credentials (the LLM gate then short-circuits as well).
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Tag("live")
@Execution(ExecutionMode.SAME_THREAD)
public @interface LiveDockerTest {}
