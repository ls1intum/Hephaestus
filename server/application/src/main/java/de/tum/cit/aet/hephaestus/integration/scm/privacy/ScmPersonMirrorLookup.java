package de.tum.cit.aet.hephaestus.integration.scm.privacy;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonIdentity;
import de.tum.cit.aet.hephaestus.integration.core.spi.ProviderPersonLookup;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequiredArgsConstructor
@WorkspaceAgnostic("An exact provider/native identity selects only that provider's person mirror")
public class ScmPersonMirrorLookup implements ProviderPersonLookup {
    private final JdbcTemplate jdbc;

    @Override
    public Set<String> providerTypes() {
        return Set.of("GITHUB", "GITLAB");
    }

    @Override
    public List<Long> mirrorIds(PersonIdentity identity) {
        long nativeId;
        try {
            nativeId = Long.parseLong(identity.subject());
            if (nativeId <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "An SCM identity requires a positive native user id");
        }
        return jdbc.query(
                "SELECT id FROM \"user\" WHERE provider_id=? AND native_id=?",
                (rs, rowNum) -> rs.getLong("id"),
                identity.providerId(),
                nativeId);
    }
}
