package de.tum.cit.aet.hephaestus.core.settings;

final class StaleInstanceSettingsException extends RuntimeException {

    StaleInstanceSettingsException() {
        super("Instance settings changed since they were loaded.");
    }
}
