package de.tum.cit.aet.hephaestus.integration.core.spi;

import java.util.List;
import org.jspecify.annotations.Nullable;

public interface LineNoteWriter {
    IntegrationKind kind();

    CopyWriteResult postImmutablePackage(FeedbackTarget target, List<LineNote> notes, CopyWriteFence fence);

    @Nullable
    List<CopyReceipt> findPosted(FeedbackTarget target, List<LineNote> notes);

    List<CopyUpdateReceipt> retract(FeedbackTarget target, List<CopyReceipt> copies);
}
