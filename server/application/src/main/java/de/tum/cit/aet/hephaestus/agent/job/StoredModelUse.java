package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.runtime.ScriptModelUse;
import de.tum.cit.aet.hephaestus.practices.spi.ModelSlot;
import de.tum.cit.aet.hephaestus.practices.spi.Need;
import de.tum.cit.aet.hephaestus.workspace.spi.DataHandlingTier;
import java.util.List;
import org.jspecify.annotations.Nullable;

record StoredModelUse(
        ModelSlot slot,
        Need need,
        boolean bound,
        List<ScriptModelUse.Failure> failures,
        @Nullable DataHandlingTier tier) {
    StoredModelUse {
        failures = List.copyOf(failures);
    }

    static StoredModelUse of(ScriptModelUse use, @Nullable DataHandlingTier tier) {
        return new StoredModelUse(use.slot(), use.need(), use.bound(), use.failures(), tier);
    }
}
