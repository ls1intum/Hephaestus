package de.tum.cit.aet.hephaestus.agent.sandbox.docker.interactive;

import de.tum.cit.aet.hephaestus.agent.proxy.ProxyRouting;
import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.InteractiveSandboxSpec;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.ResourceLimits;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SecurityProfile;
import de.tum.cit.aet.hephaestus.agent.usage.FundingSource;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/** Non-secret identity of everything frozen into a reusable interactive sandbox. */
record InteractiveSandboxRuntimeKey(
    String image,
    java.util.List<String> command,
    Map<String, String> environment,
    boolean internetAccess,
    @Nullable String proxyUrl,
    ResourceLimits resourceLimits,
    SecurityProfile securityProfile,
    Map<String, String> inputDigests,
    Map<String, String> volumeMounts,
    @Nullable RoutingKey routing
) {
    static InteractiveSandboxRuntimeKey of(InteractiveSandboxSpec spec, @Nullable ProxyRouting routing) {
        Map<String, String> inputDigests = spec
            .inputFiles()
            .entrySet()
            .stream()
            // Both are cold-start snapshots. A warm runner owns the authoritative session, while
            // per-turn context is refreshed through its fetch_context callback. Neither changes
            // the frozen runtime and neither should restart the container on every turn.
            .filter(
                entry ->
                    !entry.getKey().startsWith(SandboxLayout.SESSIONS_DIR_PREFIX) &&
                    !entry.getKey().startsWith(SandboxLayout.CONTEXT_PREFIX)
            )
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> sha256(entry.getValue())));
        boolean internetAccess = spec.networkPolicy() != null && spec.networkPolicy().internetAccess();
        String proxyUrl = spec.networkPolicy() != null ? spec.networkPolicy().llmProxyUrl() : null;
        return new InteractiveSandboxRuntimeKey(
            spec.image(),
            spec.command(),
            spec.environment(),
            internetAccess,
            proxyUrl,
            spec.resourceLimits(),
            spec.securityProfile(),
            inputDigests,
            spec.volumeMounts(),
            routing != null ? RoutingKey.from(routing) : null
        );
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("JVM does not provide SHA-256", impossible);
        }
    }

    private record RoutingKey(
        String apiProtocol,
        String baseUrl,
        @Nullable FundingSource connectionScope,
        @Nullable Long connectionId,
        @Nullable Long modelId,
        @Nullable Long workspaceId,
        @Nullable Long legacyConfigId
    ) {
        static RoutingKey from(ProxyRouting routing) {
            return new RoutingKey(
                routing.apiProtocol(),
                routing.baseUrl(),
                routing.connectionScope(),
                routing.connectionId(),
                routing.modelId(),
                routing.workspaceId(),
                routing.legacyConfigId()
            );
        }
    }
}
