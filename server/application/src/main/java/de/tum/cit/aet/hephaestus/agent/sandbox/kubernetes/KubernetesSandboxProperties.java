package de.tum.cit.aet.hephaestus.agent.sandbox.kubernetes;

import de.tum.cit.aet.hephaestus.agent.sandbox.SandboxRuntime;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnWorkerRole;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * The Kubernetes sandbox runtime. Bound and validated only when it is the selected runtime; a missing or invalid value
 * stops the worker's start. The chart passes every value; the worker's own Pod name and address come from the Downward
 * API.
 *
 * @param namespace the namespace of every sandbox Pod and Job. The installation owns it alone, so everything in it of
 *     the sandbox kinds is this installation's
 * @param podName this worker's own Pod
 * @param podIp the address of this worker's own Pod: its sandboxes reach its gateway there
 * @param podPidsLimit the per-Pod process limit ({@code podPidsLimit}) of the kubelet. The isolation probe proves that
 *     the nodes hold at most this many processes per Pod; a sandbox that asks for fewer does not start
 * @param runtimeClassName the RuntimeClass of every sandbox Pod, gVisor's for example; blank for the cluster's default
 * @param seccompProfile the shipped {@code sandbox/agent-seccomp-profile.json} as a Localhost profile, relative to the
 *     kubelet's seccomp directory; blank for {@code RuntimeDefault}
 */
@Validated
@ConditionalOnWorkerRole
@ConditionalOnProperty(name = SandboxRuntime.PROPERTY, havingValue = "kubernetes")
@ConfigurationProperties(prefix = "hephaestus.sandbox.kubernetes", ignoreUnknownFields = false)
public record KubernetesSandboxProperties(
        @DefaultValue("") @NotBlank @Pattern(regexp = DNS_LABEL)
        String namespace,

        @DefaultValue("") @NotBlank @Pattern(regexp = DNS_LABEL)
        String podName,

        @DefaultValue("") @NotBlank String podIp,
        @DefaultValue("0") @Min(1) @Max(4096) int podPidsLimit,

        @DefaultValue("") @Pattern(regexp = "|" + DNS_SUBDOMAIN)
        String runtimeClassName,

        @DefaultValue("") @Pattern(regexp = "|" + LOCALHOST_PROFILE)
        String seccompProfile) {

    /** A namespace, and a Pod name short enough to be a label value. */
    static final String DNS_LABEL = "[a-z0-9]([-a-z0-9]{0,61}[a-z0-9])?";

    static final String DNS_SUBDOMAIN = "[a-z0-9]([-a-z0-9.]{0,251}[a-z0-9])?";

    /** Relative to the kubelet's seccomp directory, without a step out of it. */
    static final String LOCALHOST_PROFILE = "(?!.*(^|/)\\.\\.(/|$))[A-Za-z0-9._-]+(/[A-Za-z0-9._-]+)*";

    @AssertTrue(message = "pod-ip must be an IPv4 or IPv6 address without a zone")
    private boolean isPodIpLiteral() {
        return isIpv4(podIp) || isIpv6(podIp);
    }

    /** Dotted decimal, four octets, as the Downward API writes {@code status.podIP}. */
    private static boolean isIpv4(String value) {
        String[] octets = value.split("\\.", -1);
        return octets.length == 4
                && Arrays.stream(octets).allMatch(octet -> octet.matches("25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9]"));
    }

    /**
     * The JDK's own IPv6 literal parser. A value that starts with a hexadecimal digit or a colon and contains a colon is
     * only ever parsed as a literal by {@link InetAddress#getByName}, never looked up: a name could resolve to another
     * worker. A zone ({@code %}) is not an address a Pod has.
     */
    private static boolean isIpv6(String value) {
        if (!value.matches("[0-9A-Fa-f:][0-9A-Fa-f:.]{1,44}") || value.indexOf(':') < 0) {
            return false;
        }
        try {
            InetAddress.getByName(value);
            return true;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    /** The host of this worker's gateway in a URL: the address itself, an IPv6 address in brackets. */
    String gatewayHost() {
        return podIp.indexOf(':') >= 0 ? "[" + podIp + "]" : podIp;
    }
}
