package de.tum.cit.aet.hephaestus.practices.web;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Shared parsing for practice query parameters. */
public final class QueryFilterSupport {

    private QueryFilterSupport() {}

    /**
     * One artifact kind, parsed; {@code null} means "every kind".
     *
     * <p>The surfaces take the kind as a bare string rather than as an {@link ArtifactKind} because
     * springdoc walks into the value type and publishes the parameter as {@code artifactKind.value}, so a
     * generated client would send a query key no caller writes. The grammar is enforced here instead.
     */
    public static @Nullable ArtifactKind artifactKind(@Nullable String raw) {
        return raw == null ? null : parse(raw);
    }

    public static @Nullable List<ArtifactKind> artifactKinds(@Nullable List<String> raw) {
        return raw == null ? null : raw.stream().map(QueryFilterSupport::parse).toList();
    }

    /** Builds an unsorted page after the parameter record has validated supplied bounds. */
    public static Pageable pageable(@Nullable Integer page, @Nullable Integer size, int defaultSize) {
        return PageRequest.of(page == null ? 0 : page, size == null ? defaultSize : size);
    }

    private static ArtifactKind parse(String raw) {
        try {
            return ArtifactKind.of(raw);
        } catch (IllegalArgumentException malformed) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, malformed.getMessage(), malformed);
        }
    }
}
