package de.tum.cit.aet.hephaestus.agent.context;

import de.tum.cit.aet.hephaestus.agent.runtime.WorkspaceAbi;
import java.util.Map;

/**
 * SPI for contributors to the workspace context. Each implementation narrows on a
 * {@link ContextRequest} variant via {@link #supports} and materialises its files under
 * {@link #OUTPUT_PREFIX} into the supplied map.
 *
 * <p>Provider order at {@link WorkspaceContextBuilder} is governed by Spring's
 * {@code @Order} (or {@code Ordered.getOrder()}); a {@link #required()} provider whose
 * {@link #contribute} throws aborts the build, optional providers degrade quietly.
 *
 * <p>Implementations must reside under {@code agent.context.providers.*} — enforced by
 * {@code AgentRuntimeBoundaryTest}.
 */
public interface ContentProvider {
    /** Workspace-relative prefix every provider must write under (see {@link WorkspaceAbi#CONTEXT_TARGET_PREFIX}). */
    String OUTPUT_PREFIX = WorkspaceAbi.CONTEXT_TARGET_PREFIX;

    /** @return {@code true} iff this provider can produce content for the given request variant. */
    boolean supports(ContextRequest request);

    /** @return {@code true} (default) for fatal-on-failure providers; {@code false} for best-effort. */
    default boolean required() {
        return true;
    }

    /**
     * Materialise this provider's files into {@code files}. Keys must begin with
     * {@link #OUTPUT_PREFIX}. The builder validates this after each call.
     */
    void contribute(ContextRequest request, Map<String, byte[]> files);
}
