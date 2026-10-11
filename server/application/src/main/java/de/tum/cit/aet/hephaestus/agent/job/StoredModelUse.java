package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.practices.spi.PracticeScriptModelPurpose;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeScriptModelUseDTO;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeScriptNeed;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeScriptNotRatedDTO;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeScriptNotRatedReason;
import de.tum.cit.aet.hephaestus.workspace.spi.DataHandlingTier;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * One model use as the {@code models} column of {@code agent_job_practice_script_run} stores it. The stored form
 * belongs to this module, apart from the wire form {@link PracticeScriptModelUseDTO}, so a change to a wire field
 * name cannot make the stored runs unreadable.
 *
 * <p>The enum values are stored by name, and they are a storage contract. To rename, split, or remove a value,
 * add a changeset that rewrites the stored {@code models}. Otherwise the stored runs that hold the old name
 * cannot be read.
 *
 * @param tier the data handling tier of the model that the attempt froze for this purpose; {@code null} when the
 *     attempt had none. The job's snapshot holds only its latest attempt's models, so the run keeps its own.
 */
record StoredModelUse(
        PracticeScriptModelPurpose purpose,
        PracticeScriptNeed need,
        boolean bound,
        List<NotRated> notRated,
        @Nullable DataHandlingTier tier) {

    StoredModelUse {
        notRated = List.copyOf(notRated);
    }

    record NotRated(PracticeScriptNotRatedReason reason, int count) {}

    static StoredModelUse of(PracticeScriptModelUseDTO use, @Nullable DataHandlingTier tier) {
        return new StoredModelUse(
                use.purpose(),
                use.need(),
                use.bound(),
                use.notRated().stream()
                        .map(entry -> new NotRated(entry.reason(), entry.count()))
                        .toList(),
                tier);
    }

    PracticeScriptModelUseDTO toDto() {
        return new PracticeScriptModelUseDTO(
                purpose,
                need,
                bound,
                notRated.stream()
                        .map(entry -> new PracticeScriptNotRatedDTO(entry.reason(), entry.count()))
                        .toList());
    }
}
