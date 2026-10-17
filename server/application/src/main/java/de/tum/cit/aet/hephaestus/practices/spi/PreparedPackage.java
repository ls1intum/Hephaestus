package de.tum.cit.aet.hephaestus.practices.spi;

import java.time.Instant;
import java.util.List;

public record PreparedPackage(String packageDigest, Instant preparedAt, List<PreparedFeedback> pieces) {
    public PreparedPackage {
        pieces = List.copyOf(pieces);
    }
}
