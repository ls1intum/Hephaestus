package de.tum.cit.aet.hephaestus.core;

import io.netty.resolver.AddressResolverGroup;
import io.netty.resolver.DefaultAddressResolverGroup;
import java.net.InetSocketAddress;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.client.HttpClient;

/** Connectors pinning the JVM/system DNS resolver instead of Netty's async resolver. */
public final class WebClientConnectors {

    private WebClientConnectors() {}

    public static ClientHttpConnector systemDns() {
        return new ReactorClientHttpConnector(HttpClient.create().resolver(DefaultAddressResolverGroup.INSTANCE));
    }

    /**
     * Like {@link #systemDns()} but rejects DNS answers that resolve to a non-public address, closing
     * the DNS-rebind/TOCTOU SSRF window. Use this for any outbound call whose host derives from a
     * user-supplied server URL (e.g. the GitLab workspace-creation preflight). See
     * {@link SsrfGuardedResolverGroup}.
     */
    public static ClientHttpConnector ssrfGuarded() {
        return new ReactorClientHttpConnector(HttpClient.create().resolver(SsrfGuardedResolverGroup.INSTANCE));
    }

    /**
     * Like {@link #ssrfGuarded()} but, when {@code allowLoopback} is {@code true}, exempts a RESOLVED
     * loopback address from the block — for outbound calls whose upstream policy layer has already
     * decided loopback is an acceptable dev/e2e target (see {@code EgressPolicy#allowLoopback}). Every
     * other private/reserved range stays blocked either way, so DNS rebinding to a non-loopback
     * private address is closed regardless of the flag.
     */
    public static ClientHttpConnector ssrfGuarded(boolean allowLoopback) {
        return new ReactorClientHttpConnector(HttpClient.create().resolver(resolverGroup(allowLoopback)));
    }

    /** The raw Netty resolver group backing {@link #ssrfGuarded(boolean)} — for callers that build their own {@code HttpClient}. */
    public static AddressResolverGroup<InetSocketAddress> resolverGroup(boolean allowLoopback) {
        return allowLoopback ? SsrfGuardedResolverGroup.LOOPBACK_EXEMPT_INSTANCE : SsrfGuardedResolverGroup.INSTANCE;
    }
}
