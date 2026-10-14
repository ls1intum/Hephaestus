package de.tum.cit.aet.hephaestus.practices.spi;

import java.util.List;
import org.jspecify.annotations.Nullable;

public interface ReviewModelBindings {
    List<BoundModelSlot> models(long workspaceId, List<@Nullable Long> developerIds);
}
