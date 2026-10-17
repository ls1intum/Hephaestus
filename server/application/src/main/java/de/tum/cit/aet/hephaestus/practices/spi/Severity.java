package de.tum.cit.aet.hephaestus.practices.spi;

public enum Severity {
    CRITICAL,
    MAJOR,
    MINOR,
    TRIVIAL;

    public int rank() {
        return switch (this) {
            case CRITICAL -> 0;
            case MAJOR -> 1;
            case MINOR -> 2;
            case TRIVIAL -> 3;
        };
    }
}
