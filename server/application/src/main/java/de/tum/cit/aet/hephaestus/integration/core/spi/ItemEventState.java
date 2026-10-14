package de.tum.cit.aet.hephaestus.integration.core.spi;

public enum ItemEventState {
    RECORDED,
    WAITING,
    STARTED,
    WITHHELD,
    BLOCKED,
    EXPIRED;

    public boolean isTerminal() {
        return this == STARTED || this == WITHHELD || this == EXPIRED;
    }
}
