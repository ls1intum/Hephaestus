package de.tum.cit.aet.hephaestus.agent.context;

import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import java.util.Map;

/**
 * Projects integration-owned source data into read-only workspace inputs. Providers may reshape source
 * data only when the transformation is lossless and practice-agnostic; practice-specific features belong
 * downstream. Data derivable from an already materialised repository tree is not a separate integration source.
 */
public interface ContentSource {
    String OUTPUT_PREFIX = SandboxLayout.CONTEXT_PREFIX;

    boolean supports(ContextRequest request);

    default boolean required() {
        return true;
    }

    /** Stable provenance identifier recorded for every emitted artifact. */
    String originId();

    /**
     * Whether {@code path} belongs to this provider's declared read-only namespace. Bulk sources may
     * override the default for a connector-owned {@code inputs/sources/...} prefix.
     */
    default boolean ownsPath(String path) {
        return path.startsWith(OUTPUT_PREFIX);
    }

    /** Materialise workspace-relative paths and bytes into {@code files}. */
    void contribute(ContextRequest request, Map<String, byte[]> files);
}
