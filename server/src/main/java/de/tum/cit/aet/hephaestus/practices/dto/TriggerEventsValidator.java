package de.tum.cit.aet.hephaestus.practices.dto;

import de.tum.cit.aet.hephaestus.practices.PracticeTriggerOptions;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates that every element in a trigger events list is a literal some domain declares, and that
 * there are no duplicates.
 *
 * <p>The allow-list is resolved per validation rather than held in a static, because it is now assembled
 * from the registered domain vocabularies: a static would freeze whatever the first classload saw.
 * Spring instantiates constraint validators through the application context, which is what makes the
 * injection here work.
 */
public class TriggerEventsValidator implements ConstraintValidator<ValidTriggerEvents, List<String>> {

    private final PracticeTriggerOptions triggerOptions;

    public TriggerEventsValidator(PracticeTriggerOptions triggerOptions) {
        this.triggerOptions = triggerOptions;
    }

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

        Set<String> validEvents = triggerOptions.allEvents();
        Set<String> unknown = value
            .stream()
            .filter(e -> !validEvents.contains(e))
            .collect(Collectors.toSet());
        if (!unknown.isEmpty()) {
            context.disableDefaultConstraintViolation();
            context
                .buildConstraintViolationWithTemplate(
                    "Unknown trigger events: " +
                        String.join(", ", unknown) +
                        ". Valid events are: " +
                        String.join(", ", validEvents.stream().sorted().toList())
                )
                .addConstraintViolation();
            return false;
        }

        return true;
    }
}
