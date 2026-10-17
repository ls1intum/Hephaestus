package de.tum.cit.aet.hephaestus.practices.spi;

import java.util.List;

public record ReviewRuntimeSnapshot(List<ReviewJobSnapshot> jobs) {
    public ReviewRuntimeSnapshot {
        jobs = List.copyOf(jobs);
    }
}
