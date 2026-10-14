package de.tum.cit.aet.hephaestus.integration.core.spi;

import java.util.List;
import java.util.UUID;

@FunctionalInterface
public interface CopyWriteFence {
    boolean beforeCreate(List<UUID> attempting, List<CopyReceipt> completed);
}
