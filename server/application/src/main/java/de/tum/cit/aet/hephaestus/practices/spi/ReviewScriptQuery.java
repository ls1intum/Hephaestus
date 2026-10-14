package de.tum.cit.aet.hephaestus.practices.spi;

import java.util.List;
import java.util.UUID;

public interface ReviewScriptQuery {
    List<PracticeScriptRunDTO> forReview(long workspaceId, UUID reviewId);
}
