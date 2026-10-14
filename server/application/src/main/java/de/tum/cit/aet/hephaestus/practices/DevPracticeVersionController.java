package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.core.AuditExempt;
import de.tum.cit.aet.hephaestus.core.RecentSignInExempt;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@ConditionalOnServerRole
@Hidden
@RestController
@ConditionalOnBooleanProperty("hephaestus.dev.seed-enabled")
@RecentSignInExempt(reason = "Development seed endpoint is disabled by default")
@PreAuthorize("hasAuthority('app_admin')")
@WorkspaceAgnostic("The development seed supplies its workspace id")
@RequiredArgsConstructor
public class DevPracticeVersionController {
    private final PracticeVersionService versions;

    public record PinnedVersionDTO(String slug, String versionId, int number) {}

    @PostMapping("/api/dev/practice-versions")
    @AuditExempt(reason = "Reads the current immutable versions for development seeds")
    public List<PinnedVersionDTO> forReview(@RequestParam Long workspaceId, @RequestParam("slug") List<String> slugs) {
        return versions.forReview(workspaceId, slugs).stream()
                .map(pinned -> new PinnedVersionDTO(
                        pinned.getPractice().getSlug(),
                        Objects.requireNonNull(pinned.getId()).toString(),
                        pinned.getNumber()))
                .sorted(Comparator.comparing(PinnedVersionDTO::slug))
                .toList();
    }
}
