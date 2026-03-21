package de.tum.in.www1.hephaestus.practices.dto;

import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent.TriggerEventNames;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates that every element in a trigger events list is a known
 * {@link TriggerEventNames} constant, and that there are no duplicates.
 */
public class TriggerEventsValidator implements ConstraintValidator<ValidTriggerEvents, List<String>> {

    static final Set<String> VALID_EVENTS = Set.of(
        TriggerEventNames.PULL_REQUEST_CREATED,
        TriggerEventNames.PULL_REQUEST_READY,
        TriggerEventNames.PULL_REQUEST_SYNCHRONIZED,
        TriggerEventNames.REVIEW_SUBMITTED
    );

    @Override
    public boolean isValid(List<String> value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // @NotNull handles nullability
        }

        // Check for duplicates
        Set<String> seen = new HashSet<>();
        List<String> duplicates = value
            .stream()
            .filter(e -> !seen.add(e))
            .distinct()
            .toList();
        if (!duplicates.isEmpty()) {
            context.disableDefaultConstraintViolation();
            context
                .buildConstraintViolationWithTemplate("Duplicate trigger events: " + String.join(", ", duplicates))
                .addConstraintViolation();
            return false;
        }

        // Check for unknown events
        Set<String> unknown = value
            .stream()
            .filter(e -> !VALID_EVENTS.contains(e))
            .collect(Collectors.toSet());
        if (!unknown.isEmpty()) {
            context.disableDefaultConstraintViolation();
            context
                .buildConstraintViolationWithTemplate(
                    "Unknown trigger events: " +
                        String.join(", ", unknown) +
                        ". Valid events are: " +
                        String.join(", ", VALID_EVENTS.stream().sorted().toList())
                )
                .addConstraintViolation();
            return false;
        }

        return true;
    }
}
