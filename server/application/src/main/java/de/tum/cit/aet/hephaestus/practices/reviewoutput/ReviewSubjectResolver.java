package de.tum.cit.aet.hephaestus.practices.reviewoutput;

import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewSubjectDTO;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.modulith.NamedInterface;
import org.springframework.stereotype.Component;

/** The developers that admin review lists and sheets name, by user id; the one place that reads them. */
@Component
@NamedInterface("review-output")
@RequiredArgsConstructor
public class ReviewSubjectResolver {

    private final UserRepository userRepository;

    public Map<Long, ReviewSubjectDTO> resolve(Collection<? extends @Nullable Long> userIds) {
        Set<Long> ids = userIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .map(ReviewSubjectDTO::from)
                .collect(Collectors.toMap(ReviewSubjectDTO::id, Function.identity()));
    }
}
