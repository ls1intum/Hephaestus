package de.tum.cit.aet.hephaestus.observability;

import java.io.IOException;
import org.springframework.boot.logging.StackTracePrinter;
import org.springframework.boot.logging.StandardStackTracePrinter;

/**
 * Prints stack traces with each throwable named by its class only, as {@code logging.structured.json.stacktrace.printer}.
 *
 * <p>Exception messages are not ours to vet: a driver quotes rejected row values, an HTTP client the request URL and
 * part of the response body, a parser the input it rejected. Boot hands in the printer it configured from the other
 * {@code stacktrace} properties, so frames, hashes, cause and suppressed sections and the length bound stay as
 * configured; only the line naming each throwable loses its message.
 */
public class ExceptionClassStackTracePrinter implements StackTracePrinter {

    private final StackTracePrinter delegate;

    public ExceptionClassStackTracePrinter(StandardStackTracePrinter configured) {
        this.delegate =
                configured.withFormatter(throwable -> throwable.getClass().getName());
    }

    @Override
    public void printStackTrace(Throwable throwable, Appendable out) throws IOException {
        delegate.printStackTrace(throwable, out);
    }
}
