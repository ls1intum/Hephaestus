package de.tum.cit.aet.hephaestus.agent.gateway;

import de.tum.cit.aet.hephaestus.core.runtime.RuntimeRole;
import org.apache.catalina.connector.Connector;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The connector sandboxes reach the worker on. Present wherever the worker role is (ADR 0005), so
 * the monolith opens it too.
 */
@Configuration
@ConditionalOnProperty(name = RuntimeRole.WORKER_PROPERTY, havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SandboxGatewayProperties.class)
public class SandboxGatewayConfiguration {

    /**
     * Deliberately carries no {@code address}, so it binds every interface: a sandbox dials the worker
     * across a per-job Docker bridge, on an address the worker does not know in advance. The
     * application and management connectors are the ones {@code server.address} keeps on loopback.
     */
    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> sandboxGatewayConnector(
            SandboxGatewayProperties properties) {
        return factory -> {
            Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
            connector.setPort(properties.port());
            factory.addAdditionalConnectors(connector);
        };
    }
}
