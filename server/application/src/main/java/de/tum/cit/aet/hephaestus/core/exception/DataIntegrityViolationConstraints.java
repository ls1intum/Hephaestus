package de.tum.cit.aet.hephaestus.core.exception;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

public final class DataIntegrityViolationConstraints {

    private DataIntegrityViolationConstraints() {}

    public static boolean hasName(DataIntegrityViolationException exception, String expectedName) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return expectedName.equalsIgnoreCase(violation.getConstraintName());
            }
        }
        return false;
    }
}
