package de.tum.cit.aet.hephaestus.agent.practice;

import de.tum.cit.aet.hephaestus.agent.runtime.AgentImageProperties;
import de.tum.cit.aet.hephaestus.agent.runtime.AgentResult;
import de.tum.cit.aet.hephaestus.agent.runtime.PiPlanSpec;
import de.tum.cit.aet.hephaestus.agent.runtime.PiResultParser;
import de.tum.cit.aet.hephaestus.agent.runtime.PiRuntimeFactory;
import de.tum.cit.aet.hephaestus.agent.runtime.WorkspaceAbi;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxResult;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Practice-review Pi adapter. Thin facade over {@link PiRuntimeFactory} + {@link PiResultParser}:
 * supplies the practice-specific precompute step and wraps the runtime plan in a
 * {@link PracticeSandboxSpec}.
 */
@Service
@RequiredArgsConstructor
public class PracticePiAdapter {

    private static final PracticeRunnerProfile PROFILE = new PracticeRunnerProfile();

    private final PiRuntimeFactory runtimeFactory;
    private final PiResultParser resultParser;
    private final AgentImageProperties imageProperties;

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
                PROFILE,
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
     * workspace ABI ({@link WorkspaceAbi#CONTEXT_PREFIX}).
     *
     * <p><b>Trust boundary:</b> the returned string is interpolated verbatim into the container's
     * {@code sh -c} command line. Do not derive any part of this output from untrusted input.
     */
    static String buildPrecomputeStep() {
        String root = WorkspaceAbi.WORKSPACE_ROOT;
        String contextTarget = root + "/" + WorkspaceAbi.CONTEXT_PREFIX;
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
            // diff.patch is the AGENT-facing view: every line carries a [L<n>] line-number annotation. The
            // precompute diff parser expects a RAW unified diff, so strip the annotation into a clean copy the
            // runner parses (the raw diff is the right input for static analysis; the annotation is the agent's).
            " && sed 's/^\\[L[0-9]*\\] //' " +
            contextTarget +
            "diff.patch > " +
            precomputeOut +
            "/diff_clean.patch 2>/dev/null ; bun run /opt/precompute/runner.ts" +
            " --repo " +
            WorkspaceAbi.REPO_MOUNT +
            " --diff " +
            precomputeOut +
            "/diff_clean.patch" +
            " --metadata " +
            contextTarget +
            "metadata.json" +
            // Give scripts the materialised context dir so they can read the SAME cross-artifact context the
            // agent sees (project_inventory.json, linked_work_items.json, …) and point the LLM at neighbours.
            " --context " +
            contextTarget +
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
