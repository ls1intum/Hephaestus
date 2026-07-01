package de.tum.cit.aet.hephaestus.practices.dto;

import de.tum.cit.aet.hephaestus.practices.TriggerEventCatalog;
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

    // Allow-list is derived from TriggerEventCatalog (the single source of truth for subscribable events)
    // rather than duplicated here, so it cannot drift out of sync with the catalog.
    static final Set<String> VALID_EVENTS = TriggerEventCatalog.allEvents();

    @Override
    public boolean isValid(List<String> value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // @NotNull handles nullability
        }

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
