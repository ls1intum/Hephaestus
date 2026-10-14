package de.tum.cit.aet.hephaestus.integration.core.spi;

import java.util.Map;

/** Current monitored repositories, keyed by their workspace repository item id. */
public interface MonitoredRepositoryQuery {
    Map<Long, MonitoredRepositoryRef> repositories(long workspaceId);
}
