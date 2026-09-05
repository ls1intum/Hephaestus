package de.tum.cit.aet.hephaestus.agent.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.net.InetAddress;
import org.junit.jupiter.api.Test;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;

class SandboxGatewayConfigurationTest extends BaseUnitTest {

    /**
     * Sandboxes dial the worker across a per-job Docker bridge, so the gateway must answer on every
     * interface, while the connector serving {@code /api/**} and the management endpoints stays
     * wherever {@code server.address} put it.
     */
    @Test
    void bindsTheGatewayOnEveryInterfaceWhileTheApplicationConnectorStaysWhereServerAddressPutIt() {
        var factory = new TomcatServletWebServerFactory();
        factory.setAddress(InetAddress.getLoopbackAddress());

        new SandboxGatewayConfiguration()
                .sandboxGatewayConnector(new SandboxGatewayProperties(9081, 1024, 60))
                .customize(factory);

        assertThat(factory.getAdditionalConnectors()).singleElement().satisfies(connector -> {
            assertThat(connector.getPort()).isEqualTo(9081);
            assertThat(connector.getProperty("address")).isNull();
        });
        assertThat(factory.getAddress()).isEqualTo(InetAddress.getLoopbackAddress());
    }
}
