package de.tum.in.www1.hephaestus.activity.model;

public class RuleNotFoundException extends RuntimeException {

    public RuleNotFoundException(long id) {
        super("Rule with id '" + id + "' not found");
    }
}
