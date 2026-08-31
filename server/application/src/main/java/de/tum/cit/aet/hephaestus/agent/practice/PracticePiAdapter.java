package de.tum.cit.aet.hephaestus.agent.practice;

import de.tum.cit.aet.hephaestus.agent.runtime.AgentImageProperties;
import de.tum.cit.aet.hephaestus.agent.runtime.AgentResult;
import de.tum.cit.aet.hephaestus.agent.runtime.PiPlanSpec;
import de.tum.cit.aet.hephaestus.agent.runtime.PiResultParser;
import de.tum.cit.aet.hephaestus.agent.runtime.PiRuntimeFactory;
import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
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
        PiRuntimeFactory.PiPlan plan = runtimeFactory.build(new PiPlanSpec(
                request.apiProtocol(),
                request.upstreamModelId(),
                request.contextWindow(),
                request.maxOutputTokens(),
                request.supportsReasoning(),
                request.jobToken(),
                request.allowInternet(),
                request.timeoutSeconds(),
                PROFILE,
                Map.of(),
                buildPrecomputeStep()));
        return new PracticeSandboxSpec(
                imageProperties.reference(),
                plan.command(),
                plan.environment(),
                plan.inputFiles(),
                SandboxLayout.OUTPUT_PATH,
                null,
                plan.networkPolicy(),
                Map.of(),
                plan.promptDigest());
    }

    /** Parse the sandbox result via the shared {@link PiResultParser}. */
    public AgentResult parseResult(SandboxResult sandboxResult) {
        return resultParser.parse(sandboxResult);
    }

    /** Build the fixed, best-effort precompute shell fragment. */
    static String buildPrecomputeStep() {
        String root = SandboxLayout.WORKSPACE_ROOT;
        String contextTarget = root + "/" + SandboxLayout.CONTEXT_PREFIX;
        String precomputeIn = root + "/" + SandboxLayout.PRECOMPUTE_PREFIX + "practices";
        String precomputeStage = root + "/work/precompute-stage";
        String precomputeOut = root + "/" + SandboxLayout.PRECOMPUTE_OUT_PREFIX.replaceFirst("/$", "");
        return ("(rm -rf " + precomputeStage
                + " && mkdir -p "
                + precomputeStage
                + "/practices "
                + precomputeOut
                + " && find "
                + precomputeIn
                + " -maxdepth 1 -type f -name '*.ts' -exec cp {} "
                + precomputeStage
                + "/practices/ \\;"
                + " && ln -sf /opt/precompute/lib "
                + precomputeStage
                + "/lib"
                +
                // Precompute consumes a raw diff; the staged agent view prefixes lines with [L<n>].
                " && sed 's/^\\[L[0-9]*\\] //' "
                + contextTarget
                + "diff.patch > "
                + precomputeOut
                +
                // env -i resolves node through the PATH it sets, so it must include /usr/local/bin,
                // where the node:24-slim base installs the binary.
                "/diff_clean.patch 2>/dev/null ; env -i HOME=/home/agent PATH=/usr/local/bin:/usr/bin:/bin TMPDIR=/tmp node"
                + " --permission"
                + " --allow-fs-read=/workspace"
                + " --allow-fs-read=/opt/precompute"
                + " '--allow-fs-write=/workspace/work/precompute-out*'"
                + " --allow-child-process /opt/precompute/runner.ts"
                + " --repo "
                + SandboxLayout.REPO_MOUNT
                + " --diff "
                + precomputeOut
                + "/diff_clean.patch"
                + " --metadata "
                + contextTarget
                + "metadata.json"
                + " --context "
                + contextTarget
                + " --practices "
                + precomputeStage
                + "/practices"
                + " --output "
                + precomputeOut
                + " > /tmp/precompute-runner.log 2>&1"
                + " || { echo '[precompute] failed, continuing without hints'"
                + " && cp /tmp/precompute-runner.log "
                + precomputeOut
                + "/precompute-runner.log 2>/dev/null"
                + " ; tail -200 /tmp/precompute-runner.log 2>/dev/null"
                + " ; true; }) && ");
    }
}
