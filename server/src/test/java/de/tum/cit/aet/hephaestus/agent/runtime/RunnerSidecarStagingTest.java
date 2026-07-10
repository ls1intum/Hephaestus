package de.tum.cit.aet.hephaestus.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.mentor.MentorRunnerProfile;
import de.tum.cit.aet.hephaestus.agent.practice.PracticeRunnerProfile;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Guards the class of bug where a Pi runner script imports a sibling helper ({@code ./x.mjs}) that the
 * factory never stages into the sandbox — a runtime {@code ERR_MODULE_NOT_FOUND} that crashes the agent
 * container and that no other test caught (it happened live for {@code pi-observation-normalize.mjs}).
 *
 * <p>For every {@link PiRunnerProfile}, every relative {@code .mjs} import in its script MUST be declared
 * in {@link PiRunnerProfile#sidecarScripts()} and resolve on the classpath under {@code agent/}.
 */
@Tag("unit")
class RunnerSidecarStagingTest {

    /** Matches {@code from "./name.mjs"} / {@code from './name.mjs'} relative ESM imports. */
    private static final Pattern RELATIVE_MJS_IMPORT = Pattern.compile("from\\s+[\"']\\./([\\w.-]+\\.mjs)[\"']");

    private static final List<PiRunnerProfile> PROFILES = List.of(
        new PracticeRunnerProfile(),
        new MentorRunnerProfile()
    );

    @Test
    void everyRelativeImportInARunnerIsDeclaredAsSidecar() {
        for (PiRunnerProfile profile : PROFILES) {
            String script = new String(
                PiRuntimeFactory.loadClasspathResource(profile.runnerScript()),
                StandardCharsets.UTF_8
            );
            Matcher matcher = RELATIVE_MJS_IMPORT.matcher(script);
            while (matcher.find()) {
                String imported = matcher.group(1);
                assertThat(profile.sidecarScripts())
                    .as(
                        "%s imports ./%s at workspace-root — it MUST be in sidecarScripts()",
                        profile.runnerScript(),
                        imported
                    )
                    .contains(imported);
            }
        }
    }

    @Test
    void everyDeclaredSidecarResolvesOnTheClasspath() {
        for (PiRunnerProfile profile : PROFILES) {
            for (String sidecar : profile.sidecarScripts()) {
                assertThat(PiRuntimeFactory.loadClasspathResource(sidecar))
                    .as("declared sidecar %s must resolve under agent/", sidecar)
                    .isNotEmpty();
            }
        }
    }
}
