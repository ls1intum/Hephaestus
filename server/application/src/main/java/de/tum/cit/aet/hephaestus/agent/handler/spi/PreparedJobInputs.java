package de.tum.cit.aet.hephaestus.agent.handler.spi;

import de.tum.cit.aet.hephaestus.agent.context.JobFolderIndex;
import de.tum.cit.aet.hephaestus.evidence.AutomatedReviewReadinessReport;
import de.tum.cit.aet.hephaestus.evidence.capture.EvidenceDirectory;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The immutable, disk-backed folder retained for one attempt through admission.
 *
 * @param copiesExpireAt the fixed expiry of the occasion record these inputs copy; later copies become ineligible
 *     at it, and the existing sweep removes expired retained files once their runtime lease permits it.
 *     Null when the inputs copy no retained occasion record
 */
public record PreparedJobInputs(
        Map<String, Path> filesOnDisk,
        List<EvidenceDirectory> directories,
        List<AutoCloseable> cleanups,
        @Nullable JobFolderIndex folderIndex,
        @Nullable AutomatedReviewReadinessReport automatedReviewReadinessReport,
        @Nullable Instant copiesExpireAt)
        implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(PreparedJobInputs.class);

    public PreparedJobInputs {
        filesOnDisk = Collections.unmodifiableMap(new LinkedHashMap<>(filesOnDisk));
        directories = List.copyOf(directories);
        cleanups = List.copyOf(cleanups);
    }

    public PreparedJobInputs(
            Map<String, Path> filesOnDisk,
            List<EvidenceDirectory> directories,
            List<AutoCloseable> cleanups,
            @Nullable JobFolderIndex folderIndex,
            @Nullable AutomatedReviewReadinessReport automatedReviewReadinessReport) {
        this(filesOnDisk, directories, cleanups, folderIndex, automatedReviewReadinessReport, null);
    }

    /** The same folder, owned by the same cleanups, whose copies expire at {@code expiresAt}. */
    public PreparedJobInputs withCopiesExpireAt(@Nullable Instant expiresAt) {
        return new PreparedJobInputs(
                filesOnDisk, directories, cleanups, folderIndex, automatedReviewReadinessReport, expiresAt);
    }

    @Override
    public void close() {
        for (AutoCloseable cleanup : cleanups) {
            try {
                cleanup.close();
            } catch (Exception exception) {
                log.warn("Could not release the job folder", exception);
            }
        }
    }
}
