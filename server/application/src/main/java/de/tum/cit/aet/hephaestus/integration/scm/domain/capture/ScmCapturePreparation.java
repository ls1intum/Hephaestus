package de.tum.cit.aet.hephaestus.integration.scm.domain.capture;

import org.jspecify.annotations.Nullable;

// Share one fetch per request, but record a preparation failure separately for each affected source.
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
