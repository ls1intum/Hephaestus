package de.tum.cit.aet.hephaestus.agent.catalog;

import de.tum.cit.aet.hephaestus.agent.LlmProperties;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.security.PrivateAddressGuard;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * SSRF egress guard for LLM provider connections.
 *
 * <p>Userinfo, query strings and fragments are rejected because that is how a gateway URL smuggles a
 * credential (e.g. {@code https://gw/v1?api-key=SECRET}) into snapshots, DTOs and logs.
 *
 * <p>Validating here is not sufficient on its own: the same {@link PrivateAddressGuard} predicate must
 * stay re-applied at connect time by the proxy's and probe's guarded resolver, or a DNS rebind reopens
 * the window between validation and connection.
 */
@Component
@WorkspaceAgnostic("Instance egress policy reads the global instance_llm_settings singleton, not tenant data")
public class EgressPolicy {

    private static final String NOT_PUBLIC_HTTPS = "Provider host must be a public HTTPS URL";
    private static final String NO_CREDENTIALS_OR_QUERY =
        "Provider URLs must not contain credentials or query parameters.";
    private static final Set<String> LOCAL_DEV_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "[::1]");

    private final InstanceLlmSettingsRepository settingsRepository;

    /**
     * Off in production — a loopback "provider" would let a workspace admin reach host-local services.
     * Read from {@link LlmProperties} so this guard and the probe's connect-time resolver cannot drift
     * apart on what "loopback allowed" means.
     */
    private final boolean allowLoopback;

    public EgressPolicy(InstanceLlmSettingsRepository settingsRepository, LlmProperties llmProperties) {
        this.settingsRepository = settingsRepository;
        this.allowLoopback = llmProperties.egress().allowLoopback();
    }

    public void validate(String baseUrl) {
        URI uri = parse(baseUrl);
        if (uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException(NO_CREDENTIALS_OR_QUERY);
        }
        String host = uri.getHost();
        String scheme = uri.getScheme();
        if (host == null || host.isBlank() || scheme == null) {
            throw new IllegalArgumentException(NOT_PUBLIC_HTTPS);
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        boolean localDev = allowLoopback && LOCAL_DEV_HOSTS.contains(normalizedHost);

        String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
        boolean https = normalizedScheme.equals("https");
        boolean httpLocalDev = normalizedScheme.equals("http") && localDev;
        if (!https && !httpLocalDev) {
            throw new IllegalArgumentException(NOT_PUBLIC_HTTPS);
        }

        if (!localDev) {
            assertPublicAddress(normalizedHost);
            assertAllowlisted(normalizedHost);
        }
    }

    private static URI parse(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException(NOT_PUBLIC_HTTPS);
        }
        try {
            return new URI(baseUrl.trim());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(NOT_PUBLIC_HTTPS);
        }
    }

    private static void assertPublicAddress(String host) {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException(NOT_PUBLIC_HTTPS);
        }
        for (InetAddress address : addresses) {
            if (PrivateAddressGuard.isNonPublic(address)) {
                throw new IllegalArgumentException(NOT_PUBLIC_HTTPS);
            }
        }
    }

    private void assertAllowlisted(String host) {
        InstanceLlmSettings settings = settingsRepository.findById((short) 1).orElse(null);
        String allowlist = settings != null ? settings.getAllowedEgressHosts() : null;
        if (allowlist == null || allowlist.isBlank()) {
            return;
        }
        Set<String> allowed = Arrays.stream(allowlist.split("[,\\n\\r]+"))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(s -> s.toLowerCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());
        if (!allowed.contains(host)) {
            throw new IllegalArgumentException("Provider host " + host + " is not in the allowed list");
        }
    }
}
