package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.core.runtime.RuntimeRole;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Refuses to start an application server that offers a practice signal nothing can raise.
 *
 * <p>The two sets it compares are derived independently — what an author may bind to comes from the
 * registered artifact descriptors, what an integration says it raises comes from the manifests — and the
 * disagreement between them is the whole check. Offering a switch no shipped integration will ever flip
 * is a mistake in the build, so it is the same mistake on every deployment, and the honest response is
 * to not come up.
 *
 * <p>Its own bean, gated, rather than a {@code @PostConstruct} on {@link PracticeSignalCoverage}. That
 * class is an ungated {@code @Service} because the trace views need it in every role, and a throw from
 * its construction would crash-loop the webhook pod — which exists precisely so that webhook reception
 * survives an app-server failure, and whose missed push events cannot be redelivered. Splitting the
 * assertion off keeps the crash where it belongs: the role that would serve the broken authoring UI.
 * Same shape and same reason as {@code IntegrationFrameworkBootstrap}.
 *
 * <p>CI reaches this first regardless — {@code PracticeSignalCoverageIntegrationTest} runs the same
 * method over the same real registries, so the boot failure is the second line of defence rather than
 * the first, and exists for images built somewhere that gate never ran.
 */
@Component
@ConditionalOnProperty(name = RuntimeRole.SERVER_PROPERTY, havingValue = "true", matchIfMissing = true)
public class PracticeSignalVocabularyCheck {

    private final PracticeSignalCoverage coverage;

    public PracticeSignalVocabularyCheck(PracticeSignalCoverage coverage) {
        this.coverage = coverage;
    }

    @PostConstruct
    void verify() {
        coverage.validateAuthoringVocabulary();
    }
}
