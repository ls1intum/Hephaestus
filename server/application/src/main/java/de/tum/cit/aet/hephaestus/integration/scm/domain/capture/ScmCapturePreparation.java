package de.tum.cit.aet.hephaestus.integration.scm.domain.capture;

import de.tum.cit.aet.hephaestus.evidence.SourceAbsenceReason;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureCode;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureException;
import de.tum.cit.aet.hephaestus.integration.scm.domain.workdir.GitRepositoryManager;
import org.jspecify.annotations.Nullable;

// Share one fetch and its availability fact across the sources in this capture.
public final class ScmCapturePreparation {
    private ReviewRepositoryPreparer.@Nullable PreparedReview prepared;
    private @Nullable RuntimeException failure;
    private @Nullable SourceAbsenceReason unavailable;

    /**
     * The review prepared so far, or null when no source has asked yet or the preparation failed.
     */
    public synchronized ReviewRepositoryPreparer.@Nullable PreparedReview prepared() {
        return prepared;
    }

    public synchronized @Nullable SourceAbsenceReason unavailableReason() {
        return unavailable;
    }

    public synchronized ReviewRepositoryPreparer.@Nullable PreparedReview prepare(
            ReviewRepositoryPreparer preparer, ScmCaptureContext capture) {
        if (prepared != null) return prepared;
        if (failure != null) throw failure;
        if (unavailable != null) return null;
        try {
            var review = preparer.prepare(capture);
            prepared = review;
            return review;
        } catch (GitRepositoryManager.RepositoryFetchException exception) {
            unavailable = SourceAbsenceReason.REPOSITORY_FETCH_FAILED;
            return null;
        } catch (ItemCaptureException exception) {
            if (exception.code() != ItemCaptureCode.VERSION_UNAVAILABLE) {
                failure = exception;
                throw exception;
            }
            unavailable = SourceAbsenceReason.REVIEW_VERSION_UNAVAILABLE;
            return null;
        } catch (RuntimeException exception) {
            failure = exception;
            throw exception;
        }
    }
}
