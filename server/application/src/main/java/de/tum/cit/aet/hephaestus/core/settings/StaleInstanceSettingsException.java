package de.tum.cit.aet.hephaestus.core.settings;

import org.jspecify.annotations.Nullable;

final class StaleInstanceSettingsException extends RuntimeException {

    StaleInstanceSettingsException() {
        this(null);
    }

    StaleInstanceSettingsException(@Nullable Throwable cause) {
        super("Instance settings changed since they were loaded.", cause);
    }
}
