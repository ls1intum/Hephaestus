package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.context.EvidenceCollectionException;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobPreparationException;
import de.tum.cit.aet.hephaestus.agent.handler.spi.ReviewSourceNotReadyException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Set;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;

/** A preparation operation and a cause classified by type, without sensitive exception text. */
record PreparationFailure(Stage stage, Kind kind) {

    /** The owned operations before execution, in the order the executor runs them. */
    enum Stage {
        RUN_SETUP("while initializing review preparation"),
        INPUTS("while preparing the review inputs"),
        CREDENTIAL("while issuing the run credential"),
        SPEC("while building the sandbox specification"),
        PROVENANCE("while recording the input provenance"),
        EXECUTION_FENCE("while marking execution as started");

        private final String phrase;

        Stage(String phrase) {
            this.phrase = phrase;
        }

        String tag() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    enum Kind {
        SOURCE_NOT_READY("the reviewed source was not ready"),
        INPUT_PREPARATION("the review inputs could not be prepared"),
        IO("a file or network operation failed"),
        DATABASE("a database operation failed"),
        UNKNOWN("the cause is not classified");

        private final String phrase;

        Kind(String phrase) {
            this.phrase = phrase;
        }

        String tag() {
            return name().toLowerCase(Locale.ROOT);
        }

        static Kind of(Throwable failure) {
            Kind fallback = failure instanceof JobPreparationException ? INPUT_PREPARATION : UNKNOWN;
            Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
            Throwable current = failure;
            while (visited.add(current)) {
                if (current instanceof ReviewSourceNotReadyException) return SOURCE_NOT_READY;
                if (current instanceof IOException || current instanceof UncheckedIOException) return IO;
                if (current instanceof DataAccessException || current instanceof TransactionException) return DATABASE;
                // These preparation owners retain the failure they wrap. A buried cause in any other
                // exception says nothing reliable about preparation, and this diagnosis grants no retry.
                if (!(current instanceof JobPreparationException || current instanceof EvidenceCollectionException)) {
                    return fallback;
                }
                Throwable cause = current.getCause();
                if (cause == null) return fallback;
                current = cause;
            }
            return fallback;
        }
    }

    static PreparationFailure of(Stage stage, Throwable failure) {
        return new PreparationFailure(stage, Kind.of(failure));
    }

    /** What the job row records as its error message. */
    String message() {
        return "The job failed before execution " + stage.phrase + ": " + kind.phrase + ".";
    }
}
