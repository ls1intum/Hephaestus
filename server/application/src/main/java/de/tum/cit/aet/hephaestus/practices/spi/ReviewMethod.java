package de.tum.cit.aet.hephaestus.practices.spi;

import java.util.List;

public record ReviewMethod(int epoch, String modelDigest, List<BoundModelSlot> models) {
    public ReviewMethod {
        models = List.copyOf(models);
    }
}
