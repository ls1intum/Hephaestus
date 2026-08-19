package de.tum.cit.aet.hephaestus.practices.curated.adoption;

public class StaleCatalogAdoptionPlanException extends RuntimeException {

    public StaleCatalogAdoptionPlanException() {
        super("The practice or its workspace adoption outcome changed. Review the current definition before adopting.");
    }
}
