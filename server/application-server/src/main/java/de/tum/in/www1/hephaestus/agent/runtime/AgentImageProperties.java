package de.tum.in.www1.hephaestus.agent.runtime;

import de.tum.in.www1.hephaestus.agent.sandbox.ImagePullPolicy;
import jakarta.validation.constraints.NotBlank;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Shared image reference for the agent runtime. Pi practice and Pi mentor share the same image
 * by design (unified Pi runtime epic #1066); keeping a single property prevents drift between
 * defaults. Production sets {@code require-digest=true} so the application fails fast on tag
 * references — see {@code docs/admin/agent-image-digests.md}.
 *
 * @param reference fully-qualified image reference (registry/path:tag or
 *     registry/path@sha256:&lt;digest&gt;). Production must be digest-pinned.
 * @param pullPolicy startup pull behaviour: ALWAYS, IF_NOT_PRESENT, or NEVER.
 * @param requireDigest when true, {@code AgentImagePinGuard} fails startup unless
 *     {@link #reference()} matches {@link #DIGEST_PATTERN}.
 */
@Validated
@ConfigurationProperties(prefix = "hephaestus.agent.image")
public record AgentImageProperties(
    @DefaultValue("ghcr.io/ls1intum/hephaestus/agent-pi:latest") @NotBlank String reference,
    @DefaultValue("IF_NOT_PRESENT") ImagePullPolicy pullPolicy,
    @DefaultValue("false") boolean requireDigest
) {
    /** Canonical OCI digest reference shape consumed by both the predicate and the guard's error message. */
    public static final String DIGEST_SUFFIX_DESCRIPTION = "@sha256:<64 lowercase hex>";

    public static final Pattern DIGEST_PATTERN = Pattern.compile(".+@sha256:[a-f0-9]{64}$");

    /** True iff {@link #reference()} ends in {@code @sha256:<64 lowercase hex>}. */
    public boolean isDigestPinned() {
        return DIGEST_PATTERN.matcher(reference).matches();
    }
}
