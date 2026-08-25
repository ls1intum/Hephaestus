package de.tum.cit.aet.hephaestus.testconfig;

import de.tum.cit.aet.hephaestus.integration.core.spi.InstallationRepositoryEnumerator;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@Order(Ordered.LOWEST_PRECEDENCE)
public class StubInstallationRepositoryEnumerator implements InstallationRepositoryEnumerator {

    private List<InstallationRepository> repositories = List.of();

    public void returns(List<InstallationRepository> repositories) {
        this.repositories = List.copyOf(repositories);
    }

    public void reset() {
        repositories = List.of();
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.GITHUB;
    }

    @Override
    public List<InstallationRepository> enumerate(long installationId) {
        return repositories;
    }
}
