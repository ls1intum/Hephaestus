package de.tum.cit.aet.hephaestus.practices.reviewoutput.dto;

import org.jspecify.annotations.Nullable;

/** Reads enum names stored in the database, where a value this server no longer names must not fail a read. */
public final class StoredEnums {

    private StoredEnums() {}

    /** The constant of {@code type} named {@code stored}, or null when it is absent or unknown. */
    public static <E extends Enum<E>> @Nullable E parse(@Nullable String stored, Class<E> type) {
        if (stored == null) {
            return null;
        }
        for (E value : type.getEnumConstants()) {
            if (value.name().equals(stored)) {
                return value;
            }
        }
        return null;
    }
}
