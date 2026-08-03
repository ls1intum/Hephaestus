package de.tum.cit.aet.hephaestus.evidence;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Objects;
import java.util.regex.Pattern;

public record EvidenceProfileId(String value) {
    private static final Pattern FORMAT = Pattern.compile("[a-z][a-z0-9]*(?:-[a-z0-9]+)*");

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public EvidenceProfileId {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid evidence profile id: " + value);
        }
    }

    @Override
    @JsonValue
    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
