package de.tum.cit.aet.hephaestus.practices.review.report;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record ScanReportCursor(
        UUID scanId,
        String path,
        long practiceId,
        long itemId,
        int position,
        @Nullable UUID problemId) implements Comparable<ScanReportCursor> {
    @Override
    public int compareTo(ScanReportCursor other) {
        int c = path.compareTo(other.path);
        if (c == 0) c = Long.compare(practiceId, other.practiceId);
        if (c == 0) c = Long.compare(itemId, other.itemId);
        if (c == 0) c = Integer.compare(position, other.position);
        if (c == 0 && problemId != null && other.problemId() != null)
            c = problemId.toString().compareTo(other.problemId().toString());
        return c;
    }
}
