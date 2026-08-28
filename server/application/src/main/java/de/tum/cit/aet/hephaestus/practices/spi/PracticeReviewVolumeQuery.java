package de.tum.cit.aet.hephaestus.practices.spi;

import java.time.Instant;

public interface PracticeReviewVolumeQuery {
    int countSince(long workspaceId, Instant since);
}
