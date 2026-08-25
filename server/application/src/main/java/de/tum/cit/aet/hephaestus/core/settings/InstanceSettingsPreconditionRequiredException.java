package de.tum.cit.aet.hephaestus.core.settings;

final class InstanceSettingsPreconditionRequiredException extends RuntimeException {

    InstanceSettingsPreconditionRequiredException() {
        super("If-Match must contain the current instance settings ETag when releasing Silent Mode");
    }
}
