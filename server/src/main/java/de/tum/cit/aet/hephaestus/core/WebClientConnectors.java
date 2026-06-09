package de.tum.cit.aet.hephaestus.core;

import io.netty.resolver.DefaultAddressResolverGroup;
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
}
