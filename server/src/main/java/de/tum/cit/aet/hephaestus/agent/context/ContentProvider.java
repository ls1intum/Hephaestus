package de.tum.cit.aet.hephaestus.agent.context;

import de.tum.cit.aet.hephaestus.agent.runtime.WorkspaceAbi;
import java.util.Map;

/**
 * SPI for an integration's content into the agent workspace. A ContentProvider is an
 * <strong>EXTRACT+LOAD connector and nothing else</strong> — it materialises the integration's RAW NATIVE
 * OBJECTS under {@link #OUTPUT_PREFIX} and stops there.
 *
 * <p><strong>The admission test is PROVENANCE, not usefulness.</strong> A byte qualifies as integration
 * content iff it (a) lives in SQL / the integration's API object graph (for SCM: the PR/issue object, its
 * comments, the computed two-ref diff, review-decision + thread rows, resolved linked-issue rows, developer
 * history) AND (b) is absent from the read-only worktree the sandbox already mounts at
 * {@code inputs/sources/scm/repo}. If a grep / JGit walk over that mount, or a parse of {@code diff.patch},
 * reproduces it, it is NOT integration content — it is downstream Transform.
 *
 * <p><strong>A provider MUST NOT</strong> compute a practice-shaped feature, emit an observation/note sentence,
 * carry a tuned threshold, or name a practice anywhere (field, file, javadoc). Such derivation is
 * practice-dependent TRANSFORM and belongs downstream — in the per-practice precompute script or the agent
 * (which has the mounted worktree). The single permitted in-connector transform is a practice-AGNOSTIC,
 * lossless structural reshape reused identically by every practice — the staging band: {@code diff_summary.md}
 * is a lossless re-chunking of {@code diff.patch} (passes the rule); a {@code test_presence.json} feature
 * file scanned from the worktree does not (it is Transform — deleted, ELT boundary). {@link #connectorId()}
 * is the enforcement seam: a provider stamped {@code "scm"} that emits a practice observation is, by definition,
 * code in the wrong layer. The SPI generalises verbatim to Slack/Outline/Jira — each LOADS its native
 * message/doc/ticket object, none pre-judges it.
 *
 * <p>Provider order at {@link WorkspaceContextBuilder} is governed by Spring's {@code @Order}; a
 * {@link #required()} provider whose {@link #contribute} throws aborts the build, optional providers degrade
 * quietly. Implementations must reside under {@code agent.context.providers.*} — enforced by
 * {@code AgentRuntimeBoundaryTest}.
 */
public interface ContentProvider {
    /** Workspace-relative prefix every provider must write under (see {@link WorkspaceAbi#CONTEXT_PREFIX}). */
    String OUTPUT_PREFIX = WorkspaceAbi.CONTEXT_PREFIX;

    /** @return {@code true} iff this provider can produce content for the given request variant. */
    boolean supports(ContextRequest request);

    /** @return {@code true} (default) for fatal-on-failure providers; {@code false} for best-effort. */
    default boolean required() {
        return true;
    }

    /**
     * The integration this provider projects from (ADR 0020) — recorded per file in the context manifest
     * so the agent sees uniform provenance regardless of which connector produced the bytes. No default:
     * every provider MUST state its own provenance ({@code "scm"} for SCM-derived context, {@code "core"}
     * for Hephaestus-native data such as the mentor aspects, a future Slack/Outline connector its own id).
     * Abstract on purpose — a silent default would mislabel a non-SCM provider's files as {@code "scm"} in
     * the telescope, corrupting the one thing the manifest exists to provide; the compiler now forbids that.
     */
    String connectorId();

    /**
     * Materialise this provider's files into {@code files}. Keys must begin with
     * {@link #OUTPUT_PREFIX}. The builder validates this after each call.
     */
    void contribute(ContextRequest request, Map<String, byte[]> files);
}
