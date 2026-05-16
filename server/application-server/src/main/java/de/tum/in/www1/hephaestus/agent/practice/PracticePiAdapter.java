package de.tum.in.www1.hephaestus.agent.practice;

import de.tum.in.www1.hephaestus.agent.runtime.AgentImageProperties;
import de.tum.in.www1.hephaestus.agent.runtime.AgentResult;
import de.tum.in.www1.hephaestus.agent.runtime.PiAgentProperties;
import de.tum.in.www1.hephaestus.agent.runtime.PiPlanSpec;
import de.tum.in.www1.hephaestus.agent.runtime.PiResultParser;
import de.tum.in.www1.hephaestus.agent.runtime.PiRuntimeFactory;
import de.tum.in.www1.hephaestus.agent.runtime.WorkspaceAbi;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.SandboxResult;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Practice-review Pi adapter. Thin facade over {@link PiRuntimeFactory} + {@link PiResultParser}:
 * supplies the practice-specific precompute step and wraps the runtime plan in a
 * {@link PracticeSandboxSpec}.
 */
@Service
public class PracticePiAdapter {

    private final PiRuntimeFactory runtimeFactory;
    private final PiResultParser resultParser;
    private final PiAgentProperties properties;
    private final AgentImageProperties imageProperties;

    public PracticePiAdapter(
        PiRuntimeFactory runtimeFactory,
        PiResultParser resultParser,
        PiAgentProperties properties,
        AgentImageProperties imageProperties
    ) {
        this.runtimeFactory = runtimeFactory;
        this.resultParser = resultParser;
        this.properties = properties;
        this.imageProperties = imageProperties;
    }

    public PracticeSandboxSpec buildSandboxSpec(PracticeAgentRequest request) {
        PiRuntimeFactory.PiPlan plan = runtimeFactory.build(
            new PiPlanSpec(
                request.llmProvider(),
                request.credentialMode(),
                request.credential(),
                request.modelName(),
                request.baseUrl(),
                request.jobToken(),
                request.allowInternet(),
                request.timeoutSeconds(),
                properties.runnerScript(),
                Map.of(),
                buildPrecomputeStep()
            )
        );
        return new PracticeSandboxSpec(
            imageProperties.reference(),
            plan.command(),
            plan.environment(),
            plan.inputFiles(),
            WorkspaceAbi.OUTPUT_PATH,
            null,
            plan.networkPolicy(),
            null
        );
    }

    /** Parse the sandbox result via the shared {@link PiResultParser}. */
    public AgentResult parseResult(SandboxResult sandboxResult) {
        return resultParser.parse(sandboxResult);
    }

    /**
     * Run precompute scripts via Bun before the agent. Failure is non-fatal. Paths reference the
     * workspace ABI ({@link WorkspaceAbi#CONTEXT_TARGET_PREFIX}).
     *
     * <p><b>Trust boundary:</b> the returned string is interpolated verbatim into the container's
     * {@code sh -c} command line. Do not derive any part of this output from untrusted input.
     */
    static String buildPrecomputeStep() {
        String root = WorkspaceAbi.WORKSPACE_ROOT;
        String contextTarget = root + "/" + WorkspaceAbi.CONTEXT_TARGET_PREFIX;
        String precomputeIn = root + "/" + WorkspaceAbi.PRECOMPUTE_PREFIX + "practices";
        String precomputeOut = root + "/" + WorkspaceAbi.PRECOMPUTE_OUT_PREFIX.replaceFirst("/$", "");
        return (
            "(mkdir -p " +
            precomputeOut +
            "/practices" +
            " && cp " +
            precomputeIn +
            "/*.ts " +
            precomputeOut +
            "/practices/" +
            " && ln -sf /opt/precompute/lib " +
            precomputeOut +
            "/lib" +
            " && bun run /opt/precompute/runner.ts" +
            " --repo " +
            WorkspaceAbi.REPO_MOUNT +
            " --diff " +
            contextTarget +
            "diff.patch" +
            " --metadata " +
            contextTarget +
            "metadata.json" +
            " --output " +
            precomputeOut +
            " > /tmp/precompute-runner.log 2>&1" +
            " || { echo '[precompute] failed, continuing without hints'" +
            " && cp /tmp/precompute-runner.log " +
            precomputeOut +
            "/precompute-runner.log 2>/dev/null" +
            " ; tail -200 /tmp/precompute-runner.log 2>/dev/null" +
            " ; true; }) && "
        );
    }
}
