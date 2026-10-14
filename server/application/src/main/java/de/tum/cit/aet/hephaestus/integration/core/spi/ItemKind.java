package de.tum.cit.aet.hephaestus.integration.core.spi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Objects;

public record ItemKind(String value) {
    public static final int MAX_LENGTH = 64;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public ItemKind {
        Objects.requireNonNull(value);
        if (value.length() > MAX_LENGTH || !value.matches("[a-z][a-z0-9_]*[.][a-z][a-z0-9_]*"))
            throw new IllegalArgumentException("Invalid item kind");
    }

    @JsonValue
    @Override
    public String value() {
        return value;
    }
}
