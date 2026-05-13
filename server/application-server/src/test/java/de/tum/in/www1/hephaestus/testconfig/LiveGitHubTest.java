package de.tum.in.www1.hephaestus.testconfig;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/**
 * Live test that hits the real GitHub API. {@code GH_APP_INSTALLATION_ID} acts as the gate
 * (presence implies the App is wired up); {@code BaseGitHubLiveIntegrationTest} additionally
 * runs a runtime check on {@code github.app.id} + private key material so PEM parse failures
 * surface as a skip rather than a hard test error.
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Tag("live")
@EnabledIfEnvironmentVariable(named = "GH_APP_INSTALLATION_ID", matches = ".+")
@Execution(ExecutionMode.SAME_THREAD)
public @interface LiveGitHubTest {}
