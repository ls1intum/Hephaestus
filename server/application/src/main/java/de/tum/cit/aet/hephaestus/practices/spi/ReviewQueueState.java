package de.tum.cit.aet.hephaestus.practices.spi;

/**
 * Where a review's job stands in the queue now, as the queue counts it: waiting for a worker,
 * held for budget, or running.
 */
public enum ReviewQueueState {
    WAITING,
    HELD,
    RUNNING;
}
