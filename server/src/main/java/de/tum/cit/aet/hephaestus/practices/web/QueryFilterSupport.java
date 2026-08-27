package de.tum.cit.aet.hephaestus.practices.web;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/**
 * What every {@code *FilterParams} record in this module does with its raw query parameters.
 *
 * <p>The read surfaces differ in what they filter on but agree on how they read an artifact kind and a
 * page, and each answer they give is a decision worth making once: a malformed kind is the caller's
 * mistake and gets a 400, an oversized page is not and gets clamped.
 */
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

    /** The requested artifact kinds, parsed; {@code null} means "every kind". */
    public static @Nullable List<ArtifactKind> artifactKinds(@Nullable List<String> raw) {
        return raw == null ? null : raw.stream().map(QueryFilterSupport::parse).toList();
    }

    /**
     * The page to read, normalised: a missing or negative page is the first one, and the size falls back
     * to {@code defaultSize} and is clamped to {@code 1..maxSize}.
     *
     * <p>Clamped, not rejected: these are reading surfaces, and answering 400 to "?size=999" tells a
     * reader nothing they can act on while hiding data they are allowed to see. The values arrive as
     * nullable wrappers because a {@code defaultValue} on {@link RequestParam} does not reach a record
     * bound as a parameter object — the binder constructs the record and hands a primitive component
     * {@code null}, which fails conversion and answers 400 to a request that named no filter at all.
     *
     * <p>The returned {@link Pageable} carries no sort: the queries behind these surfaces bring their own
     * {@code ORDER BY}, and a caller that needs a different one adds it to this page.
     */
    public static Pageable pageable(@Nullable Integer page, @Nullable Integer size, int defaultSize, int maxSize) {
        return PageRequest.of(
            page == null || page < 0 ? 0 : page,
            size == null ? defaultSize : Math.clamp(size, 1, maxSize)
        );
    }

    private static ArtifactKind parse(String raw) {
        try {
            return ArtifactKind.of(raw);
        } catch (IllegalArgumentException malformed) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, malformed.getMessage(), malformed);
        }
    }
}
