package de.tum.cit.aet.hephaestus.agent.job;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.context.EvidenceCollectionException;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobPreparationException;
import de.tum.cit.aet.hephaestus.agent.handler.spi.ReviewSourceNotReadyException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.CannotCreateTransactionException;

@Tag("unit")
class PreparationFailureTest {
    static Stream<Arguments> failures() {
        return Stream.of(
                Arguments.of(new ReviewSourceNotReadyException("pending"), PreparationFailure.Kind.SOURCE_NOT_READY),
                Arguments.of(new IOException("file"), PreparationFailure.Kind.IO),
                Arguments.of(new UncheckedIOException(new IOException("file")), PreparationFailure.Kind.IO),
                Arguments.of(new DataIntegrityViolationException("constraint"), PreparationFailure.Kind.DATABASE),
                Arguments.of(new CannotCreateTransactionException("database"), PreparationFailure.Kind.DATABASE),
                Arguments.of(new JobPreparationException("metadata"), PreparationFailure.Kind.INPUT_PREPARATION),
                Arguments.of(new IllegalStateException("unknown"), PreparationFailure.Kind.UNKNOWN),
                Arguments.of(
                        new JobPreparationException("folder", new UncheckedIOException(new IOException("file"))),
                        PreparationFailure.Kind.IO),
                Arguments.of(
                        new JobPreparationException(
                                "required",
                                new EvidenceCollectionException(
                                        "collection", new DataIntegrityViolationException("constraint"))),
                        PreparationFailure.Kind.DATABASE),
                Arguments.of(
                        new JobPreparationException("wrapper", new ReviewSourceNotReadyException("pending")),
                        PreparationFailure.Kind.SOURCE_NOT_READY),
                Arguments.of(
                        new JobPreparationException(
                                "wrapper", new RuntimeException("unclassified", new IOException("buried"))),
                        PreparationFailure.Kind.INPUT_PREPARATION),
                Arguments.of(
                        new RuntimeException("unclassified", new IOException("buried")),
                        PreparationFailure.Kind.UNKNOWN),
                Arguments.of(new EvidenceCollectionException("unknown", null), PreparationFailure.Kind.UNKNOWN));
    }

    @ParameterizedTest
    @MethodSource("failures")
    void shouldPreserveKnownTypesOnlyThroughPreparationWrappers(Throwable failure, PreparationFailure.Kind kind) {
        var diagnosis = PreparationFailure.of(PreparationFailure.Stage.INPUTS, failure);
        assertThat(diagnosis.stage()).isEqualTo(PreparationFailure.Stage.INPUTS);
        assertThat(diagnosis.kind()).isEqualTo(kind);
    }

    @Test
    void shouldStopWhenKnownWrappersHaveACycle() {
        var first = new JobPreparationException("first");
        var second = new JobPreparationException("second", first);
        first.initCause(second);
        assertThat(PreparationFailure.of(PreparationFailure.Stage.INPUTS, first).kind())
                .isEqualTo(PreparationFailure.Kind.INPUT_PREPARATION);
    }

    @Test
    void shouldIgnoreSuppressedCleanupCauses() {
        var failure = new JobPreparationException("primary");
        failure.addSuppressed(new IOException("cleanup"));
        assertThat(PreparationFailure.of(PreparationFailure.Stage.INPUTS, failure)
                        .kind())
                .isEqualTo(PreparationFailure.Kind.INPUT_PREPARATION);
    }
}
