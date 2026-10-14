package de.tum.cit.aet.hephaestus.integration.scm.feedback;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.CopyReceipt;
import de.tum.cit.aet.hephaestus.integration.core.spi.CopyUpdateReceipt;
import de.tum.cit.aet.hephaestus.integration.core.spi.CopyWriteFence;
import de.tum.cit.aet.hephaestus.integration.core.spi.CopyWriteResult;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackNotSentException;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackTarget;
import de.tum.cit.aet.hephaestus.integration.core.spi.LineNote;
import de.tum.cit.aet.hephaestus.integration.core.spi.LineNoteWriter;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnServerRole
public class ScmLineNotePoster {
    private final List<LineNoteWriter> writers;

    public ScmLineNotePoster(List<LineNoteWriter> writers) {
        this.writers = List.copyOf(writers);
    }

    public CopyWriteResult post(FeedbackTarget target, List<LineNote> notes, CopyWriteFence fence) {
        return writer(target).postImmutablePackage(target, notes, fence);
    }

    public @Nullable List<CopyReceipt> findPosted(FeedbackTarget target, List<LineNote> notes) {
        return writer(target).findPosted(target, notes);
    }

    public List<CopyUpdateReceipt> retract(FeedbackTarget target, List<CopyReceipt> copies) {
        return writer(target).retract(target, copies);
    }

    private LineNoteWriter writer(FeedbackTarget target) {
        var matches = writers.stream()
                .filter(w -> w.kind() == target.integration().kind())
                .toList();
        if (matches.size() != 1) throw new FeedbackNotSentException("Line-note writer is unavailable or ambiguous");
        return matches.getFirst();
    }
}
