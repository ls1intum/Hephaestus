package de.tum.cit.aet.hephaestus.config;

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
}
