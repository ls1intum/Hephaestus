package de.tum.cit.aet.hephaestus.integration.scm.domain.capture;

import org.jspecify.annotations.Nullable;

/**
 * One Git preparation per review request. Every source that reads the clone asks here, so the fetch
 * runs once, and a failure is handed to each of them in turn so each still records it for its own
 * source.
 */
public final class ScmCapturePreparation {
    private ReviewRepositoryPreparer.@Nullable PreparedReview prepared;
    private @Nullable RuntimeException failure;

    /**
     * The review prepared so far, or null when no source has asked yet or the preparation failed.
     */
    public synchronized ReviewRepositoryPreparer.@Nullable PreparedReview prepared() {
        return prepared;
    }

    public synchronized ReviewRepositoryPreparer.PreparedReview prepare(
            ReviewRepositoryPreparer preparer, ScmCaptureContext capture) {
        if (prepared != null) return prepared;
        if (failure != null) throw failure;
        try {
            var review = preparer.prepare(capture);
            prepared = review;
            return review;
        } catch (RuntimeException exception) {
            failure = exception;
            throw exception;
        }
    }
}
