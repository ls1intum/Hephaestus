package de.tum.in.www1.hephaestus.intelligenceservice;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ValidationException;
import java.util.Set;

public class BeanValidationException extends ValidationException {

    /**
     *
     */
    private static final long serialVersionUID = -5294733947409491364L;
    Set<ConstraintViolation<Object>> violations;

    public BeanValidationException(Set<ConstraintViolation<Object>> violations) {
        this.violations = violations;
    }

    public Set<ConstraintViolation<Object>> getViolations() {
        return violations;
    }

    public void setViolations(Set<ConstraintViolation<Object>> violations) {
        this.violations = violations;
    }
}
