package de.tum.cit.aet.hephaestus.integration.scm.domain.capture;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * A repository path of the context folder: {@code repos/<host>/<full path>}, with an optional {@code @<ref>}, such as
 * {@code repos/github.com/acme/api@pull/42} or {@code repos/gitlab.com/group/sub/proj@main}. The host keeps
 * repositories of two providers apart, and a GitLab path can have any number of groups.
 *
 * @param ref null for the repository's default branch
 */
record RepositoryLoadPath(
        String host, String repository, @Nullable String ref) {

    static final String PREFIX = "repos/";

    private static final int MAX_REF_LENGTH = 300;

    /** The folder path the repository is staged at, without a ref. */
    String folderPath() {
        return PREFIX + host + "/" + repository;
    }

    /** Empty when the path is not one repository with an optional ref. */
    static Optional<RepositoryLoadPath> parse(String path) {
        if (!path.startsWith(PREFIX)) {
            return Optional.empty();
        }
        String rest = path.substring(PREFIX.length());
        String ref = null;
        int at = rest.indexOf('@');
        if (at >= 0) {
            ref = rest.substring(at + 1);
            rest = rest.substring(0, at);
            if (ref.isBlank() || ref.length() > MAX_REF_LENGTH || ref.chars().anyMatch(Character::isWhitespace)) {
                return Optional.empty();
            }
        }
        if (rest.endsWith("/")) {
            rest = rest.substring(0, rest.length() - 1);
        }
        String[] segments = rest.split("/", -1);
        if (segments.length < 3 || Arrays.stream(segments).anyMatch(RepositoryLoadPath::invalidSegment)) {
            return Optional.empty();
        }
        String repository = String.join("/", Arrays.copyOfRange(segments, 1, segments.length));
        return Optional.of(new RepositoryLoadPath(segments[0].toLowerCase(Locale.ROOT), repository, ref));
    }

    private static boolean invalidSegment(String segment) {
        return segment.isEmpty()
                || segment.equals(".")
                || segment.equals("..")
                || segment.chars().anyMatch(c -> !(Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '.'));
    }
}
