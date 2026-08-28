package de.tum.cit.aet.hephaestus.integration.outline.client;

import java.util.Optional;

public interface OutlineTokenClient {
    OutlineApiClient.OutlineIdentity validateToken(String serverUrl, String token);
    Optional<OutlineApiClient.OutlineTokenDescription> describeToken(String serverUrl, String token);
}
