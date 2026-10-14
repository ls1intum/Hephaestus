package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

class CitationVerificationTest extends BaseUnitTest {
    @Test
    void rejectsUnpairedSurrogatesRatherThanHashingReplacementBytes() {
        assertThatThrownBy(() -> CitationVerification.quoteDigest(String.valueOf((char) 0xd800)))
                .isInstanceOf(JobDeliveryException.class);
    }

    @Test
    void retainedProofDoesNotDependOnARuntimeJobOrAttempt() {
        var evidence = AdmittedResultFixtures.evidence("docs.document.core");
        assertThat(CitationVerification.isVerified(evidence.path("citations"))).isTrue();
        var proof = evidence.path("citations").get(0).path("verification");
        assertThat(proof.has("jobId")).isFalse();
        assertThat(proof.has("attempt")).isFalse();
    }

    @Test
    void refusesMissingOrChangedQuoteProof() {
        var evidence = AdmittedResultFixtures.evidence("docs.document.core");
        var citation = (ObjectNode) evidence.path("citations").get(0);
        citation.put("quote", "different words");
        assertThat(CitationVerification.isVerified(evidence.path("citations"))).isFalse();
        citation.remove("verification");
        assertThat(CitationVerification.isVerified(evidence.path("citations"))).isFalse();
    }
}
