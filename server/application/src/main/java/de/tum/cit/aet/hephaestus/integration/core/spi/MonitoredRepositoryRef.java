package de.tum.cit.aet.hephaestus.integration.core.spi;

public record MonitoredRepositoryRef(long repositoryItemId, long monitorId, String nameWithOwner) {
    public MonitoredRepositoryRef {
        if (repositoryItemId <= 0 || monitorId <= 0 || nameWithOwner.isBlank())
            throw new IllegalArgumentException("Invalid monitored repository reference");
    }
}
