package de.tum.cit.aet.hephaestus.agent.runtime;

import de.tum.cit.aet.hephaestus.practices.spi.ModelSlot;
import de.tum.cit.aet.hephaestus.practices.spi.Need;
import java.util.List;

/** Direct model slots declared by one script and the counted call refusals. */
public record ScriptModelUse(ModelSlot slot, Need need, boolean bound, List<Failure> failures) {
    public ScriptModelUse {
        failures = List.copyOf(failures);
    }

    public record Failure(String reason, int count) {
        public Failure {
            if (!reason.matches("[a-z][a-z0-9-]{0,63}") || count <= 0)
                throw new IllegalArgumentException("Invalid model call refusal");
        }
    }
}
