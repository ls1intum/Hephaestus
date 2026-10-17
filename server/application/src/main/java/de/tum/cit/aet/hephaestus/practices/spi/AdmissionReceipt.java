package de.tum.cit.aet.hephaestus.practices.spi;

import java.util.List;
import java.util.UUID;

public record AdmissionReceipt(List<UUID> resultIds, List<WithheldItem> withheldItems) {
    public AdmissionReceipt {
        resultIds = List.copyOf(resultIds);
        withheldItems = List.copyOf(withheldItems);
    }
}
